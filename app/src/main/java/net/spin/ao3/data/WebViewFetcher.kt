package net.spin.ao3.data

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.LinkedList

/**
 * Fallback network layer that fetches AO3 pages through the system WebView —
 * the same Chromium that backs Chrome — instead of OkHttp.
 *
 * Why: Cloudflare tarpits OkHttp's TLS fingerprint (measured: AO3 requests
 * stall ~60s or die with HTTP 525, while curl/OpenSSL and real Chrome pass).
 * The system WebView IS Chromium, so AO3 treats its traffic like a browser's.
 * It also executes JavaScript, so Cloudflare JS challenges resolve themselves
 * (a visible captcha is shown to the user if one appears).
 *
 * Strategy copied from CO3 (https://github.com/tbvns/CO3), a working AO3
 * client: try the plain HTTP client first; on Cloudflare codes (403/525/418/
 * 520/522/503), a timeout or a `_cf_chl_opt` challenge page, fall back to
 * this WebView fetch.
 *
 * Hardening over the previous version (which wedged the app: endless spinner):
 *  - A FRESH WebView is created per fetch and destroyed afterwards, so no
 *    stale state can leak between requests (a shared WebView kept running the
 *    previous page's JS, answered stale chunks to the next request and could
 *    leave its overlay visible forever).
 *  - The timeout is awaited on a background dispatcher: `withTimeoutOrNull`
 *    on the Main dispatcher cannot fire while the main thread is busy, which
 *    used to leave the request (and the global gate around it) locked forever.
 *  - A Cloudflare challenge only gets ~25s to auto-solve; if it doesn't, the
 *    fetch fails cleanly instead of blocking the app indefinitely.
 *  - The overlay is always detached after a fetch, visible or not.
 *
 * The WebView lives in a full-screen overlay on the current activity's decor
 * view (INVISIBLE normally so it never blocks touches; VISIBLE + white only
 * while a Cloudflare captcha needs the user to solve it).
 */
object WebViewFetcher {

    private const val TAG = "WebViewFetcher"

    /** Cloudflare error codes that mean "you're being bot-detected". */
    val CF_CODES = setOf(403, 525, 418, 520, 522, 503)

    /** Time a Cloudflare challenge gets to auto-solve before the fetch fails. */
    private const val CHALLENGE_GRACE_MS = 25_000L

    /** Set once a Cloudflare challenge is seen; Ao3Client then goes straight
     *  to the WebView (skipping the doomed OkHttp attempt) for the rest of
     *  the session — like CO3's 24h CF mode, but in memory. */
    @Volatile
    var cloudflareBlocked: Boolean = false
        private set

    /** Cookie header del dominio tal como lo ve el WebView del sistema, capturado
     *  tras cada carga exitosa. CRÍTICO para pasar Cloudflare: cuando el WebView
     *  resuelve un challenge gana un `cf_clearance` que vive en LA COOKIE STORE
     *  DEL SISTEMA, no en el jar de OkHttp. CO3 no tiene este problema porque el
     *  fetch de React Native comparte esa store; nuestro OkHttp no, así que sin
     *  este puente cada petición OkHttp volvía a comerse el muro de bots aunque
     *  el WebView acabara de resolverlo. Ao3Client lo inyecta en cada GET. */
    @Volatile
    var webviewCookieHeader: String? = null
        private set

    /** Puente inverso: cookies ganadas por OkHttp (p. ej. `view_adult` tras
     *  aceptar el age-gate) se inyectan en la store del sistema para que los
     *  WebView frescos de las siguientes cargas las lleven. Sin esto, cada
     *  fetch por WebView re-presentaba el age-gate de obras adultas.
     *  A prueba de JVM: el objeto no se inicializa en tests unitarios. */
    fun pushCookiesToWebView(url: String, cookieHeader: String) {
        mainHandler.post {
            runCatching {
                android.webkit.CookieManager.getInstance().setCookie(url, cookieHeader)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // -------------------- Activity tracking --------------------

    @Volatile
    private var currentActivity: Activity? = null

    /** Call once from app start (e.g. AppContainer) so fetches can find a host. */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) { currentActivity = activity }
                override fun onActivityPaused(activity: Activity) {
                    if (currentActivity === activity) currentActivity = null
                }
                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            },
        )
    }

    // -------------------- Queue (all WebView access on main thread) --------------------

    private class Request(val url: String, val deferred: CompletableDeferred<String>) {
        /** The WebView this request runs in; console traffic from any other
         *  WebView is stale and must be ignored (an aborted transfer's
         *  WebView can outlive its request just long enough to corrupt the
         *  next one). */
        var web: WebView? = null
    }

    private val queue = LinkedList<Request>()
    private var inFlight: Request? = null

    // -------------------- Per-fetch WebView --------------------

    /** Accumulates the chunked HTML of the in-flight request. The console
     *  channel can drop very large single strings, so the page is transferred
     *  in 50 KB chunks and stitched back here. */
    private var chunkBuffer: StringBuilder? = null
    private var overlay: FrameLayout? = null
    private var webView: WebView? = null

    /** Último código HTTP >= 400 del frame principal (403/503 quedan como
     *  interinos mientras el challenge resuelve). Se consulta al cerrar la
     *  carga: un «done» con error vigente se descarta como página de muro. */
    @Volatile
    private var httpErrorStatus: Int? = null

    /**
     * Fetches [url] through the system WebView and returns the final rendered
     * HTML (after any Cloudflare challenge resolves). Returns null on failure
     * or timeout rather than throwing, so callers can keep their OkHttp retry
     * loop as the primary path.
     */
    suspend fun fetch(url: String, timeoutMs: Long = 90_000L): String? {
        Log.d(TAG, "fetch() solicitado: $url")
        val request = Request(url, CompletableDeferred())
        mainHandler.post { enqueue(request) }
        // Await on a BACKGROUND dispatcher: the timeout must fire even if the
        // main thread is busy (a busy main thread used to block the timeout
        // AND the gate lock that wraps this call -> endless spinner).
        val result = withContext(Dispatchers.Default) {
            withTimeoutOrNull(timeoutMs) { runCatching { request.deferred.await() }.getOrNull() }
        }
        if (result == null) {
            Log.w(TAG, "timeout ($timeoutMs ms) esperando a WebView; cancelando carga")
            mainHandler.post {
                // A newer queued request may already own the slot; only cancel
                // if THIS request is still the one in flight.
                if (inFlight === request) cancelCurrent()
            }
        }
        return result
    }

    // -------------------- Main-thread machinery --------------------

    private fun enqueue(request: Request) {
        queue.add(request)
        processNext()
    }

    /** Cancels the in-flight load (caller timed out): stops and destroys the
     *  WebView and lets the next queued request proceed. */
    private fun cancelCurrent() {
        val request = inFlight ?: return
        Log.w(TAG, "cancelCurrent(): abortando ${request.url}")
        teardown()
        settle(null, IOException("WebView agotó su tiempo (cancelado por el caller)"))
    }

    private fun processNext() {
        if (inFlight != null || queue.isEmpty()) return
        val request = queue.removeFirst()
        inFlight = request
        val wv = try {
            ensureWebView()
        } catch (e: Exception) {
            Log.e(TAG, "ensureWebView falló", e)
            settle(null, e)
            return
        }
        request.web = wv
        chunkBuffer = null
        Log.d(TAG, "cargando en WebView: ${request.url}")
        wv.loadUrl(request.url)
    }

    private fun settle(value: String?, error: Throwable?) {
        val request = inFlight ?: return
        inFlight = null
        chunkBuffer = null
        Log.d(
            TAG,
            "settle(): ${if (value != null) "OK (${value.length} chars)" else "FALLO: ${error?.message}"} para ${request.url}",
        )
        if (value != null) {
            // Capturar las cookies del sistema ANTES de destruir el WebView: el
            // cf_clearance que el challenge acaba de ganar debe sobrevivir a la
            // destrucción (la store del CookieManager es global a la app, así
            // que también sirve para los WebView frescos de las próximas cargas).
            webviewCookieHeader = try {
                android.webkit.CookieManager.getInstance().getCookie(request.url)
            } catch (t: Throwable) {
                Log.w(TAG, "no se pudieron capturar cookies del WebView: ${t.message}")
                null
            }
        }
        if (error != null || value == null) {
            request.deferred.completeExceptionally(error ?: IOException("WebView devolvió vacío"))
        } else {
            request.deferred.complete(value)
        }
        teardown()
        // Give the UI a beat to breathe before the next request.
        mainHandler.postDelayed({ processNext() }, 150)
    }

    /** Removes the WebView + overlay from the window and DESTROYS the WebView:
     *  a merely detached WebView keeps loading pages and running JS, and its
     *  console messages would keep arriving (stale chunks / a stale "done" can
     *  otherwise complete the NEXT request with the previous page's HTML). */
    private fun teardown() {
        val ov = overlay
        val wv = webView
        overlay = null
        webView = null
        if (ov != null) {
            (ov.parent as? ViewGroup)?.removeView(ov)
            ov.removeAllViews()
        }
        wv?.stopLoading()
        wv?.destroy()
    }

    /** Handles one AO3FETCH: console message coming from the page JS.
     *  [source] must be the request's own WebView; anything else is stale
     *  traffic from an aborted transfer and gets dropped. */
    private fun handleConsole(source: WebView?, payload: String) {
        val req = inFlight ?: return
        if (source === null || source !== req.web) {
            Log.w(TAG, "mensaje AO3FETCH de una WebView huérfana — ignorado")
            return
        }
        when {
            payload == "running" -> {
                // Channel works; the chunk stream will follow. Nothing to do.
            }
            payload == "challenge" -> {
                cloudflareBlocked = true
                Log.w(TAG, "Cloudflare challenge detectado — activando CF mode, mostrando overlay")
                // Let the user solve the captcha: show the WebView full-screen.
                showOverlay()
                // If the challenge does NOT auto-solve (it usually redirects to
                // the real page after a few seconds), fail the fetch rather
                // than blocking the app forever.
                mainHandler.postDelayed(
                    {
                        if (inFlight === req) {
                            Log.w(TAG, "challenge no se resolvió en ${CHALLENGE_GRACE_MS}ms — fallando fetch")
                            settle(null, IOException("Cloudflare no resolvió el challenge"))
                        }
                    },
                    CHALLENGE_GRACE_MS,
                )
            }
            payload == "done" -> {
                val html = chunkBuffer?.toString()
                chunkBuffer = null
                Log.d(TAG, "HTML completo: ${html?.length ?: 0} chars")
                val err = httpErrorStatus
                httpErrorStatus = null
                if (err != null) {
                    // El frame principal acabó con error y no hubo redirect que
                    // lo resetease: es una página de muro/error, no la pedida.
                    settle(null, IOException("WebView recibió HTTP $err"))
                } else {
                    settle(html ?: "", null)
                }
            }
            payload.startsWith("chunk:") -> {
                val body = payload.removePrefix("chunk:")
                val sep = body.indexOf(':')
                if (sep > 0) {
                    val offset = body.substring(0, sep).toIntOrNull()
                    val part = body.substring(sep + 1)
                    val buf = chunkBuffer ?: StringBuilder(part.length * 4).also { chunkBuffer = it }
                    if (offset == null || offset == buf.length) {
                        buf.append(part)
                        if (buf.length % 200_000 < part.length) {
                            Log.d(TAG, "recibidos ${buf.length} chars hasta ahora")
                        }
                    } else {
                        // Gap: the console channel dropped at least one chunk,
                        // so every later offset mismatches too. Ignoring them
                        // used to let "done" settle a silently truncated page
                        // as if it were complete; fail instead so the caller's
                        // retry loop refetches the whole URL.
                        Log.w(
                            TAG,
                            "chunk perdido: esperaba offset ${buf.length}, llegó $offset — cancelando transferencia",
                        )
                        chunkBuffer = null
                        settle(null, IOException("Se perdió un fragmento del HTML en el canal de la WebView"))
                    }
                }
            }
            payload.startsWith("error:") -> {
                chunkBuffer = null
                settle(null, IOException(payload.removePrefix("error:")))
            }
            else -> Log.w(TAG, "mensaje AO3FETCH inesperado: ${payload.take(80)}")
        }
    }

    private fun showOverlay() {
        val ov = overlay ?: return
        ov.setBackgroundColor(Color.WHITE)
        ov.visibility = android.view.View.VISIBLE
        ov.isClickable = true
        ov.isFocusable = true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        val activity = currentActivity ?: throw IOException("No hay Activity activa para WebView")

        val ov = FrameLayout(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            visibility = android.view.View.INVISIBLE
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }

        val wv = WebView(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            // Leave the default User-Agent: it's the real Chrome/WebView UA,
            // which is exactly what Cloudflare expects from a browser.
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    val text = msg.message()
                    if (text.startsWith("AO3FETCH:")) {
                        val payload = text.removePrefix("AO3FETCH:")
                        // `this@apply` is the WebView that emitted the message:
                        // handleConsole drops traffic from any other source.
                        mainHandler.post { handleConsole(this@apply, payload) }
                        return true
                    }
                    return super.onConsoleMessage(msg)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // Cada navegación (incluidos los redirects del challenge)
                    // resetea el error HTTP: lo que manda es la respuesta FINAL.
                    if (view === inFlight?.web) httpErrorStatus = null
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    // Never re-arm an orphaned WebView: after teardown it must
                    // stay inert, or a late redirect would start streaming the
                    // old page into whatever request is live by then.
                    if (view !== inFlight?.web) return
                    Log.d(TAG, "onPageFinished: $url — inyectando detector")
                    view.evaluateJavascript(DETECT_JS, null)
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    // Solo el frame principal: los subrecursos fallan sin que
                    // la página esté bloqueada. Sin esto, el HTML del muro de
                    // bots («bot servers are currently overloaded», HTTP 429)
                    // se devolvía como si fuera la página pedida y el parser
                    // moría con «No se pudo interpretar».
                    if (!request.isForMainFrame || view !== inFlight?.web) return
                    val status = errorResponse.statusCode
                    if (status == 403 || status == 503) {
                        // Interino: puede ser la página del challenge, que
                        // resuelve sola y redirige. No fallar todavía.
                        httpErrorStatus = status
                        return
                    }
                    if (status >= 400) {
                        Log.w(TAG, "onReceivedHttpError: HTTP $status (frame principal)")
                        settle(null, IOException("WebView recibió HTTP $status"))
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)
                    // Only the main frame matters (subresources fail all the time).
                    if (request.isForMainFrame && view === inFlight?.web) {
                        Log.w(TAG, "onReceivedError: ${error.errorCode} ${error.description}")
                        settle(null, IOException("WebView error ${error.errorCode}: ${error.description}"))
                    }
                }
            }
        }

        ov.addView(
            wv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        // Full-screen overlay (INVISIBLE: laid out and JS-capable, but not
        // drawn and not touchable). A 1x1 WebView can refuse to run JS on
        // some devices (MIUI), which silently breaks the whole bridge.
        (activity.window.decorView as ViewGroup).addView(
            ov,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        overlay = ov
        webView = wv
        return wv
    }

    /** Detects a Cloudflare challenge vs. real content and reports the page
     *  back through console.log in 50 KB chunks.
     *
     *  CO3 uses window.ReactNativeWebView.postMessage; the Android-native
     *  equivalent that needs NO @JavascriptInterface annotations (R8 deletes
     *  those in release) is WebChromeClient.onConsoleMessage — console.log
     *  from the page always reaches it. */
    private val DETECT_JS = """
        (function() {
          try {
            console.log('AO3FETCH:running');
            // Only ACTIVE challenge markers count. Cloudflare injects its
            // passive detector (jsd/main.js) into every legit page, so
            // matching 'cdn-cgi/challenge-platform' would mark real pages
            // as challenges and throw away good HTML.
            var isChallenge =
              typeof window._cf_chl_opt !== 'undefined' ||
              !!document.querySelector('script[src*="challenges.cloudflare.com"]') ||
              !!document.querySelector('iframe[src*="challenges.cloudflare.com"]');
            if (isChallenge) {
              console.log('AO3FETCH:challenge');
              return;
            }
            var html = document.documentElement.outerHTML;
            var CHUNK = 50000;
            for (var i = 0; i < html.length; i += CHUNK) {
              console.log('AO3FETCH:chunk:' + i + ':' + html.substr(i, CHUNK));
            }
            console.log('AO3FETCH:done');
          } catch (e) {
            console.log('AO3FETCH:error:' + e.message);
          }
        })();
        true;
    """.trimIndent()
}

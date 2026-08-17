package net.spin.ao3.data

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
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

    private data class Request(val url: String, val deferred: CompletableDeferred<String>)

    private val queue = LinkedList<Request>()
    private var inFlight: Request? = null

    // -------------------- Per-fetch WebView --------------------

    /** Accumulates the chunked HTML of the in-flight request. The console
     *  channel can drop very large single strings, so the page is transferred
     *  in 50 KB chunks and stitched back here. */
    private var chunkBuffer: StringBuilder? = null
    private var overlay: FrameLayout? = null
    private var webView: WebView? = null

    /**
     * Fetches [url] through the system WebView and returns the final rendered
     * HTML (after any Cloudflare challenge resolves). Returns null on failure
     * or timeout rather than throwing, so callers can keep their OkHttp retry
     * loop as the primary path.
     */
    suspend fun fetch(url: String, timeoutMs: Long = 90_000L): String? {
        Log.d(TAG, "fetch() solicitado: $url")
        val deferred = CompletableDeferred<String>()
        mainHandler.post { enqueue(Request(url, deferred)) }
        // Await on a BACKGROUND dispatcher: the timeout must fire even if the
        // main thread is busy (a busy main thread used to block the timeout
        // AND the gate lock that wraps this call -> endless spinner).
        val result = withContext(Dispatchers.Default) {
            withTimeoutOrNull(timeoutMs) { runCatching { deferred.await() }.getOrNull() }
        }
        if (result == null) {
            Log.w(TAG, "timeout ($timeoutMs ms) esperando a WebView; cancelando carga")
            mainHandler.post { cancelCurrent() }
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
        if (error != null || value == null) {
            request.deferred.completeExceptionally(error ?: IOException("WebView devolvió vacío"))
        } else {
            request.deferred.complete(value)
        }
        teardown()
        // Give the UI a beat to breathe before the next request.
        mainHandler.postDelayed({ processNext() }, 150)
    }

    /** Removes the WebView + overlay from the window and frees them. */
    private fun teardown() {
        val ov = overlay
        overlay = null
        webView = null
        if (ov != null) {
            (ov.parent as? ViewGroup)?.removeView(ov)
            ov.removeAllViews()
        }
    }

    /** Handles one AO3FETCH: console message coming from the page JS. */
    private fun handleConsole(payload: String) {
        // Anything arriving without an in-flight request is stale (the WebView
        // was torn down) and must be ignored.
        val req = inFlight ?: return
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
                settle(html ?: "", null)
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
                    }
                    // Out-of-order chunk (offset mismatch): ignored; the
                    // in-order stream still produces the full page.
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
                        mainHandler.post { handleConsole(payload) }
                        return true
                    }
                    return super.onConsoleMessage(msg)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "onPageFinished: $url — inyectando detector")
                    view.evaluateJavascript(DETECT_JS, null)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)
                    // Only the main frame matters (subresources fail all the time).
                    if (request.isForMainFrame) {
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

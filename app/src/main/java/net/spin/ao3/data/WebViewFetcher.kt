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
 * The WebView lives in a 1x1 transparent overlay on the current activity's
 * decor view (so it never blocks touches) and is swapped to full-screen
 * visible only while a Cloudflare captcha needs the user to solve it.
 */
object WebViewFetcher {

    private const val TAG = "WebViewFetcher"

    /** Cloudflare error codes that mean "you're being bot-detected". */
    val CF_CODES = setOf(403, 525, 418, 520, 522, 503)

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
    private var overlay: FrameLayout? = null
    private var webView: WebView? = null

    /** Accumulates the chunked HTML of the in-flight request. The console
     *  channel can drop very large single strings, so the page is transferred
     *  in 100 KB chunks and stitched back here. */
    private var chunkBuffer: StringBuilder? = null

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
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        if (result == null) {
            Log.w(TAG, "timeout ($timeoutMs ms) esperando a WebView; cancelando carga")
            // The caller gave up: stop the WebView so it doesn't keep loading in
            // the background (seen: a stuck ESTABLISHED connection + constant GC).
            mainHandler.post { cancelCurrent() }
        }
        return result
    }

    // -------------------- Main-thread machinery --------------------

    private fun enqueue(request: Request) {
        queue.add(request)
        processNext()
    }

    /** Cancels the in-flight load (caller timed out): stops the WebView and
     *  lets the next queued request proceed. */
    private fun cancelCurrent() {
        val request = inFlight ?: return
        webView?.stopLoading()
        settle(null, IOException("WebView agotó su tiempo (cancelado por el caller)"))
    }

    private fun processNext() {
        if (inFlight != null || queue.isEmpty()) return
        inFlight = queue.removeFirst()
        val wv = try {
            ensureWebView()
        } catch (e: Exception) {
            Log.e(TAG, "ensureWebView falló", e)
            settle(null, e)
            return
        }
        setOverlayVisible(false)
        chunkBuffer = null
        Log.d(TAG, "cargando en WebView: ${inFlight!!.url}")
        wv.loadUrl(inFlight!!.url)
    }

    private fun settle(value: String?, error: Throwable?) {
        val request = inFlight ?: return
        inFlight = null
        setOverlayVisible(false)
        if (error != null || value == null) request.deferred.completeExceptionally(error ?: IOException("WebView devolvió vacío"))
        else request.deferred.complete(value)
        // Give the WebView a beat to reset before the next request.
        mainHandler.postDelayed({ processNext() }, 150)
    }

    /** Handles one AO3FETCH: console message coming from the page JS. */
    private fun handleConsole(payload: String) {
        when {
            payload == "running" -> {
                // Channel works; the chunk stream will follow. Nothing to do.
            }
            payload == "challenge" -> {
                cloudflareBlocked = true
                Log.w(TAG, "Cloudflare challenge detectado — activando CF mode")
                // Let the user solve the captcha: show the WebView full-screen.
                setOverlayVisible(true)
                // The challenge reloads the page when solved; onPageFinished
                // fires again and the JS detector will report success then.
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

    private fun setOverlayVisible(visible: Boolean) {
        val ov = overlay ?: return
        if (visible) {
            ov.setBackgroundColor(Color.WHITE)
            ov.visibility = android.view.View.VISIBLE
        } else {
            ov.setBackgroundColor(Color.TRANSPARENT)
            ov.visibility = android.view.View.INVISIBLE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        overlay?.let { ov ->
            // Already created: make sure it's still attached to the current
            // activity's decor view (navigation may have recreated it).
            val activity = currentActivity ?: throw IOException("No hay Activity activa para WebView")
            if (ov.parent == null) {
                (activity.window.decorView as ViewGroup).addView(
                    ov,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            return webView!!
        }

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
                        mainHandler.post { handleConsole(text.removePrefix("AO3FETCH:")) }
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
                    // Some pages take a moment to settle; if the first injection
                    // raced with the load, retry once shortly after.
                    mainHandler.postDelayed(
                        { view.evaluateJavascript(DETECT_JS, null) },
                        1_500L,
                    )
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)
                    // Only the main frame matters (subresources fail all the time).
                    if (request.isForMainFrame) {
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
     *  back through console.log in 100 KB chunks.
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

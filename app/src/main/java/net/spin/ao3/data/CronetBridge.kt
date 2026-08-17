package net.spin.ao3.data

import android.content.Context
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * OkHttp NETWORK interceptor that performs the request through Cronet — the
 * Chromium network stack that backs Chrome — instead of OkHttp's own TLS/HTTP-2
 * implementation.
 *
 * Why: Cloudflare tarpits OkHttp's TLS fingerprint (measured: ~1 in 5 AO3
 * requests stalls ~60s or dies with HTTP 525, regardless of User-Agent or
 * HTTP version, while curl/OpenSSL and real Chrome pass). Cronet presents the
 * same fingerprint as Chrome, so AO3 treats the app's traffic like a browser.
 *
 * The engine is embedded (cronet-embedded: API + full Chromium stack in one
 * AAR, no Google Play Services needed). If engine creation still fails the
 * interceptor falls through to the normal OkHttp chain. Registered via
 * addNetworkInterceptor so OkHttp's cookie handling (CookieJar) still applies
 * around it.
 */
class CronetBridge(context: Context) : Interceptor {

    private val engine: CronetEngine? = try {
        CronetEngine.Builder(context).enableBrotli(true).build()
    } catch (_: Throwable) {
        null
    }

    // Cronet 141 no longer exposes an executor on the engine; use our own so
    // callbacks never run on the OkHttp dispatcher thread (which is blocked on
    // the latch below).
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun intercept(chain: Interceptor.Chain): Response {
        val e = engine ?: return chain.proceed(chain.request())
        val req = chain.request()
        val url = req.url.toString()

        val latch = CountDownLatch(1)
        val bodyBytes = ByteArrayOutputStream()
        var outcome: Outcome = Outcome.Failure(IOException("Cronet request never started"))
        var requestRef: UrlRequest? = null
        // Set-Cookie from intermediate redirect responses (e.g. login).
        // Cronet's internal followRedirect() drops them, so we capture and
        // forward them so OkHttp's CookieJar still sees them.
        val redirectCookies = mutableListOf<String>()

        val callback = object : UrlRequest.Callback() {
            private var redirects = 0

            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                if (++redirects > 10) {
                    outcome = Outcome.Failure(IOException("Demasiados redireccionamientos"))
                    request.cancel()
                    return
                }
                info.allHeaders["set-cookie"]?.let { redirectCookies.addAll(it) }
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                request.read(ByteBuffer.allocateDirect(64 * 1024))
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.flip()
                val chunk = ByteArray(byteBuffer.remaining())
                byteBuffer.get(chunk)
                bodyBytes.write(chunk)
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                outcome = Outcome.Success(info, bodyBytes.toByteArray())
                latch.countDown()
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                outcome = Outcome.Failure(IOException("Cronet: ${error.message}", error))
                latch.countDown()
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                outcome = Outcome.Failure(IOException("Cronet request cancelado"))
                latch.countDown()
            }
        }

        val builder = e.newUrlRequestBuilder(url, callback, executor)
            .setHttpMethod(req.method)
        req.headers.forEach { (k, v) ->
            // Headers Cronet manages itself — adding them throws
            // IllegalArgumentException, and Accept-Encoding would risk
            // double-decompression (Cronet negotiates + decodes its own).
            if (k.equals("Accept-Encoding", true) ||
                k.equals("Host", true) ||
                k.equals("Connection", true) ||
                k.equals("Content-Length", true) ||
                k.equals("Transfer-Encoding", true)
            ) return@forEach
            builder.addHeader(k, v)
        }
        val body = req.body
        if (body != null && req.method != "GET" && req.method != "HEAD") {
            val buffer = Buffer()
            body.writeTo(buffer)
            val bytes = buffer.readByteArray()
            builder.setUploadDataProvider(
                object : UploadDataProvider() {
                    override fun getLength(): Long = bytes.size.toLong()
                    override fun read(uploadSink: UploadDataSink, byteBuffer: ByteBuffer) {
                        val n = minOf(byteBuffer.remaining(), bytes.size)
                        byteBuffer.put(bytes, 0, n)
                        uploadSink.onReadSucceeded(n == bytes.size)
                    }
                    override fun rewind(uploadSink: UploadDataSink) {
                        uploadSink.onRewindSucceeded()
                    }
                },
                executor,
            )
        }
        requestRef = builder.build()
        requestRef?.start()

        if (!latch.await(75, TimeUnit.SECONDS)) {
            requestRef?.cancel()
            throw IOException("Cronet: timeout al cargar $url")
        }

        return when (val o = outcome) {
            is Outcome.Success -> {
                val info = o.info
                val headersBuilder = Headers.Builder()
                info.allHeaders.forEach { (name, values) -> values.forEach { headersBuilder.add(name, it) } }
                val present = headersBuilder.build()["set-cookie"]
                if (present == null && redirectCookies.isNotEmpty()) {
                    redirectCookies.forEach { headersBuilder.add("Set-Cookie", it) }
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(info.httpStatusCode)
                    .message(info.httpStatusText)
                    .headers(headersBuilder.build())
                    .body(
                        o.bytes.toResponseBody(
                            info.allHeaders["content-type"]
                                ?.firstOrNull()
                                ?.let { runCatching { it.toMediaType() }.getOrNull() },
                        ),
                    )
                    .build()
            }
            is Outcome.Failure -> throw o.error
        }
    }

    private sealed class Outcome {
        data class Success(val info: UrlResponseInfo, val bytes: ByteArray) : Outcome()
        data class Failure(val error: IOException) : Outcome()
    }
}

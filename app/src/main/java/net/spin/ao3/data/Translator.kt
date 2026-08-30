package net.spin.ao3.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Chapter translation with two selectable backends:
 *
 *  - [Config.Provider.GOOGLE]: the unofficial endpoint that
 *    translate.google.com itself uses (`translate.googleapis.com/translate_a/
 *    single?client=gtx`) — no API key, real Google quality. Verified limits
 *    (measured): ~10k characters per request (25k+ → HTTP 400), so 8k chunks.
 *    It is an internal endpoint and Google throttles/blocks bot-looking
 *    clients: the request MUST look like a browser (Chrome UA + Accept
 *    headers), the same lesson [Ao3Client] learned with Cloudflare — the old
 *    "AO3-Lector/... okhttp" UA got HTTP 403/429, which showed up as
 *    "Traducción: HTTP xxx".
 *  - [Config.Provider.AI]: any OpenAI-compatible API (`POST {baseUrl}/chat/
 *    completions`, bearer key) configured in Ajustes → Traducción. Slower but
 *    usually better literary quality, and it does not depend on Google's
 *    goodwill.
 *
 * Both providers receive plain text blocks (joined with blank lines) and the
 * result is rebuilt as `<p>` HTML, so the reader/TTS/paging integration is
 * identical. Translations are cached forever in [cache]; the cache key carries
 * the provider (and model for AI), so switching provider never mixes results.
 */
class Translator(
    private val cache: DiskCache,
    private val configProvider: () -> Config = { Config() },
) {
    /** Snapshot of the translation settings, read fresh at each call. */
    data class Config(
        val provider: Provider = Provider.GOOGLE,
        val aiBaseUrl: String = "",
        val aiApiKey: String = "",
        val aiModel: String = "",
    ) {
        enum class Provider { GOOGLE, AI }

        val aiConfigured: Boolean
            get() = aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && aiModel.isNotBlank()
    }

    /** Browser-like client for the Google endpoint. */
    private val googleClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** LLMs are slow; give the AI backend a generous read timeout. */
    private val aiClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /** Comfortably under the ~10k measured ceiling, big enough to keep context. */
    private val chunkChars = 8_000
    /** Small pause between chunks: respectful, avoids bursty behavior. */
    private val paceMs = 150L

    private val endpoint = "https://translate.googleapis.com/translate_a/single"

    /**
     * Translates the block text of [html] (a chapter fragment: `<p>` blocks
     * and similar) preserving paragraph structure, caches the result under
     * [key] + provider tag and returns the rebuilt HTML fragment.
     * [onProgress] reports (doneBatches, totalBatches) for the loading overlay.
     */
    suspend fun translateChapterHtml(
        key: String,
        html: String,
        lang: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): String {
        val cfg = configProvider()
        if (cfg.provider == Config.Provider.AI && !cfg.aiConfigured) {
            throw IOException(
                "Traducción con IA sin configurar: añade Base URL, API key y modelo en Ajustes → Traducción.",
            )
        }
        val cacheKey = "$key#${cacheTag(cfg)}"
        cache.get(cacheKey)?.let { return it }
        val blocks = extractBlocks(html)
        if (blocks.isEmpty()) {
            onProgress(1, 1)
            return html
        }
        // Batch consecutive blocks into chunks of <= chunkChars.
        val batches = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        var length = 0
        for (block in blocks) {
            if (length + block.length > chunkChars && current.isNotEmpty()) {
                batches += current
                current = mutableListOf()
                length = 0
            }
            current += block
            length += block.length
        }
        if (current.isNotEmpty()) batches += current

        val translated = mutableListOf<String>()
        batches.forEachIndexed { i, batch ->
            val joined = batch.joinToString("\n\n")
            val out = when (cfg.provider) {
                Config.Provider.GOOGLE -> translateGoogleChunk(joined, lang)
                Config.Provider.AI -> translateAiChunk(joined, lang, cfg)
            }
            // Backends usually preserve the blank line between paragraphs; when
            // the split matches the batch size, map 1:1, else keep it as one.
            val parts = out.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
            translated += if (parts.size == batch.size) parts else listOf(out.trim())
            onProgress(i + 1, batches.size)
            if (i < batches.lastIndex) delay(paceMs)
        }
        val result = rebuildHtml(translated)
        if (result.isNotBlank()) cache.put(cacheKey, result)
        return result
    }

    // ---- Google (gtx) --------------------------------------------------------

    private class GoogleThrottle(val retryAfterSeconds: Long?) :
        IOException(GOOGLE_THROTTLE_MSG)

    private suspend fun translateGoogleChunk(text: String, lang: String): String =
        withContext(Dispatchers.IO) {
            val url = endpoint.toHttpUrl().newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", "auto")
                .addQueryParameter("tl", lang)
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", text)
                .build()
            var lastError: Exception? = null
            repeat(3) { attempt ->
                try {
                    // A real browser UA + Accept headers: Google gives
                    // bot-looking clients 403/429 (the old app UA was the
                    // "error de HTTP" the user saw).
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", BROWSER_UA)
                        .header("Accept", "*/*")
                        .header("Accept-Language", ACCEPT_LANGUAGE)
                        .build()
                    googleClient.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        when {
                            response.code == 429 -> throw GoogleThrottle(
                                response.header("Retry-After")?.trim()?.toLongOrNull()?.coerceIn(1, 30),
                            )
                            response.code != 200 -> throw IOException(googleErrorMessage(response.code))
                            // A 200 with an HTML consent/abuse page instead of
                            // JSON is also a block; fail with the same message
                            // instead of a raw JSONArray parse error.
                            !body.trimStart().startsWith("[") -> throw IOException(GOOGLE_BLOCKED_MSG)
                        }
                        return@withContext parseGtx(body)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    val wait = (e as? GoogleThrottle)?.retryAfterSeconds?.times(1000)
                        ?: (1200L * (attempt + 1))
                    delay(wait)
                }
            }
            throw lastError ?: IOException("No se pudo traducir")
        }

    /** Translated text from the gtx JSON: `[[["t1","o1",...],...],null,"en",…]`. */
    internal fun parseGtx(json: String): String {
        val root = JSONArray(json)
        val segments = root.getJSONArray(0)
        val out = StringBuilder()
        for (i in 0 until segments.length()) {
            val segment = segments.getJSONArray(i)
            if (segment.length() > 0 && !segment.isNull(0)) out.append(segment.getString(0))
        }
        return out.toString()
    }

    private fun googleErrorMessage(code: Int): String = when (code) {
        403 -> "Google rechazó la traducción (HTTP 403). Espera unos minutos o cambia a traducción con IA en Ajustes → Traducción."
        else -> "Traducción falló (HTTP $code). Reintenta o cambia de proveedor en Ajustes → Traducción."
    }

    // ---- OpenAI-compatible ---------------------------------------------------

    private class AiApiError(val code: Int, message: String) : IOException(message)

    private suspend fun translateAiChunk(text: String, lang: String, cfg: Config): String =
        withContext(Dispatchers.IO) {
            val url = cfg.aiBaseUrl.trim().trimEnd('/') + "/chat/completions"
            val payload = JSONObject().apply {
                put("model", cfg.aiModel.trim())
                put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", aiSystemPrompt(lang)))
                        .put(JSONObject().put("role", "user").put("content", text)),
                )
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${cfg.aiApiKey.trim()}")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            var lastError: Exception? = null
            repeat(2) { attempt ->
                try {
                    aiClient.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (response.code != 200) {
                            throw AiApiError(response.code, aiErrorMessage(response.code, body))
                        }
                        return@withContext parseOpenAi(body)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiApiError) {
                    // 4xx config errors (bad key, bad URL, bad model) will not
                    // improve with retries; 429 might.
                    lastError = e
                    if (e.code != 429) throw e
                    delay(2000L * (attempt + 1))
                } catch (e: Exception) {
                    lastError = e
                    delay(1000L * (attempt + 1))
                }
            }
            throw lastError ?: IOException("No se pudo traducir con IA")
        }

    /** `choices[0].message.content` of a chat-completions response. */
    internal fun parseOpenAi(json: String): String {
        val root = JSONObject(json)
        val choices = root.optJSONArray("choices") ?: throw IOException("Respuesta de IA no reconocida")
        val content = choices.getJSONObject(0).getJSONObject("message").optString("content")
        if (content.isBlank()) throw IOException("La IA devolvió una respuesta vacía")
        return content.trim()
    }

    private fun aiErrorMessage(code: Int, body: String): String {
        val detail = aiErrorFromBody(body)
        val hint = when (code) {
            401, 403 -> " Revisa la API key en Ajustes → Traducción."
            404 -> " Revisa la Base URL (para OpenAI termina en /v1) y el nombre del modelo."
            429 -> " El proveedor limitó las peticiones; espera un momento."
            else -> ""
        }
        val suffix = detail?.let { " · $it" } ?: ""
        return "La API de IA devolvió HTTP $code.$hint$suffix"
    }

    /** `{"error":{"message":"…"}}` of an OpenAI-compatible error body. */
    internal fun aiErrorFromBody(body: String): String? = try {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun aiSystemPrompt(lang: String): String =
        "You are a literary translator. Translate the user's text into the language with " +
            "ISO code \"$lang\". The text is a chapter of a novel split into blocks separated " +
            "by blank lines. Return ONLY the translation, keeping EXACTLY the same number of " +
            "blocks separated by blank lines, in the same order — never merge or split blocks. " +
            "Do not add notes, comments or explanations. Preserve the meaning and tone faithfully."

    // ---- Shared helpers ------------------------------------------------------

    /** Provider + model tag so switching provider/model never mixes cached results. */
    private fun cacheTag(cfg: Config): String = when (cfg.provider) {
        Config.Provider.GOOGLE -> "google"
        Config.Provider.AI ->
            "ai|" + cfg.aiModel.trim() + "@" +
                runCatching { cfg.aiBaseUrl.trim().toHttpUrl().host }.getOrDefault("host")
    }

    companion object {
        // Same browser identity Ao3Client uses (see there for the rationale).
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val ACCEPT_LANGUAGE = "es-ES,es;q=0.9,en;q=0.8"

        private const val GOOGLE_THROTTLE_MSG =
            "Google limitó las peticiones (HTTP 429). Espera un momento y reintenta, " +
                "o cambia a traducción con IA en Ajustes → Traducción."

        private const val GOOGLE_BLOCKED_MSG =
            "Google bloqueó la traducción (respuesta no válida). Espera unos minutos " +
                "o cambia a traducción con IA en Ajustes → Traducción."

        /**
         * Block-level text of a chapter fragment. Direct children of the body
         * (the sanitized chapter HTML has `<p>` blocks directly, occasionally
         * wrapped in one div, which gets unwrapped one level).
         */
        internal fun extractBlocks(html: String): List<String> {
            val doc = Jsoup.parseBodyFragment(html)
            var roots = doc.body().children()
            if (roots.size == 1 && roots[0].isBlock && roots[0].children().isNotEmpty()) {
                roots = roots[0].children()
            }
            return roots.mapNotNull { el ->
                val text = el.text().trim()
                text.takeIf { it.isNotEmpty() }
            }
        }

        /** Rebuilds the translated blocks as `<p>` elements. */
        internal fun rebuildHtml(blocks: List<String>): String =
            blocks.joinToString("\n") { "<p>${escapeHtml(it)}</p>" }

        internal fun escapeHtml(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}

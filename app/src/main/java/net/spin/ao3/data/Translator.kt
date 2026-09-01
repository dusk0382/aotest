package net.spin.ao3.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

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


    /** Cookies por host para el fallback gtx (solo sesión de Google). */
    private val googleCookies = object : CookieJar {
        private val store = HashMap<String, List<Cookie>>()

        override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(store) {
            val now = System.currentTimeMillis()
            (store[url.host].orEmpty().filter { it.expiresAt > now })
                .also { alive -> store[url.host] = alive }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(store) { store[url.host] = cookies }
        }
    }

    /** Browser-like client for the clients5 endpoint (no cookies needed there). */
    private val googleClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Fallback gtx client WITH a cookie jar (NID from the warm-up visit). */
    private val gtxClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .cookieJar(googleCookies)
        .build()

    /** LLMs are slow; give the AI backend a generous read timeout. */
    private val aiClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    /** Comfortably under the request ceilings; smaller batches look less bot-like. */
    private val chunkChars = 4_500
    /** Human-ish pause between batches (+ jitter, see the translate loop). */
    private val paceMs = 900L

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
            // Jitter: metronomic spacing is a bot fingerprint; human pacing is not.
            if (i < batches.lastIndex) delay(paceMs + Random.nextLong(0, 600))
        }
        val result = rebuildHtml(translated)
        if (result.isNotBlank()) cache.put(cacheKey, result)
        return result
    }

    // ---- Google (clients5 dict-chrome-ex primero, gtx con cookies de respaldo)

    private class GoogleThrottle(val retryAfterSeconds: Long?) :
        IOException(GOOGLE_THROTTLE_MSG)

    /**
     * Translates one chunk through Google. The gtx web endpoint is IP-throttled
     * for cookie-less clients (measured: HTTP 429 even with a browser UA), so:
     *  1. `clients5.google.com/translate_a/t?client=dict-chrome-ex` — the
     *     endpoint of Google Translate's own Chrome extension; measured 200
     *     without cookies, and it PRESERVES the "\n\n" block separators inside
     *     a single string (better for our 1:1 block mapping than gtx).
     *  2. Fallback: the classic gtx endpoint after a browser-like warm-up that
     *     picks up session cookies (NID) in the jar.
     */
    private suspend fun translateGoogleChunk(text: String, lang: String): String {
        var lastError: Exception? = null
        try {
            return translateClients5Chunk(text, lang)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
        }
        try {
            return translateGtxChunk(text, lang)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
        }
        throw lastError ?: IOException("No se pudo traducir")
    }

    private suspend fun translateClients5Chunk(text: String, lang: String): String =
        withContext(Dispatchers.IO) {
            val url = "https://clients5.google.com/translate_a/t".toHttpUrl().newBuilder()
                .addQueryParameter("client", "dict-chrome-ex")
                .addQueryParameter("sl", "auto")
                .addQueryParameter("tl", lang)
                .addQueryParameter("q", text)
                .build()
            fetchGoogleJson(googleClient, url, 3)
                ?.let { return@withContext parseClients5(it) }
            throw IOException(GOOGLE_THROTTLE_MSG)
        }

    private suspend fun translateGtxChunk(text: String, lang: String): String =
        withContext(Dispatchers.IO) {
            warmUpGtxCookies()
            val url = endpoint.toHttpUrl().newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", "auto")
                .addQueryParameter("tl", lang)
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", text)
                .build()
            fetchGoogleJson(gtxClient, url, 2)
                ?.let { return@withContext parseGtx(it) }
            throw IOException(GOOGLE_THROTTLE_MSG)
        }

    /**
     * One HTTP shape for both endpoints: up to [attempts] tries with patient
     * throttle backoff (Retry-After honored up to 45 s, else 2/6/15 s). Returns
     * the JSON body of the first 200, or null if every try failed (the last
     * error is logged) — the caller throws the actionable throttle message.
     */
    private suspend fun fetchGoogleJson(client: OkHttpClient, url: HttpUrl, attempts: Int): String? {
        val backoff = longArrayOf(2_000L, 6_000L, 15_000L)
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_UA)
                    .header("Accept", "*/*")
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.code == 429 -> throw GoogleThrottle(
                            response.header("Retry-After")?.trim()?.toLongOrNull()?.coerceIn(1, 45),
                        )
                        response.code != 200 -> throw IOException(googleErrorMessage(response.code))
                        // A 200 with an HTML consent/abuse page instead of JSON
                        // is also a block; treat it like the throttle path.
                        !body.trimStart().startsWith("[") -> throw IOException(GOOGLE_BLOCKED_MSG)
                    }
                    return body
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG_GOOGLE, "intento ${attempt + 1}/$attempts falló: ${e.message}")
                delay((e as? GoogleThrottle)?.retryAfterSeconds?.times(1000) ?: backoff[attempt.coerceAtMost(backoff.lastIndex)])
            }
        }
        lastError?.let { Log.w(TAG_GOOGLE, "agotado: ${it.message}") }
        return null
    }

    /**
     * Session cookies for the gtx fallback: a real browser arrives with NID
     * from translate.google.com; a cold client is exactly what gets 429'd.
     * One warm-up per process.
     */
    @Volatile
    private var gtxWarmedUp = false

    private suspend fun warmUpGtxCookies() {
        if (gtxWarmedUp) return
        withContext(Dispatchers.IO) {
            runCatching {
                gtxClient.newCall(
                    Request.Builder()
                        .url("https://translate.google.com/")
                        .header("User-Agent", BROWSER_UA)
                        .header("Accept-Language", ACCEPT_LANGUAGE)
                        .build(),
                ).execute().use { it.body?.string() }
            }
        }
        gtxWarmedUp = true
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

    /**
     * Translated text from the clients5 (`dict-chrome-ex`) JSON. Verified live
     * shape: `[["traducción","en"]]` — the whole chunk as ONE string with the
     * "\n\n" separators preserved (element 1 is the detected source language).
     * Other documented shapes handled: `["t1","t2"]` (plain strings) and the
     * nested gtx-like `[[["t","o",...],...]]`.
     */
    internal fun parseClients5(json: String): String {
        if (!json.trimStart().startsWith("[")) {
            throw IOException("Respuesta de traducción no reconocida")
        }
        val root = JSONArray(json)
        when (val first = root.opt(0)) {
            is String -> {
                val out = StringBuilder()
                for (i in 0 until root.length()) {
                    val s = root.optString(i)
                    if (s.isNotEmpty() && s != "null") out.append(s)
                }
                if (out.isNotEmpty()) return out.toString()
            }
            is JSONArray -> {
                if (first.opt(0) is String) {
                    // [["t","src"], ...]: translation is element 0 of each pair.
                    val out = StringBuilder()
                    for (i in 0 until root.length()) {
                        val el = root.optJSONArray(i) ?: continue
                        val s = el.optString(0)
                        if (s.isNotEmpty() && s != "null") out.append(s)
                    }
                    if (out.isNotEmpty()) return out.toString()
                } else {
                    return parseGtx(json)
                }
            }
        }
        throw IOException("Respuesta de traducción no reconocida")
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

    internal fun aiSystemPrompt(lang: String): String =
        "You are a professional literary translator specializing in fiction. " +
            "Translate the user's text into the language with ISO code \"$lang\", " +
            "using natural phrasing and the conventions of that language.\n\n" +
            "STRUCTURE (critical):\n" +
            "- The text is a novel chapter divided into blocks separated by blank lines.\n" +
            "- Return the translation with EXACTLY the same number of blocks, same order.\n" +
            "- Never merge or split blocks. Preserve line breaks inside a block.\n" +
            "- If a block is already in the target language, return it unchanged.\n\n" +
            "DIALOGUE AND PUNCTUATION (critical):\n" +
            "- PRESERVE all quotation marks around dialogue EXACTLY as in the source: " +
            "same characters (\" \" ' ' « » „ “), same positions. Translate the words " +
            "INSIDE the quotes; never remove, replace or normalize the quotes themselves.\n" +
            "- Same for dialogue dashes, ellipses and ALL-CAPS emphasis: keep them.\n" +
            "- Do not add quotation marks or dashes that are not in the source.\n\n" +
            "FIDELITY:\n" +
            "- Translate everything: never omit, summarize or expand sentences.\n" +
            "- Preserve meaning, tone, register, tense and narrative style.\n" +
            "- Translate idioms naturally instead of literally.\n" +
            "- Keep character and place names unchanged unless a well-established " +
            "translation exists. Never add translator notes, brackets or glosses.\n\n" +
            "OUTPUT:\n" +
            "- Return ONLY the translated text: no preamble, no explanations, " +
            "and no quotation marks around the whole output that are not in the source."

    // ---- Shared helpers ------------------------------------------------------

    /** Provider + model tag so switching provider/model never mixes cached results. */
    private fun cacheTag(cfg: Config): String = when (cfg.provider) {
        Config.Provider.GOOGLE -> "google"
        Config.Provider.AI ->
            "ai|" + cfg.aiModel.trim() + "@" +
                runCatching { cfg.aiBaseUrl.trim().toHttpUrl().host }.getOrDefault("host") +
                "|" + AI_PROMPT_VERSION
    }

    companion object {
        // Versión del prompt del proveedor IA: súbelo (p3, p4...) cuando cambie
        // aiSystemPrompt, para que las traducciones hechas con el prompt
        // anterior no se sirvan desde la caché.
        private const val AI_PROMPT_VERSION = "p2"

        // Same browser identity Ao3Client uses (see there for the rationale).
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val ACCEPT_LANGUAGE = "es-ES,es;q=0.9,en;q=0.8"

        private const val TAG_GOOGLE = "TranslatorGoogle"

        private const val GOOGLE_THROTTLE_MSG =
            "Google limitó las traducciones en esta red por ahora (HTTP 429). " +
                "Espera unos minutos y reintenta, o cambia a traducción con IA en Ajustes → Traducción."

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

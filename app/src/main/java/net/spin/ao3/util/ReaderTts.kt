package net.spin.ao3.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Compose-friendly wrapper around Android TextToSpeech for the reader. */
@Composable
fun rememberReaderTts(
    onUtteranceDone: () -> Unit = {},
    onWordRange: (String) -> Unit = {},
): ReaderTts {
    val context = LocalContext.current.applicationContext
    val tts = remember { ReaderTts(context, onUtteranceDone, onWordRange) }
    DisposableEffect(tts) {
        onDispose { tts.shutdown() }
    }
    return tts
}

class ReaderTts(
    context: Context,
    private val onUtteranceDone: () -> Unit,
    private val onWordRange: (String) -> Unit = {},
) {
    private companion object {
        const val MAX_CHUNK = 4000
    }

    private var engine: TextToSpeech? = null
    private val lock = Any()

    var ready by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    /** True when paused with the current chunk and remaining queue preserved. */
    var paused by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var lastText: String? = null
    private var lastRate: Float = 1.0f
    private var pendingText: String? = null
    private var pendingRate: Float = 1.0f
    private var queue = ArrayDeque<String>()
    private var currentChunk: String? = null

    init {
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                engine?.language = Locale.getDefault()
                pendingText?.let {
                    pendingText = null
                    doSpeak(it, pendingRate)
                }
            } else {
                error = "No hay motor de voz disponible en este dispositivo"
            }
        }
    }

    /** Starts reading [text], replacing any current speech. */
    fun speak(text: String, rate: Float) {
        lastText = text
        lastRate = rate
        error = null
        engine?.stop()
        synchronized(lock) {
            paused = false
            queue = ArrayDeque(chunk(text))
            currentChunk = queue.removeFirstOrNull()
            val first = currentChunk
            if (first == null) {
                speaking = false
                onWordRange("")
                return
            }
            doSpeak(first, rate)
        }
    }

    private fun chunk(text: String): List<String> {
        if (text.length <= MAX_CHUNK) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + MAX_CHUNK).coerceAtMost(text.length)
            if (end < text.length) {
                val space = text.lastIndexOf(' ', end)
                if (space > start + MAX_CHUNK / 2) end = space
            }
            val piece = text.substring(start, end).trim()
            if (piece.isNotBlank()) chunks += piece
            start = end
        }
        return chunks
    }

    private fun doSpeak(text: String, rate: Float) {
        val e = engine
        if (!ready || e == null) {
            pendingText = text
            pendingRate = rate
            return
        }
        currentChunk = text
        e.setSpeechRate(rate)
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (!paused && currentChunk == text) speaking = true
            }

            /** Many Android engines provide this callback; unsupported engines
             * simply omit the highlight while continuing to speak normally. */
            @Suppress("DEPRECATION")
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (paused || currentChunk != text) return
                val safeStart = start.coerceIn(0, text.length)
                val safeEnd = end.coerceIn(safeStart, text.length)
                if (safeEnd > safeStart) onWordRange(text.substring(safeStart, safeEnd))
            }

            override fun onDone(utteranceId: String?) {
                synchronized(lock) {
                    // stop()/pause() can still produce a delayed onDone callback.
                    if (paused || currentChunk != text) return
                    val next = queue.removeFirstOrNull()
                    if (next != null) {
                        currentChunk = next
                        doSpeak(next, rate)
                    } else {
                        currentChunk = null
                        speaking = false
                        paused = false
                        onWordRange("")
                        onUtteranceDone()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (paused) return
                speaking = false
                currentChunk = null
                onWordRange("")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (paused) return
                speaking = false
                currentChunk = null
                onWordRange("")
            }
        })
        e.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ao3tts")
    }

    /** Pauses at the current engine chunk; resume continues that chunk. */
    fun pause() {
        if (!speaking) return
        engine?.stop()
        speaking = false
        paused = currentChunk != null
        if (!paused) onWordRange("")
    }

    /** Stops and discards the current queue. It does not leave a fake paused state. */
    fun stop() {
        engine?.stop()
        synchronized(lock) {
            queue.clear()
            currentChunk = null
            paused = false
            speaking = false
        }
        onWordRange("")
    }

    /** Resumes the current chunk instead of replaying the whole chapter. */
    fun resume() {
        synchronized(lock) {
            if (!paused) return
            paused = false
            val current = currentChunk
            if (current != null) doSpeak(current, lastRate) else speak(lastText ?: return, lastRate)
        }
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        speaking = false
        paused = false
        synchronized(lock) {
            queue.clear()
            currentChunk = null
        }
        onWordRange("")
    }
}

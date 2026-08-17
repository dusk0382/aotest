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

/**
 * Compose-friendly wrapper around Android's [TextToSpeech] for the reader.
 *
 * Exposes [ready]/[speaking]/[paused] as Compose state so the UI can react to
 * the engine, and shuts the engine down automatically when the composable
 * leaves composition. [onUtteranceDone] fires when a text finishes reading —
 * the reader uses it to auto-advance to the next chapter.
 */
@Composable
fun rememberReaderTts(onUtteranceDone: () -> Unit = {}): ReaderTts {
    val context = LocalContext.current.applicationContext
    val tts = remember { ReaderTts(context, onUtteranceDone) }
    DisposableEffect(tts) {
        onDispose { tts.shutdown() }
    }
    return tts
}

class ReaderTts(context: Context, private val onUtteranceDone: () -> Unit) {

    private var engine: TextToSpeech? = null
    var ready by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    /** True after [stop]: the last read text is kept so [resume] can replay it. */
    var paused by mutableStateOf(false)
        private set

    private var lastText: String? = null
    private var lastRate: Float = 1.0f
    private var pendingText: String? = null
    private var pendingRate: Float = 1.0f

    init {
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                engine?.language = Locale.getDefault()
                pendingText?.let { doSpeak(it, pendingRate) }
                pendingText = null
            }
        }
    }

    /** Starts reading [text] at [rate] (0.5..2.0), replacing any current speech. */
    fun speak(text: String, rate: Float) {
        lastText = text
        lastRate = rate
        paused = false
        doSpeak(text, rate)
    }

    private fun doSpeak(text: String, rate: Float) {
        val e = engine
        if (!ready || e == null) {
            pendingText = text
            pendingRate = rate
            return
        }
        e.setSpeechRate(rate)
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking = true
                paused = false
            }

            override fun onDone(utteranceId: String?) {
                speaking = false
                paused = false
                onUtteranceDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speaking = false
                paused = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                speaking = false
                paused = false
            }
        })
        e.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ao3tts")
    }

    /** Stops speaking but keeps the last text so [resume] can replay it. */
    fun stop() {
        engine?.stop()
        if (speaking) paused = true
        speaking = false
    }

    /** Replays the last read text (used after [stop]). */
    fun resume() {
        val t = lastText ?: return
        speak(t, lastRate)
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        speaking = false
    }
}

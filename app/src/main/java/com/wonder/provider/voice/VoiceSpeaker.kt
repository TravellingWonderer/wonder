package com.wonder.provider.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Reads Wonder's replies aloud when the provider spoke first, so a spoken question gets a
 * spoken answer.
 */
class VoiceSpeaker(context: Context) {

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var ready = false
    private var pending: String? = null

    private val engine = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            pending?.let { speak(it) }
            pending = null
        }
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Required by the platform interface")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
    }

    fun speak(text: String) {
        val spoken = readable(text)
        if (spoken.isBlank()) return
        if (!ready) {
            pending = spoken
            return
        }
        engine.language = Locale.getDefault().takeIf { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
            ?: Locale.ENGLISH
        engine.setSpeechRate(1.02f)
        engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        pending = null
        if (ready) engine.stop()
        _isSpeaking.value = false
    }

    fun release() {
        stop()
        engine.shutdown()
        ready = false
    }

    /** Emoji and stray symbols read terribly out loud. */
    private fun readable(text: String) = text
        .replace(Regex("[\\p{So}\\p{Cn}]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val UTTERANCE_ID = "wonder"
    }
}

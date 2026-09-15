package com.wonder.provider.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface VoiceState {
    data object Idle : VoiceState

    /** Mic is open. [partial] is the running transcript, [level] drives the orb, 0f..1f. */
    data class Listening(val partial: String = "", val level: Float = 0f) : VoiceState

    /** A complete utterance, ready to be sent. */
    data class Heard(val text: String) : VoiceState

    data class Failed(val message: String) : VoiceState
}

/**
 * Thin wrapper over [SpeechRecognizer] that exposes listening as state rather than callbacks,
 * so the conversation UI can react to the provider's voice the same way it reacts to typing.
 */
class SpeechController(private val context: Context) {

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var transcript = ""

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() = onMain {
        if (!isAvailable) {
            _state.value = VoiceState.Failed("No speech recognition on this device")
            return@onMain
        }
        transcript = ""
        _state.value = VoiceState.Listening()

        val speech = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        speech.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    /** Stop capturing but keep whatever was said. */
    fun finish() = onMain {
        recognizer?.stopListening()
        // Some engines never deliver onResults after a manual stop; fall back to partials.
        main.postDelayed({
            if (_state.value is VoiceState.Listening) {
                _state.value = transcript.trim().takeIf { it.isNotEmpty() }
                    ?.let(VoiceState::Heard)
                    ?: VoiceState.Idle
            }
        }, MANUAL_STOP_GRACE_MS)
    }

    fun cancel() = onMain {
        recognizer?.cancel()
        transcript = ""
        _state.value = VoiceState.Idle
    }

    /** Acknowledge a [VoiceState.Heard] or [VoiceState.Failed] so the mic can be used again. */
    fun reset() {
        _state.value = VoiceState.Idle
    }

    fun release() = onMain {
        recognizer?.destroy()
        recognizer = null
        _state.value = VoiceState.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = VoiceState.Listening()
        }

        override fun onRmsChanged(rmsdB: Float) {
            val current = _state.value as? VoiceState.Listening ?: return
            _state.value = current.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val heard = partialResults.firstResult() ?: return
            transcript = heard
            val current = _state.value as? VoiceState.Listening ?: VoiceState.Listening()
            _state.value = current.copy(partial = heard)
        }

        override fun onResults(results: Bundle?) {
            val heard = results.firstResult()?.trim().orEmpty().ifBlank { transcript.trim() }
            _state.value = if (heard.isBlank()) VoiceState.Idle else VoiceState.Heard(heard)
        }

        override fun onError(error: Int) {
            // A manual stop often surfaces as a "no match" — keep what we already heard.
            val salvaged = transcript.trim()
            _state.value = when {
                salvaged.isNotEmpty() -> VoiceState.Heard(salvaged)
                error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceState.Idle
                else -> VoiceState.Failed(message(error))
            }
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onEndOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun message(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "I lost the connection while listening"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "I need microphone access to listen"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Still finishing the last one — try again"
        else -> "I didn't catch that"
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    private companion object {
        const val MANUAL_STOP_GRACE_MS = 1200L
    }
}

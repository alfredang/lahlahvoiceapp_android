package sg.com.tertiarycourses.lahlahvoice.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * One dictation with Android's speech recogniser. It uses the on-device recogniser when the phone
 * has one (Android 12+ with the language downloaded), so audio stays on the phone. Must be used
 * from the main thread.
 *
 * Long pauses don't end it: it keeps listening and joins the pieces until [stop] is called.
 */
class Dictation(
    private val context: Context,
    private val languageTag: String,
    private val onPartial: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onFinished: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private val pieces = mutableListOf<String>()
    private var stopping = false
    private var finished = false
    /** The words heard so far in the current piece, in case the final result never comes. */
    private var lastPartial = ""
    private val handler = Handler(Looper.getMainLooper())

    /** Cleared when the on-device recogniser lacks this language, to use the standard one. */
    private var preferOnDevice = true

    val isOnDevice: Boolean
        get() = preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context) && !isOnDevice) {
            onError("This phone has no speech recogniser. Install or update the Google app.")
            return
        }
        listen()
    }

    /** Finish: the recogniser delivers what it heard, then [onFinished] runs once. */
    fun stop() {
        stopping = true
        val active = recognizer ?: return finish()
        active.stopListening()
        // Some recognisers never call back when they heard nothing. Don't wait forever.
        handler.postDelayed({
            if (!finished) {
                if (lastPartial.isNotBlank()) pieces += lastPartial
                lastPartial = ""
                finish()
            }
        }, 2500)
    }

    fun cancel() {
        finished = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        release()
    }

    private fun listen() {
        release()
        lastPartial = ""
        val recognizer = if (isOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        this.recognizer = recognizer
        recognizer.setRecognitionListener(listener)
        val intent = recognitionIntent()
        recognizer.startListening(intent)
    }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Let the speaker pause to think without ending the dictation.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }

    /** The on-device recogniser has no pack for this language yet: fetch it, and carry on now. */
    private fun fallBackFromOnDevice() {
        val onDevice = recognizer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { onDevice?.triggerModelDownload(recognitionIntent()) }
        }
        preferOnDevice = false
        listen()
    }

    private fun currentText(extra: String = "") =
        (pieces + extra).filter { it.isNotBlank() }.joinToString(" ")

    private fun finish() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        release()
        onFinished(currentText())
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) lastPartial = text
            onPartial(currentText(text))
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) pieces += text else if (lastPartial.isNotBlank()) pieces += lastPartial
            lastPartial = ""
            // The recogniser ended on a pause. Keep listening unless the user finished.
            if (stopping) finish() else listen()
        }

        override fun onError(error: Int) {
            when {
                // Silence or nothing recognised: keep going, or finish with what we have.
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    if (stopping || finished) finish() else listen()
                error == SpeechRecognizer.ERROR_CLIENT && stopping -> finish()
                isOnDevice && error in languageErrors && !stopping -> fallBackFromOnDevice()
                pieces.isNotEmpty() -> finish()
                else -> {
                    finished = true
                    release()
                    onError(message(error))
                }
            }
        }
    }

    private val languageErrors = setOf(
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    )

    private fun message(error: Int) = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "LahLah Voice needs microphone access. Open the app to allow it."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs the offline language pack. Download it in your phone's speech settings."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Try again in a moment."
        SpeechRecognizer.ERROR_AUDIO -> "The microphone couldn't be opened."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "This language isn't available for speech recognition on this phone."
        else -> "Didn't catch that. Try again."
    }
}

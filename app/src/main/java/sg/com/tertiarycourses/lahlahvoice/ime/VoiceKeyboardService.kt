package sg.com.tertiarycourses.lahlahvoice.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sg.com.tertiarycourses.lahlahvoice.MainActivity
import sg.com.tertiarycourses.lahlahvoice.R
import sg.com.tertiarycourses.lahlahvoice.core.DictationPipeline
import sg.com.tertiarycourses.lahlahvoice.core.DictationRecord
import sg.com.tertiarycourses.lahlahvoice.core.Store
import sg.com.tertiarycourses.lahlahvoice.speech.Dictation

/**
 * The LahLah Voice keyboard: tap the mic, speak, tap again, and the finished text goes into
 * whatever app you're typing in. Unlike iOS, Android lets a keyboard use the microphone, once the
 * app has been given permission.
 */
class VoiceKeyboardService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var dictation: Dictation? = null
    private var working = false
    private lateinit var status: TextView
    private lateinit var mic: Button

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard, null)
        // Keep the keys above the gesture bar on edge-to-edge phones.
        val basePadding = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = basePadding + bars.bottom)
            insets
        }
        status = view.findViewById(R.id.status)
        mic = view.findViewById(R.id.mic)
        mic.setOnClickListener { toggle() }
        view.findViewById<Button>(R.id.space).setOnClickListener { currentInputConnection?.commitText(" ", 1) }
        view.findViewById<Button>(R.id.backspace).setOnClickListener { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        view.findViewById<Button>(R.id.enter).setOnClickListener { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
        view.findViewById<Button>(R.id.switchKeyboard).setOnClickListener {
            if (!switchToPreviousInputMethod()) switchToNextInputMethod(false)
        }
        showIdle()
        return view
    }

    /** The mic is useful even with a hardware keyboard attached, so always show. */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        dictation?.cancel()
        dictation = null
        if (::mic.isInitialized) showIdle()
    }

    override fun onDestroy() {
        dictation?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private val hasMicrophone: Boolean
        get() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun toggle() {
        if (working) return
        dictation?.let {
            it.stop()
            return
        }
        if (!hasMicrophone) {
            status.setText(R.string.keyboard_needs_mic)
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val store = Store.get(this)
        val session = Dictation(
            context = this,
            languageTag = store.settings.value.languageTag,
            onPartial = { status.text = it.ifBlank { getString(R.string.keyboard_listening) } },
            onLevel = { mic.scaleX = 1f + it * 0.12f; mic.scaleY = mic.scaleX },
            onFinished = { raw -> finish(raw, store) },
            onError = { message ->
                dictation = null
                showIdle()
                status.text = message
            },
        )
        dictation = session
        mic.setBackgroundResource(R.drawable.mic_button_listening)
        mic.text = "■"
        status.setText(R.string.keyboard_listening)
        session.start()
    }

    private fun finish(raw: String, store: Store) {
        dictation = null
        mic.scaleX = 1f
        mic.scaleY = 1f
        if (raw.isBlank()) {
            showIdle()
            status.text = "Didn't catch that."
            return
        }
        working = true
        status.setText(R.string.keyboard_working)
        scope.launch {
            val result = DictationPipeline.process(raw, store)
            working = false
            showIdle()
            if (result.text.isNotEmpty()) {
                val connection = currentInputConnection
                val before = connection?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                val spacer = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
                connection?.commitText(spacer + result.text, 1)
                store.record(DictationRecord(raw = raw, text = result.text, usedModel = result.usedModel))
            }
        }
    }

    private fun showIdle() {
        mic.setBackgroundResource(R.drawable.mic_button)
        mic.setText(R.string.keyboard_mic_glyph)
        status.setText(R.string.keyboard_tap_to_speak)
    }
}

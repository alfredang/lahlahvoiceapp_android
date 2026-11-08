package sg.com.tertiarycourses.lahlahvoice.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import sg.com.tertiarycourses.lahlahvoice.core.DictationPipeline
import sg.com.tertiarycourses.lahlahvoice.core.DictationRecord
import sg.com.tertiarycourses.lahlahvoice.core.DictationResult
import sg.com.tertiarycourses.lahlahvoice.core.Store
import sg.com.tertiarycourses.lahlahvoice.speech.Dictation
import java.text.DateFormat
import java.util.Date

private enum class Phase { IDLE, LISTENING, WORKING }

@Composable
fun DictateScreen(store: Store, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by store.history.collectAsState()
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<DictationResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var dictation by remember { mutableStateOf<Dictation?>(null) }
    var hasMic by remember { mutableStateOf(micGranted(context)) }
    var keyboardEnabled by remember { mutableStateOf(keyboardEnabled(context)) }

    // Re-check the keyboard after the user comes back from Settings.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                keyboardEnabled = keyboardEnabled(context)
                hasMic = micGranted(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            dictation?.cancel()
        }
    }

    fun start() {
        result = null
        message = null
        partial = ""
        val session = Dictation(
            context = context,
            languageTag = store.settings.value.languageTag,
            onPartial = { partial = it },
            onLevel = { level = it },
            onFinished = { raw ->
                dictation = null
                level = 0f
                if (raw.isBlank()) {
                    phase = Phase.IDLE
                    message = "Didn't catch that. Try again."
                } else {
                    phase = Phase.WORKING
                    scope.launch {
                        val processed = DictationPipeline.process(raw, store)
                        result = processed
                        phase = Phase.IDLE
                        if (processed.text.isNotEmpty()) {
                            store.record(DictationRecord(raw = raw, text = processed.text, usedModel = processed.usedModel))
                        }
                    }
                }
            },
            onError = {
                dictation = null
                phase = Phase.IDLE
                level = 0f
                message = it
            },
        )
        dictation = session
        phase = Phase.LISTENING
        session.start()
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        if (granted) start() else message = "LahLah Voice needs the microphone to hear you."
    }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("LahLah Voice", "Say what you mean. It writes what you intended.") }

        item {
            Card {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(112.dp)
                            .scale(1f + level * 0.15f)
                            .background(if (phase == Phase.LISTENING) Brand.ink else Brand.ember, CircleShape)
                            .clickable(enabled = phase != Phase.WORKING) {
                                when {
                                    phase == Phase.LISTENING -> dictation?.stop()
                                    !hasMic -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                                    else -> start()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (phase == Phase.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (phase == Phase.LISTENING) "Finish" else "Start dictating",
                            tint = Color.White, modifier = Modifier.size(48.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when (phase) {
                        Phase.LISTENING -> partial.ifBlank { "Listening… tap again when you're done." }
                        Phase.WORKING -> "Writing…"
                        Phase.IDLE -> message ?: "Tap the mic and speak naturally. Ums, restarts and \"no wait\" corrections are handled for you."
                    },
                    color = Brand.muted, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        result?.takeIf { it.text.isNotEmpty() }?.let { finished ->
            item {
                Card {
                    Text(finished.text, fontSize = 18.sp, color = Brand.ink)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { copy(context, finished.text) },
                            colors = ButtonDefaults.buttonColors(containerColor = Brand.ember),
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                            Text("  Copy")
                        }
                        Spacer(Modifier.size(8.dp))
                        OutlinedButton(onClick = { share(context, finished.text) }) {
                            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                            Text("  Share")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(if (finished.usedModel) "Gemini Nano" else "Rules", fontSize = 12.sp, color = Brand.muted)
                    }
                    if (finished.raw.trim() != finished.text) {
                        Spacer(Modifier.height(8.dp))
                        Text("You said: ${finished.raw}", fontSize = 13.sp, color = Brand.muted)
                    }
                }
            }
        }

        if (!keyboardEnabled) {
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Keyboard, null, tint = Brand.ember)
                        Text("  Dictate in any app", fontWeight = FontWeight.SemiBold, color = Brand.ink)
                    }
                    Text(
                        "Turn on the LahLah Voice keyboard, then switch to it in WhatsApp, Gmail, ChatGPT or any app and tap the mic.",
                        color = Brand.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.ember),
                    ) { Text("Turn on the keyboard") }
                }
            }
        } else {
            item {
                OutlinedButton(onClick = {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                }) {
                    Icon(Icons.Filled.Keyboard, null, Modifier.size(18.dp))
                    Text("  Switch keyboard")
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("History")
                    Spacer(Modifier.weight(1f))
                    Text("Clear", color = Brand.ember, fontSize = 13.sp,
                        modifier = Modifier.clickable { store.clearHistory() }.padding(top = 12.dp, end = 4.dp))
                }
            }
            items(history.take(100), key = { it.id }) { record ->
                Card {
                    Text(record.text, color = Brand.ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.date)),
                            fontSize = 12.sp, color = Brand.muted,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { copy(context, record.text) }) {
                            Icon(Icons.Filled.ContentCopy, "Copy", tint = Brand.muted, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { store.deleteRecord(record.id) }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = Brand.muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    LaunchedEffect(Unit) { keyboardEnabled = keyboardEnabled(context) }
}

private fun micGranted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun keyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("LahLah Voice", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

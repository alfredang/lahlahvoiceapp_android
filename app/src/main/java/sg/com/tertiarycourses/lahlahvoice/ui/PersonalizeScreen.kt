package sg.com.tertiarycourses.lahlahvoice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sg.com.tertiarycourses.lahlahvoice.core.DictationPipeline
import sg.com.tertiarycourses.lahlahvoice.core.DictionaryEntry
import sg.com.tertiarycourses.lahlahvoice.core.GeminiEditor
import sg.com.tertiarycourses.lahlahvoice.core.ModelStatus
import sg.com.tertiarycourses.lahlahvoice.core.PersonalNote
import sg.com.tertiarycourses.lahlahvoice.core.Store
import sg.com.tertiarycourses.lahlahvoice.core.WritingStyle

private val languages = listOf(
    "en-US" to "English (US)", "en-GB" to "English (UK)", "en-AU" to "English (Australia)",
    "en-IN" to "English (India)", "zh-CN" to "Chinese (Mandarin)", "ms-MY" to "Malay",
)

@Composable
fun PersonalizeScreen(store: Store, modifier: Modifier = Modifier) {
    val settings by store.settings.collectAsState()
    val notes by store.notes.collectAsState()
    val dictionary by store.dictionary.collectAsState()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<ModelStatus?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var newNote by remember { mutableStateOf("") }
    var newWord by remember { mutableStateOf("") }
    var sample by remember(settings.writingSample) { mutableStateOf(settings.writingSample) }

    LaunchedEffect(Unit) { status = GeminiEditor.status() }

    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ScreenTitle("Personalize", "Make LahLah Voice understand and write like you.") }

        item { SectionTitle("Understanding") }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Understand what I meant", fontWeight = FontWeight.SemiBold, color = Brand.ink)
                        Text("Uses Gemini Nano, which runs on this phone.", fontSize = 13.sp, color = Brand.muted)
                    }
                    Switch(
                        checked = settings.useModel,
                        onCheckedChange = { on -> store.updateSettings { it.copy(useModel = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Brand.ember),
                    )
                }
                status?.let { current ->
                    Spacer(Modifier.size(10.dp))
                    Text(current.title, fontWeight = FontWeight.Medium, color = Brand.ink, fontSize = 14.sp)
                    Text(current.detail, fontSize = 13.sp, color = Brand.muted)
                    if (current == ModelStatus.DOWNLOADABLE || downloading) {
                        Spacer(Modifier.size(8.dp))
                        if (downloading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand.ember)
                        } else {
                            Button(
                                onClick = {
                                    downloading = true
                                    downloadError = null
                                    scope.launch {
                                        runCatching { GeminiEditor.download().collect {} }
                                            .onFailure { downloadError = it.message ?: "Download failed." }
                                        downloading = false
                                        status = GeminiEditor.status()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Brand.ember),
                            ) { Text("Download Gemini Nano") }
                        }
                    }
                    downloadError?.let { Text(it, color = Brand.ember, fontSize = 13.sp) }
                }
            }
        }

        item { SectionTitle("Writing style") }
        item {
            Card {
                WritingStyle.entries.forEach { style ->
                    Row(
                        Modifier.fillMaxWidth().clickable { store.updateSettings { it.copy(style = style) } }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.style == style,
                            onClick = { store.updateSettings { it.copy(style = style) } },
                            colors = RadioButtonDefaults.colors(selectedColor = Brand.ember),
                        )
                        Column {
                            Text(style.title, color = Brand.ink, fontWeight = FontWeight.Medium)
                            Text(style.summary, fontSize = 13.sp, color = Brand.muted)
                        }
                    }
                }
            }
        }

        item { SectionTitle("About you") }
        item {
            Card {
                Text(
                    "Your work, the topics you talk about and how you like things written. These help it hear the right word. They're never added to your text.",
                    fontSize = 13.sp, color = Brand.muted,
                )
                notes.forEach { note ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${note.text}", Modifier.weight(1f), color = Brand.ink)
                        IconButton(onClick = { store.setNotes(notes.filterNot { it.id == note.id }) }) {
                            Icon(Icons.Filled.Close, "Remove", tint = Brand.muted)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newNote, onValueChange = { newNote = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("e.g. I teach group theory") }, singleLine = true,
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            store.setNotes(notes + PersonalNote(text = newNote.trim()))
                            newNote = ""
                        },
                        enabled = newNote.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.ember),
                    ) { Text("Add") }
                }
                Spacer(Modifier.size(12.dp))
                Text("A sample of your writing", fontWeight = FontWeight.Medium, color = Brand.ink)
                OutlinedTextField(
                    value = sample,
                    onValueChange = {
                        sample = it
                        store.updateSettings { s -> s.copy(writingSample = it) }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("Paste a message or email you wrote. Dictation will match its tone.") },
                )
                val words = sample.split(Regex("\\s+")).count { it.isNotEmpty() }
                Text(
                    if (words >= DictationPipeline.MIN_SAMPLE_WORDS) "$words words. Dictation will follow this style."
                    else "$words of at least ${DictationPipeline.MIN_SAMPLE_WORDS} words.",
                    fontSize = 12.sp, color = Brand.muted,
                )
            }
        }

        item { SectionTitle("Dictionary") }
        item {
            Card {
                Text("Names and terms you want spelled your way.", fontSize = 13.sp, color = Brand.muted)
                dictionary.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.term, Modifier.weight(1f), color = Brand.ink)
                        IconButton(onClick = { store.setDictionary(dictionary.filterNot { it.term == entry.term }) }) {
                            Icon(Icons.Filled.Close, "Remove", tint = Brand.muted)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newWord, onValueChange = { newWord = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("e.g. LahLah Voice") }, singleLine = true,
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            val term = newWord.trim()
                            if (dictionary.none { it.term.equals(term, ignoreCase = true) }) {
                                store.setDictionary(dictionary + DictionaryEntry(term))
                            }
                            newWord = ""
                        },
                        enabled = newWord.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.ember),
                    ) { Text("Add") }
                }
            }
        }

        item { SectionTitle("Language") }
        item {
            Card {
                languages.forEach { (tag, name) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { store.updateSettings { it.copy(languageTag = tag) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.languageTag == tag,
                            onClick = { store.updateSettings { it.copy(languageTag = tag) } },
                            colors = RadioButtonDefaults.colors(selectedColor = Brand.ember),
                        )
                        Text(name, color = Brand.ink)
                    }
                }
                Text(
                    "Fillers and spoken corrections are handled for English. Other languages are cleaned up by Gemini Nano where available.",
                    fontSize = 12.sp, color = Brand.muted,
                )
            }
        }

        item { SectionTitle("History") }
        item {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep dictation history", color = Brand.ink, fontWeight = FontWeight.Medium)
                        Text("Stored on this phone only.", fontSize = 13.sp, color = Brand.muted)
                    }
                    Switch(
                        checked = settings.keepHistory,
                        onCheckedChange = { on -> store.updateSettings { it.copy(keepHistory = on) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Brand.ember),
                    )
                }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

package sg.com.tertiarycourses.lahlahvoice.core

/** What the speaker said, what they meant, and whether the model helped. */
data class DictationResult(val raw: String, val text: String, val usedModel: Boolean)

/** raw transcript → rules → Gemini Nano → your dictionary */
object DictationPipeline {

    suspend fun process(raw: String, store: Store): DictationResult {
        val settings = store.settings.value
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return DictationResult(raw, "", false)

        val ruled = TranscriptCleaner.clean(trimmed, settings.languageTag, settings.style)
        if (ruled.isEmpty()) return DictationResult(raw, "", false)

        var text = ruled
        var usedModel = false
        if (settings.useModel && (settings.style != WritingStyle.VERBATIM || ruled != trimmed)) {
            val terms = store.dictionary.value.map { it.term }
            val context = personalContext(store.notes.value, settings.writingSample, settings.style)
            GeminiEditor.edit(ruled, settings.style, terms, context)?.let {
                text = it
                usedModel = true
            }
        }
        text = applyDictionary(text, store.dictionary.value)
        return DictationResult(raw, text, usedModel)
    }

    /** About You: notes and a writing sample, used only to understand and match the speaker. */
    fun personalContext(notes: List<PersonalNote>, writingSample: String, style: WritingStyle): String {
        val parts = mutableListOf<String>()
        val texts = notes.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (texts.isNotEmpty()) {
            parts += "About the speaker. Use this only to understand and spell what they said. Never add any of it to the text:\n" +
                texts.take(12).joinToString("\n") { "- $it" }
        }
        val sample = writingSample.trim()
        if (style != WritingStyle.VERBATIM && sample.split(Regex("\\s+")).size >= MIN_SAMPLE_WORDS) {
            parts += "How the speaker writes. Match its tone, punctuation and phrasing. Never copy its content:\n\"\"\"\n${sample.take(700)}\n\"\"\""
        }
        return parts.joinToString("\n\n")
    }

    const val MIN_SAMPLE_WORDS = 40

    fun applyDictionary(text: String, entries: List<DictionaryEntry>): String {
        var output = text
        for (entry in entries) {
            for (spoken in listOf(entry.term) + entry.soundsLike) {
                val form = spoken.trim()
                if (form.isEmpty()) continue
                output = Regex("\\b${Regex.escape(form)}\\b", RegexOption.IGNORE_CASE)
                    .replace(output) { entry.term }
            }
        }
        return output
    }
}

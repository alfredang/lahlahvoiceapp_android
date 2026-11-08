package sg.com.tertiarycourses.lahlahvoice.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Everything LahLah Voice keeps, stored on this phone only. Shared by the app and the keyboard,
 * which run in the same process.
 */
class Store private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lahlah", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _history = MutableStateFlow(loadList("history") { DictationRecord.fromJson(it) })
    val history: StateFlow<List<DictationRecord>> = _history.asStateFlow()

    private val _dictionary = MutableStateFlow(loadList("dictionary") { DictionaryEntry.fromJson(it) })
    val dictionary: StateFlow<List<DictionaryEntry>> = _dictionary.asStateFlow()

    private val _notes = MutableStateFlow(loadList("notes") { PersonalNote.fromJson(it) })
    val notes: StateFlow<List<PersonalNote>> = _notes.asStateFlow()

    fun updateSettings(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString("style", next.style.name)
            .putBoolean("useModel", next.useModel)
            .putBoolean("keepHistory", next.keepHistory)
            .putString("languageTag", next.languageTag)
            .putString("writingSample", next.writingSample)
            .apply()
        if (!next.keepHistory) setHistory(emptyList())
    }

    fun record(entry: DictationRecord) {
        if (!_settings.value.keepHistory) return
        setHistory((listOf(entry) + _history.value).take(500))
    }

    fun deleteRecord(id: String) = setHistory(_history.value.filterNot { it.id == id })
    fun clearHistory() = setHistory(emptyList())

    fun setDictionary(entries: List<DictionaryEntry>) {
        _dictionary.value = entries
        saveList("dictionary", entries.map { it.toJson() })
    }

    fun setNotes(notes: List<PersonalNote>) {
        _notes.value = notes
        saveList("notes", notes.map { it.toJson() })
    }

    private fun setHistory(records: List<DictationRecord>) {
        _history.value = records
        saveList("history", records.map { it.toJson() })
    }

    private fun loadSettings() = Settings(
        style = runCatching { WritingStyle.valueOf(prefs.getString("style", null)!!) }.getOrDefault(WritingStyle.CASUAL),
        useModel = prefs.getBoolean("useModel", true),
        keepHistory = prefs.getBoolean("keepHistory", true),
        languageTag = prefs.getString("languageTag", null) ?: "en-US",
        writingSample = prefs.getString("writingSample", null) ?: "",
    )

    private fun <T> loadList(key: String, parse: (org.json.JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { parse(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun saveList(key: String, items: List<org.json.JSONObject>) {
        prefs.edit().putString(key, JSONArray(items).toString()).apply()
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store =
            instance ?: synchronized(this) { instance ?: Store(context).also { instance = it } }
    }
}

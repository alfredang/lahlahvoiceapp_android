package sg.com.tertiarycourses.lahlahvoice.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** How finished text should read. Same styles as LahLah Voice for Mac and iPhone. */
enum class WritingStyle(val title: String, val summary: String) {
    CLEAR("Clear and concise", "Turns rambling into clear sentences that say what you meant, keeping every point. Good for AI chats."),
    CASUAL("Casual", "Natural and friendly, like a message to a colleague."),
    POLISHED("Polished", "Complete, professional sentences. Good for email."),
    VERY_CASUAL("Very casual", "Lowercase and relaxed, like texting a friend."),
    VERBATIM("As spoken", "Keeps your own words. Only removes fillers and fixes punctuation."),
}

/** A word you want spelled your way, plus what the recogniser hears instead. */
data class DictionaryEntry(val term: String, val soundsLike: List<String> = emptyList()) {
    fun toJson() = JSONObject().put("term", term).put("soundsLike", JSONArray(soundsLike))

    companion object {
        fun fromJson(o: JSONObject) = DictionaryEntry(
            o.getString("term"),
            o.optJSONArray("soundsLike")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList(),
        )
    }
}

/** Something every dictation should know, such as "I teach group theory". */
data class PersonalNote(val id: String = UUID.randomUUID().toString(), val text: String) {
    fun toJson() = JSONObject().put("id", id).put("text", text)

    companion object {
        fun fromJson(o: JSONObject) = PersonalNote(o.optString("id", UUID.randomUUID().toString()), o.getString("text"))
    }
}

data class DictationRecord(
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val raw: String,
    val text: String,
    val usedModel: Boolean,
) {
    fun toJson() = JSONObject().put("id", id).put("date", date).put("raw", raw).put("text", text)
        .put("usedModel", usedModel)

    companion object {
        fun fromJson(o: JSONObject) = DictationRecord(
            o.getString("id"), o.getLong("date"), o.getString("raw"), o.getString("text"),
            o.optBoolean("usedModel"),
        )
    }
}

data class Settings(
    val style: WritingStyle = WritingStyle.CASUAL,
    /** Use Gemini Nano to understand what you meant, when this phone has it. */
    val useModel: Boolean = true,
    val keepHistory: Boolean = true,
    val languageTag: String = "en-US",
    val writingSample: String = "",
)

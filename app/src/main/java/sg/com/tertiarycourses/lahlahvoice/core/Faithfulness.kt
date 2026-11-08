package sg.com.tertiarycourses.lahlahvoice.core

/**
 * Rejects model output that drifted from the dictation, such as answering a question or
 * inventing a number. Better to keep plain text than to paste the wrong text. Same rules as the
 * Mac and iPhone apps.
 */
object Faithfulness {
    fun isFaithful(output: String, input: String, style: WritingStyle): Boolean {
        if (output.isEmpty()) return false
        if (output.length > input.length + maxOf(40, input.length / 3)) return false

        val outputWords = contentWords(output)
        val inputWords = contentWords(input)
        if (outputWords.isEmpty() || inputWords.isEmpty()) return false

        // Every number that survives must have been spoken.
        if (!(numbers(output) - listMarkers(output)).all { it in numbers(input) }) return false
        if (input.contains("\n") && !output.contains("\n")) return false
        if (repeatsItself(outputWords, inputWords)) return false

        val inputSet = inputWords.toSet()
        val overlap = outputWords.count { it in inputSet }
        if (overlap.toDouble() / outputWords.size < 0.6) return false

        // The model must not drop most of what was said: this catches it obeying dictated text.
        val outputSet = outputWords.toSet()
        val kept = inputWords.count { it in outputSet }
        return kept.toDouble() / inputWords.size >= if (style == WritingStyle.CLEAR) 0.3 else 0.5
    }

    /** The whole text returned twice only stands if the speaker said most of it twice. */
    private fun repeatsItself(words: List<String>, input: List<String>): Boolean {
        if (words.size < 12 || words.size % 2 != 0) return false
        val half = words.size / 2
        if (words.subList(0, half) != words.subList(half, words.size)) return false
        val counts = input.groupingBy { it }.eachCount().toMutableMap()
        val saidTwice = words.subList(0, half).count { word ->
            val count = counts[word] ?: 0
            if (count >= 2) { counts[word] = count - 2; true } else false
        }
        return saidTwice * 2 <= half
    }

    private val cardinals = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10", "eleven" to "11",
        "twelve" to "12",
    )
    private val numberWords = cardinals + mapOf(
        "square" to "2", "squared" to "2", "cubed" to "3", "first" to "1", "second" to "2", "third" to "3",
    )

    private fun contentWords(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            .map { cardinals[it] ?: it }

    private fun numbers(text: String): Set<String> {
        val digits = Regex("[0-9]+").findAll(text).map { it.value }
        val spoken = text.lowercase().split(Regex("[^\\p{L}]+")).mapNotNull { numberWords[it] }
        return (digits + spoken).toSet()
    }

    private fun listMarkers(text: String): Set<String> =
        text.split("\n").mapNotNull { Regex("^([0-9]+)\\.").find(it)?.groupValues?.get(1) }.toSet()
}

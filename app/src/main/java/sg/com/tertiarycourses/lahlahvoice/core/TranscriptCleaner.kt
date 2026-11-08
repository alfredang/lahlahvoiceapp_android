package sg.com.tertiarycourses.lahlahvoice.core

/**
 * The rule-based editing layer, ported from LahLah Voice for Mac and iPhone. It always runs,
 * needs no model, and gives Gemini Nano a cleaner starting point.
 *
 * Rules favour keeping a word over deleting one the speaker meant: "I like tea" keeps "like",
 * and only filler uses such as ", like," or "so like" are removed.
 */
object TranscriptCleaner {

    fun clean(raw: String, languageTag: String = "en", style: WritingStyle = WritingStyle.CASUAL): String {
        var text = raw.trim()
        if (text.isEmpty()) return ""
        val english = languageTag.lowercase().startsWith("en")

        if (english) {
            text = applyDeletions(text)
            text = removeEnglishFillers(text)
            text = applyBacktracks(text)
            text = removeRepeats(text)
            text = applySpokenPunctuation(text)
            text = formatNumberedList(text)
        } else {
            text = removeRepeats(text)
        }
        text = tidy(text)
        if (english) {
            text = fixCapitalisation(text)
            text = addMissingEndPunctuation(text)
        }
        return applyDeterministicStyle(text, style)
    }

    // ---- Regex helper: case-insensitive, ^ and $ match at line breaks, like the Swift version.

    private fun String.re(pattern: String, replacement: String, ignoreCase: Boolean = true): String {
        val options = buildSet {
            add(RegexOption.MULTILINE)
            if (ignoreCase) add(RegexOption.IGNORE_CASE)
        }
        return Regex(pattern, options).replace(this, replacement)
    }

    private fun String.trimPunctuation(): String =
        trim { !it.isLetterOrDigit() }

    private fun normalise(word: String) = word.lowercase().trimPunctuation()

    // ---- Fillers

    private const val HARD_FILLERS = """\b(?:u+h+m*|u+m+|e+r+m+|e+r+|a+h+|h+m+|m+h*m+|e+h+)\b"""

    private fun removeEnglishFillers(input: String): String {
        var text = input
        text = text.re(HARD_FILLERS + """[,.…]*""", "")
        val parenthetical = """(?:like|you know|i mean|you see|sort of|kind of|basically|literally|honestly)"""
        text = text.re(""",\s*$parenthetical\s*,""", ",")
        text = text.re("""(^|[.!?]\s+)(?:so\s+)?(?:like|you know|basically|well)\s*,\s*""", "$1")
        text = text.re("""\b(so|and|but|because)\s+like\b,?""", "$1")
        text = text.re(""",\s*you know(?=[.!?]|$)""", "")
        text = text.re("""\b(?:you know\s+like|like\s+you know)\b,?""", "")
        text = text.re("""\blike(?=\s+(?:around|about|maybe|roughly|almost|nearly)\b)\s*""", "")
        text = text.re("""^(?:so|okay so|ok so|right so),\s+""", "")
        return text
    }

    // ---- Corrections

    /** "scratch that" deletes the sentence (or clause) spoken just before it. */
    private fun applyDeletions(input: String): String {
        val marker = Regex("""\b(?:scratch that|delete that|forget that|never mind that|strike that)\b[,.]?""", RegexOption.IGNORE_CASE)
        var text = input
        var guard = 0
        while (guard++ < 8) {
            val match = marker.find(text) ?: break
            val before = text.substring(0, match.range.first).trim(' ', ',', '.')
            val after = text.substring(match.range.last + 1).trim()
            val boundary = Regex("""[.!?]\s""").findAll(before).lastOrNull()
            val kept = if (boundary != null) before.substring(0, boundary.range.last + 1) else ""
            text = (kept + after).trim()
        }
        return text
    }

    private val backtrackMarker = Regex(
        """(?:,|\.|\s)\s*(?:no,?\s+wait|wait,?\s+no|actually,?\s+no|no,?\s+actually|no,?\s+sorry|sorry,?\s+no|oh,?\s+no,?\s+wait|sorry,?\s+i\s+mean|or\s+rather|i\s+meant|i\s+mean|make\s+that|correction|let\s+me\s+rephrase|rather|wait|actually(?=,?\s+(?:let'?s|let\s+us|make|i'?ll|we'?ll|it'?s|at|on|in)\b))[,.:?!]?\s+""",
        RegexOption.IGNORE_CASE,
    )

    /** "X no wait Y": Y replaces the part of X it corrects. */
    private fun applyBacktracks(input: String): String {
        var text = input
        var guard = 0
        while (guard++ < 8) {
            val match = backtrackMarker.find(text) ?: break
            val before = text.substring(0, match.range.first)
            val after = text.substring(match.range.last + 1)
            val end = Regex("""[.!?](\s|$)""").find(after)?.range?.first ?: after.length
            val correction = after.substring(0, end)
            val remainder = after.substring(end)
            if (correction.isBlank() || before.isBlank()) break
            val sentenceStart = Regex("""[.!?]\s""").findAll(before).lastOrNull()?.let { it.range.last + 1 } ?: 0
            val prior = before.substring(0, sentenceStart)
            val clause = before.substring(sentenceStart)
            text = prior + merge(clause, correction) + remainder
        }
        return text
    }

    private val connectiveWords = setOf("the", "a", "an", "and", "to", "of")
    private val prepositions = setOf(
        "at", "on", "in", "by", "to", "from", "for", "with", "before", "after", "until", "the", "a", "an",
    )

    private fun merge(clause: String, correction: String): String {
        val clauseWords = clause.split(" ").filter { it.isNotEmpty() }
        val correctionWords = correction.split(" ").filter { it.isNotEmpty() }
        val first = correctionWords.firstOrNull() ?: return clause

        // The correction restarts from a word already said: cut back to that word.
        val anchor = clauseWords.indexOfLast { normalise(it) == normalise(first) }
        if (anchor >= 0 && (normalise(first) !in connectiveWords || correctionWords.size > 1)) {
            return (clauseWords.take(anchor) + correctionWords).joinToString(" ")
        }
        // Otherwise replace the same number of trailing words, at most two.
        var replaced = minOf(correctionWords.size, 2, clauseWords.size)
        if (replaced == 2 && normalise(clauseWords[clauseWords.size - 2]) in prepositions &&
            normalise(first) !in prepositions
        ) {
            replaced = 1
        }
        return (clauseWords.dropLast(replaced) + correctionWords).joinToString(" ")
    }

    private val allowedDoubles = setOf("had", "that", "very", "so", "really", "bye", "no", "yes", "ha")

    /** "I I think" → "I think", "we should we should go" → "we should go". */
    private fun removeRepeats(input: String): String {
        val words = input.split(" ").filter { it.isNotEmpty() }.toMutableList()
        if (words.size < 2) return input
        var changed = true
        while (changed) {
            changed = false
            var length = minOf(5, words.size / 2)
            while (length >= 1) {
                var start = 0
                while (start + 2 * length <= words.size) {
                    val first = words.subList(start, start + length).map(::normalise)
                    val second = words.subList(start + length, start + 2 * length).map(::normalise)
                    val endsSentence = words[start + length - 1].lastOrNull()?.let { it in ".!?" } ?: false
                    if (first == second && "" !in first && !endsSentence &&
                        !(length == 1 && first[0] in allowedDoubles)
                    ) {
                        repeat(length) { words.removeAt(start) }
                        changed = true
                    } else {
                        start++
                    }
                }
                length--
            }
        }
        return words.joinToString(" ")
    }

    // ---- Spoken formatting

    private fun applySpokenPunctuation(input: String): String {
        var text = input
        text = text.re("""[,.]?\s*\bnew paragraph\b[,.]?\s*""", "\n\n")
        text = text.re("""[,.]?\s*\bnew line\b[,.]?\s*""", "\n")
        text = text.re("""[,.]?\s*\bquestion mark\b""", "?")
        text = text.re("""[,.]?\s*\bexclamation (?:mark|point)\b""", "!")
        text = text.re("""[,.]?\s*\bfull stop\b""", ".")
        text = text.re("""\s+\bcomma\b(?=\s+\w)""", ",")
        // "period" is also an ordinary noun, so it only counts at the very end.
        text = Regex("""\s+\bperiod\b[.]?\s*$""", RegexOption.IGNORE_CASE).replace(text, ".")
        return text
    }

    private val ordinals = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth")

    /** "first buy milk second call mum third pay rent" becomes a numbered list. */
    private fun formatNumberedList(input: String): String {
        if (input.contains("\n")) return input
        val positions = mutableListOf<IntRange>()
        var searchStart = 0
        for (ordinal in ordinals) {
            val pattern = Regex("""(?:^|(?<=[\s,.:;]))$ordinal(?:ly)?\b(?:\s+of\s+all)?[,:]?\s+""", RegexOption.IGNORE_CASE)
            val found = pattern.find(input, searchStart) ?: break
            positions.add(found.range)
            searchStart = found.range.last + 1
        }
        if (positions.size < 2) return input

        val lines = mutableListOf<String>()
        val intro = input.substring(0, positions[0].first).trim(' ', ',', '.', ';', ':')
        if (intro.isNotEmpty()) lines.add("$intro:")
        positions.forEachIndexed { index, position ->
            val end = if (index + 1 < positions.size) positions[index + 1].first else input.length
            var item = input.substring(position.last + 1, end).trim(' ', ',', ';', ':', '\n')
            if (item.endsWith(".")) item = item.dropLast(1)
            if (index + 1 < positions.size) {
                item = Regex("""[,]?\s+(?:and|then|and then)$""", RegexOption.IGNORE_CASE).replace(item, "")
            }
            if (item.isEmpty()) return input
            lines.add("${index + 1}. ${item.first().uppercase()}${item.drop(1)}")
        }
        return lines.joinToString("\n")
    }

    // ---- Tidying

    private fun tidy(input: String): String {
        var text = input
        text = text.re("""[ \t]+""", " ")
        text = text.re("""\s+([,.!?;:])""", "$1")
        text = text.re(""",(?:\s*,)+""", ",")
        text = text.re("""([.!?])\s*[,.]+""", "$1")
        text = text.re(""",\s*([.!?])""", "$1")
        text = Regex("""^[\s,.;:]+""").replace(text, "")
        text = text.re("""[ \t]*\n[ \t]*""", "\n")
        return text.trim()
    }

    private val properNouns = listOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "april", "june", "july", "august", "september", "october",
        "november", "december",
    )

    private fun fixCapitalisation(input: String): String {
        var text = Regex("""\bi\b(?=(?:'[a-z]+)?(?:\s|[,.!?]|$))""").replace(input, "I")
        val result = StringBuilder()
        var capitaliseNext = true
        for (character in text) {
            if (capitaliseNext && character.isLetter()) {
                result.append(character.uppercase())
                capitaliseNext = false
                continue
            }
            result.append(character)
            if (character in ".!?\n") capitaliseNext = true
        }
        text = result.toString()
        for (name in properNouns) {
            text = text.re("""\b$name\b""", name.replaceFirstChar { it.uppercase() })
        }
        return text
    }

    private val questionOpeners = setOf(
        "who", "what", "when", "where", "why", "how", "which", "can", "could", "would", "should",
        "do", "does", "did", "is", "are", "was", "were", "will", "shall", "have", "has", "may",
        "am", "isn't", "aren't", "don't", "doesn't", "won't", "can't",
    )

    private fun addMissingEndPunctuation(input: String): String {
        val last = input.lastOrNull() ?: return input
        if (!last.isLetterOrDigit()) return input
        if (input.contains("\n1. ") || input.startsWith("1. ")) return input
        val lastLine = input.split("\n").last()
        val boundary = Regex("""[.!?]\s""").findAll(lastLine).lastOrNull()
        val lastSentence = if (boundary != null) lastLine.substring(boundary.range.last + 1) else lastLine
        val words = lastSentence.split(" ").map(::normalise)
        val opener = words.firstOrNull { it !in setOf("so", "and", "but", "okay", "ok", "well", "hey") }
        return input + if (opener in questionOpeners) "?" else "."
    }

    private fun applyDeterministicStyle(input: String, style: WritingStyle): String {
        if (style != WritingStyle.VERY_CASUAL || input.contains("\n")) return input
        val result = StringBuilder()
        var atStart = true
        for (character in input) {
            if (atStart && character.isLetter()) {
                result.append(if (character == 'I') "I" else character.lowercase())
                atStart = false
            } else {
                result.append(character)
                if (character in ".!?") atStart = true
            }
        }
        return result.toString().removeSuffix(".")
    }
}

package sg.com.tertiarycourses.lahlahvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sg.com.tertiarycourses.lahlahvoice.core.DictationPipeline
import sg.com.tertiarycourses.lahlahvoice.core.DictionaryEntry
import sg.com.tertiarycourses.lahlahvoice.core.Faithfulness
import sg.com.tertiarycourses.lahlahvoice.core.TranscriptCleaner
import sg.com.tertiarycourses.lahlahvoice.core.WritingStyle

class CleanerTest {
    private fun clean(text: String) = TranscriptCleaner.clean(text)

    @Test fun fillersAndStutters() =
        assertEquals("So can you send me the report by Friday?", clean("um so can you uh can you send me the the report by friday"))

    @Test fun spokenCorrections() {
        assertEquals("The meeting is on Wednesday at three.", clean("the meeting is on tuesday no wait wednesday at three"))
        assertEquals("Let's get sushi for dinner tonight.", clean("let's get pizza actually no let's get sushi for dinner tonight"))
    }

    @Test fun scratchThat() = assertEquals("Call me later.", clean("I hate this. scratch that. call me later"))

    @Test fun keepsMeaningfulLike() = assertEquals("I like tea.", clean("I like tea"))

    @Test fun numberedList() = assertEquals(
        "We need three things:\n1. The budget\n2. The timeline\n3. The team",
        clean("we need three things first the budget second the timeline third the team"),
    )

    @Test fun spokenPunctuation() = assertEquals("Are you coming?", clean("are you coming question mark"))

    @Test fun veryCasual() = assertEquals(
        "see you tomorrow",
        TranscriptCleaner.clean("see you tomorrow", style = WritingStyle.VERY_CASUAL),
    )

    @Test fun dictionaryFixesSpelling() = assertEquals(
        "Ask Priya about LahLah Voice.",
        DictationPipeline.applyDictionary("Ask priya about lala voice.", listOf(DictionaryEntry("Priya"), DictionaryEntry("LahLah Voice", listOf("lala voice")))),
    )

    @Test fun faithfulnessRejectsAnswersAndInventedNumbers() {
        val said = "what is the capital of france"
        assertTrue(Faithfulness.isFaithful("What is the capital of France?", said, WritingStyle.CASUAL))
        assertFalse(Faithfulness.isFaithful("Paris.", said, WritingStyle.CASUAL))
        assertFalse(Faithfulness.isFaithful("It's 1 + 2 + 3, which gives 7.", "it's one plus two plus three which gives six", WritingStyle.CASUAL))
        assertTrue(Faithfulness.isFaithful("It's 1 + 2 + 3, which gives 6.", "it's one plus two plus three which gives six", WritingStyle.CASUAL))
    }

    @Test fun personalContextOnlyWhenThereIsSomething() {
        assertEquals("", DictationPipeline.personalContext(emptyList(), "too short", WritingStyle.CASUAL))
    }
}

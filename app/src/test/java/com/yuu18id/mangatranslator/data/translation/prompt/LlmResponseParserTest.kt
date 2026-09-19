package com.yuu18id.mangatranslator.data.translation.prompt

import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmResponseParserTest {

    @Test
    fun parse_exactUserLogcatSingleLineGguf_parsesAllFiveBlocks() {
        val rawResponse = "1: [Four years after meeting Margaret, we decided to marry — I rejected all the other suitors from my other bloodline who were all too proud to accept a commoner like me.] 2: [I fell for her at first sight for her dignified bearing. She was the one who chose me over all the other men, and that was what made me happiest.] 3: [Her parents strongly opposed marrying a commoner like me, but after much back and forth, we still managed to hold the wedding next month.] 4: [But when she went out into town, she happened to be spotted by the scruffy little prince who'd been looking for wives all along.] 5: [She became his 13th wife in a flash.]"

        val result = LlmResponseParser.parse(rawResponse, 5)

        assertEquals(5, result.size)
        assertTrue(result[0]!!.startsWith("Four years after meeting Margaret"))
        assertTrue(result[0]!!.endsWith("accept a commoner like me."))
        assertTrue(result[1]!!.startsWith("I fell for her at first sight"))
        assertTrue(result[2]!!.startsWith("Her parents strongly opposed"))
        assertTrue(result[3]!!.startsWith("But when she went out into town"))
        assertEquals("She became his 13th wife in a flash.", result[4])
    }

    @Test
    fun parse_singleLineDotFormat_parsesCorrectly() {
        val rawResponse = "1. [Four years after meeting Margaret, I overcame the status gap.] 2. [I had a quick crush on her elegant demeanor.] 3. [Her parents strongly opposed marrying a commoner.] 4. [She went out into town.] 5. [She suddenly became his thirteenth wife.]"

        val result = LlmResponseParser.parse(rawResponse, 5)

        assertEquals(5, result.size)
        assertEquals("Four years after meeting Margaret, I overcame the status gap.", result[0])
        assertEquals("I had a quick crush on her elegant demeanor.", result[1])
        assertEquals("Her parents strongly opposed marrying a commoner.", result[2])
        assertEquals("She went out into town.", result[3])
        assertEquals("She suddenly became his thirteenth wife.", result[4])
    }

    @Test
    fun parse_slashDelimitedWithDuplicateBrackets_cleansAndParses() {
        val rawResponse = "1: I'm going to get this child to sleep now, so... / [I'm going to get this child to sleep now, so...] / 2: Until it's done, please stay in that room with the guest. / [Until it's done, please stay in that room with the guest.] / 3: Yes... I'll just lie down beside you."

        val result = LlmResponseParser.parse(rawResponse, 3)

        assertEquals(3, result.size)
        assertEquals("I'm going to get this child to sleep now, so...", result[0])
        assertEquals("Until it's done, please stay in that room with the guest.", result[1])
        assertEquals("Yes... I'll just lie down beside you.", result[2])
    }

    @Test
    fun parse_unbracketedSingleLine_doesNotCramIntoFirstBalloon() {
        val rawResponse = "1: Hello world 2: How are you? 3: I am fine"

        val result = LlmResponseParser.parse(rawResponse, 3)

        assertEquals(3, result.size)
        assertEquals("Hello world", result[0])
        assertEquals("How are you?", result[1])
        assertEquals("I am fine", result[2])
    }

    @Test
    fun parse_standardMultiLineCloudResponse_parsesCorrectly() {
        val rawResponse = """
            1: [It's been four years since I met Margaret.]
            2: [I fell in love at first sight.]
            3: [Her parents strongly opposed.]
        """.trimIndent()

        val result = LlmResponseParser.parse(rawResponse, 3)

        assertEquals(3, result.size)
        assertEquals("It's been four years since I met Margaret.", result[0])
        assertEquals("I fell in love at first sight.", result[1])
        assertEquals("Her parents strongly opposed.", result[2])
    }

    @Test
    fun parse_withThinkingTags_removesReasoningAndParsesDialogue() {
        val rawResponse = "<think>The user wants English manga translation. Line 1 is Margaret, Line 2 is love.</think> 1: [Margaret is here.] 2: [I love her.]"

        val result = LlmResponseParser.parse(rawResponse, 2)

        assertEquals(2, result.size)
        assertEquals("Margaret is here.", result[0])
        assertEquals("I love her.", result[1])
    }

    @Test
    fun parse_withMarkdownCodeFence_stripsFenceAndParses() {
        val rawResponse = """
            ```markdown
            1: [First dialogue]
            2: [Second dialogue]
            ```
        """.trimIndent()

        val result = LlmResponseParser.parse(rawResponse, 2)

        assertEquals(2, result.size)
        assertEquals("First dialogue", result[0])
        assertEquals("Second dialogue", result[1])
    }

    @Test
    fun parse_withDatesAndNumbersInDialogue_doesNotBreakMarkerTracking() {
        val rawResponse = "1: [In 2024, exactly 10 days later, we met.] 2: [She became the 13th wife.] 3: [Cost was $500.]"

        val result = LlmResponseParser.parse(rawResponse, 3)

        assertEquals(3, result.size)
        assertEquals("In 2024, exactly 10 days later, we met.", result[0])
        assertEquals("She became the 13th wife.", result[1])
        assertEquals("Cost was $500.", result[2])
    }

    @Test
    fun parse_singleTargetBlockWithoutMarkers_returnsFullCleanedText() {
        val rawResponse = "[Here is just one translated dialogue.]"

        val result = LlmResponseParser.parse(rawResponse, 1)

        assertEquals(1, result.size)
        assertEquals("Here is just one translated dialogue.", result[0])
    }

    @Test
    fun applyToBlocks_populatesTranslatedTextCorrectly() {
        val blocks = listOf(
            TextBlock(lines = emptyList(), text = "Jap 1", boundingBox = android.graphics.RectF(), language = Language.JPN),
            TextBlock(lines = emptyList(), text = "Jap 2", boundingBox = android.graphics.RectF(), language = Language.JPN),
            TextBlock(lines = emptyList(), text = "Jap 3", boundingBox = android.graphics.RectF(), language = Language.JPN)
        )

        val rawResponse = "1: [Eng 1] 2: [Eng 2] 3: [Eng 3]"
        val updated = LlmResponseParser.applyToBlocks(rawResponse, blocks)

        assertEquals(3, updated.size)
        assertEquals("Eng 1", updated[0].translatedText)
        assertEquals("Eng 2", updated[1].translatedText)
        assertEquals("Eng 3", updated[2].translatedText)
        assertEquals("Jap 1", updated[0].text) // original intact
    }

    @Test
    fun parse_mangaSpecialTagFormat_parsesAllLines() {
        val rawResponse = """
            <|1|>Four years after we met,
            <|2|>we decided to get married.
            <|3|>Her parents opposed it, but...
        """.trimIndent()

        val result = LlmResponseParser.parse(rawResponse, 3)

        assertEquals(3, result.size)
        assertEquals("Four years after we met,", result[0])
        assertEquals("we decided to get married.", result[1])
        assertEquals("Her parents opposed it, but...", result[2])
    }

    @Test
    fun parse_withPromptLeakageEchoing_discardsLeakedSlice() {
        val rawResponse = "1: [Margaret is here] 2: [I love her] 3: [>. NEVER MERGE ADJACENT BUBBLES TOGETHER, EVEN IF THEY FORM ONE CONTINUOUS SENTENCE. EVERY BUBBLE MUST BE TRANSLATED INTO NATURAL ENGLISH. DO NOT]"

        val result = LlmResponseParser.parse(rawResponse, 3)

        assertEquals(2, result.size)
        assertEquals("Margaret is here", result[0])
        assertEquals("I love her", result[1])
        assertEquals(null, result[2]) // Leaked slice is dropped!
    }

    @Test
    fun isPromptLeakage_detectsKnownKeywords() {
        assertTrue(LlmResponseParser.isPromptLeakage("NEVER MERGE adjacent bubbles together"))
        assertTrue(LlmResponseParser.isPromptLeakage("Output exactly 5 speech bubbles"))
        assertTrue(LlmResponseParser.isPromptLeakage("CRITICAL OUTPUT FORMAT: [Translated text]"))
        assertFalse(LlmResponseParser.isPromptLeakage("I will never give up on my dreams!"))
        assertFalse(LlmResponseParser.isPromptLeakage("Good morning, Senpai!"))
    }
}

package com.yuu18id.mangatranslator.data.ml.ocr

import android.graphics.PointF
import android.graphics.RectF
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import com.yuu18id.mangatranslator.domain.model.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaOcrEngineTest {

    @Test
    fun testMergeOverlappingLinesWithDirectOverlap() {
        val lines = listOf(
            "そんなある日僕たちのクラス",
            "そんなある日僕たちのクラスに転校生がやってきた",
            "僕たちのクラスに転校生がやってきた....."
        )
        val merged = MangaOcrEngine.mergeOverlappingLines(lines, isVertical = true)
        assertEquals("そんなある日僕たちのクラスに転校生がやってきた.....", merged)
    }

    @Test
    fun testMergeOverlappingLinesWithSuffixPrefixOverlap() {
        val lines = listOf(
            "案内二人きりになっちゃうけど...",
            "なっちゃうけど...リサちゃん大丈夫?"
        )
        val merged = MangaOcrEngine.mergeOverlappingLines(lines, isVertical = true)
        assertEquals("案内二人きりになっちゃうけど...リサちゃん大丈夫?", merged)
    }

    @Test
    fun testMergeOverlappingLinesDisjoint() {
        val lines = listOf(
            "彼女の名前はリサ",
            "僕の幼馴染で昔から"
        )
        val merged = MangaOcrEngine.mergeOverlappingLines(lines, isVertical = true)
        assertEquals("彼女の名前はリサ僕の幼馴染で昔から", merged)
    }

    @Test
    fun testMergeOverlappingLinesHorizontalWithSpace() {
        val lines = listOf(
            "Hello",
            "world"
        )
        val merged = MangaOcrEngine.mergeOverlappingLines(lines, isVertical = false)
        assertEquals("Hello world", merged)
    }

    @Test
    fun testIsDenseMultiLineBlock() {
        fun makeLine(x: Float, y: Float, w: Float, h: Float): Quadrilateral {
            val pts = listOf(
                PointF(x, y),
                PointF(x + w, y),
                PointF(x + w, y + h),
                PointF(x, y + h)
            )
            return Quadrilateral(pts = pts, isVertical = true)
        }

        val dummyBox = RectF(0f, 0f, 100f, 100f)

        // 1 line -> false
        val block1 = TextBlock(lines = listOf(makeLine(0f, 0f, 25f, 150f)), text = "", boundingBox = dummyBox, isVertical = true)
        assertFalse(MangaOcrEngine.isDenseMultiLineBlock(block1))

        // 2 short lines -> false
        val block2 = TextBlock(lines = listOf(
            makeLine(0f, 0f, 25f, 150f),
            makeLine(30f, 0f, 25f, 150f)
        ), text = "", boundingBox = dummyBox, isVertical = true)
        assertFalse(MangaOcrEngine.isDenseMultiLineBlock(block2))

        // 2 tall narrative lines (>= 320px) -> true
        val tallBlock2 = TextBlock(lines = listOf(
            makeLine(0f, 0f, 25f, 380f),
            makeLine(30f, 0f, 25f, 390f)
        ), text = "", boundingBox = dummyBox, isVertical = true)
        assertTrue(MangaOcrEngine.isDenseMultiLineBlock(tallBlock2))

        // 3 short lines (speech bubble) -> false
        val speechBubble3 = TextBlock(lines = listOf(
            makeLine(0f, 0f, 25f, 180f),
            makeLine(30f, 0f, 25f, 190f),
            makeLine(60f, 0f, 25f, 200f)
        ), text = "", boundingBox = dummyBox, isVertical = true)
        assertFalse(MangaOcrEngine.isDenseMultiLineBlock(speechBubble3))

        // 3 long lines (dense narration box) -> true
        val narrationBox3 = TextBlock(lines = listOf(
            makeLine(0f, 0f, 25f, 320f),
            makeLine(30f, 0f, 25f, 400f),
            makeLine(60f, 0f, 25f, 300f)
        ), text = "", boundingBox = dummyBox, isVertical = true)
        assertTrue(MangaOcrEngine.isDenseMultiLineBlock(narrationBox3))

        // 4 lines (multi-column) -> true
        val block4 = TextBlock(lines = listOf(
            makeLine(0f, 0f, 25f, 150f),
            makeLine(30f, 0f, 25f, 150f),
            makeLine(60f, 0f, 25f, 150f),
            makeLine(90f, 0f, 25f, 150f)
        ), text = "", boundingBox = dummyBox, isVertical = true)
        assertTrue(MangaOcrEngine.isDenseMultiLineBlock(block4))
    }

    @Test
    fun testHasRepetitiveTokenLoopSingleTokenContentThreshold() {
        val existing = listOf(50L, 50L, 50L, 50L) // 4 tokens
        assertFalse(
            "4 identical content tokens should not trigger loop breaker yet",
            MangaOcrEngine.hasRepetitiveTokenLoop(existing, 50L, isPunctuation = false)
        )

        val existing5 = listOf(50L, 50L, 50L, 50L, 50L) // 5 tokens
        assertTrue(
            "5 identical content tokens must trigger loop breaker",
            MangaOcrEngine.hasRepetitiveTokenLoop(existing5, 50L, isPunctuation = false)
        )
    }

    @Test
    fun testHasRepetitiveTokenLoopSingleTokenPunctuationThreshold() {
        // Punctuation can legitimately repeat up to 12 times in manga (e.g. "……")
        val punctTokens = List(6) { 10L }
        assertFalse(
            "6 consecutive ellipsis dots should be permitted in manga",
            MangaOcrEngine.hasRepetitiveTokenLoop(punctTokens, 10L, isPunctuation = true)
        )

        val punctTokens12 = List(12) { 10L }
        assertTrue(
            "12 consecutive ellipsis dots must trigger early loop break",
            MangaOcrEngine.hasRepetitiveTokenLoop(punctTokens12, 10L, isPunctuation = true)
        )
    }

    @Test
    fun testHasRepetitiveTokenLoop2TokenCycle() {
        // Alternating pattern: [100, 200, 100, 200, 100] + 200 => [100, 200, 100, 200, 100, 200]
        val cycling = listOf(100L, 200L, 100L, 200L, 100L)
        assertTrue(
            "2-token alternating cycle (3 full cycles = 6 tokens) must trigger early loop break",
            MangaOcrEngine.hasRepetitiveTokenLoop(cycling, 200L, isPunctuation = false)
        )

        assertFalse(
            "Breaking the cycle with a new token must not trigger loop breaker",
            MangaOcrEngine.hasRepetitiveTokenLoop(cycling, 300L, isPunctuation = false)
        )
    }

    @Test
    fun testHasRepetitiveTokenLoop3TokenCycle() {
        // 3-token pattern: [1, 2, 3, 1, 2, 3, 1, 2] + 3 => [1, 2, 3, 1, 2, 3, 1, 2, 3]
        val cycling3 = listOf(1L, 2L, 3L, 1L, 2L, 3L, 1L, 2L)
        assertTrue(
            "3-token repeating cycle (3 full cycles = 9 tokens) must trigger early loop break",
            MangaOcrEngine.hasRepetitiveTokenLoop(cycling3, 3L, isPunctuation = false)
        )

        assertFalse(
            "Different next token must not trigger 3-token cycle break",
            MangaOcrEngine.hasRepetitiveTokenLoop(cycling3, 99L, isPunctuation = false)
        )
    }
}

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
}

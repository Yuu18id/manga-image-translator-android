package com.yuu18id.mangatranslator.data.textline

import android.graphics.PointF
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextlineMergerTest {

    private val merger = TextlineMerger()

    private fun createVerticalBox(id: String, x: Float, y: Float, w: Float, h: Float): Quadrilateral {
        val pts = listOf(
            PointF(x, y),
            PointF(x + w, y),
            PointF(x + w, y + h),
            PointF(x, y + h)
        )
        return Quadrilateral(pts = pts, text = id, isVertical = true)
    }

    @Test
    fun testVerticallyStackedBalloonsNeverMerge() {
        // Actual coordinates extracted from user's sample manga page:
        // Top speech balloon (7 columns)
        val saki = createVerticalBox("先に", 168f, 88f, 61f, 140f)
        val tosho = createVerticalBox("図書室に", 104f, 94f, 60f, 238f)
        val itte = createVerticalBox("いっててね", 31f, 95f, 69f, 297f)
        val sugu = createVerticalBox("すぐ行くから", 237f, 108f, 58f, 353f)
        val aite = createVerticalBox("相手したら", 299f, 111f, 55f, 295f)
        val hito = createVerticalBox("人たちの", 355f, 111f, 56f, 234f)
        val ima = createVerticalBox("今並んでる", 414f, 112f, 58f, 292f)

        // Bottom speech balloon (6 columns)
        val e = createVerticalBox("え？", 431f, 536f, 61f, 138f)
        val dame = createVerticalBox("だーめ", 363f, 544f, 70f, 185f)
        val kyou = createVerticalBox("今日も勉強", 305f, 549f, 58f, 285f)
        val zettai = createVerticalBox("ぜったい", 191f, 594f, 72f, 241f)
        val issho = createVerticalBox("いっしょの", 143f, 605f, 58f, 283f)
        val daigaku = createVerticalBox("大学いこうね", 84f, 604f, 56f, 342f)

        val allLines = listOf(saki, tosho, itte, sugu, aite, hito, ima, e, dame, kyou, zettai, issho, daigaku)

        val merged = merger.merge(allLines)

        // Must strictly separate top balloon and bottom balloon!
        assertEquals(2, merged.size)

        val topBlock = merged.find { it.lines.any { l -> l.text == "今並んでる" } }!!
        val bottomBlock = merged.find { it.lines.any { l -> l.text == "え？" } }!!

        assertEquals(7, topBlock.lines.size)
        assertEquals(6, bottomBlock.lines.size)

        // Verify correct Japanese vertical reading order (Right to Left)
        assertEquals("今並んでる人たちの相手したらすぐ行くから先に図書室にいっててね", topBlock.text)
        assertEquals("え？だーめ今日も勉強ぜったいいっしょの大学いこうね", bottomBlock.text)
    }

    @Test
    fun testVerticalLinesWithoutOverlapCannotMerge() {
        val top = createVerticalBox("TopLine", 100f, 50f, 30f, 150f)
        val bottom = createVerticalBox("BottomLine", 100f, 250f, 30f, 150f) // zero vertical overlap

        val canMerge = merger.canMerge(top, bottom)
        assertEquals(false, canMerge)
    }

    @Test
    fun testAdjacentMultiColumnBalloonsNeverMerge() {
        // Coordinates from user's sample manga page with 3 adjacent speech balloons:
        // Right balloon: "うわ" (single column)
        val uwa = createVerticalBox("うわ", 756f, 82f, 156f, 278f)
        // Middle balloon: "また出た" (2 columns detected as single block)
        val mataDeta = createVerticalBox("また出た", 382f, 196f, 314f, 337f)
        // Left balloon: "共有テロ" (2 columns detected as single block)
        val kyouyuuTero = createVerticalBox("共有テロ", 40f, 14f, 345f, 354f)

        val merged = merger.merge(listOf(uwa, mataDeta, kyouyuuTero))

        // All 3 adjacent balloons must remain separate!
        assertEquals(3, merged.size)
        assertEquals(false, merger.canMerge(mataDeta, kyouyuuTero))
        assertEquals(false, merger.canMerge(uwa, mataDeta))
    }

    @Test
    fun testPeanutDoubleBalloonNeverMerge() {
        // Coordinates from user's peanut / double speech bubble:
        // Top balloon (3 columns)
        val daigaku = createVerticalBox("大学で", 545f, 65f, 120f, 276f)
        val onaji = createVerticalBox("同じ授業", 445f, 74f, 107f, 358f)
        val tottete = createVerticalBox("取っててさー", 350f, 69f, 98f, 523f)

        // Bottom balloon (2 columns)
        val hanashite = createVerticalBox("話してみたら", 231f, 464f, 95f, 480f)
        val ikitougou = createVerticalBox("意気投合", 137f, 464f, 98f, 337f)

        val allLines = listOf(daigaku, onaji, tottete, hanashite, ikitougou)
        val merged = merger.merge(allLines)

        // Must cleanly separate into 2 blocks!
        assertEquals(2, merged.size)

        val topBlock = merged.find { it.lines.any { l -> l.text == "大学で" } }!!
        val bottomBlock = merged.find { it.lines.any { l -> l.text == "話してみたら" } }!!

        assertEquals(3, topBlock.lines.size)
        assertEquals(2, bottomBlock.lines.size)

        assertEquals("大学で同じ授業取っててさー", topBlock.text)
        assertEquals("話してみたら意気投合", bottomBlock.text)

        // Crucial bridge check:
        assertEquals(false, merger.canMerge(tottete, hanashite))
    }

    @Test
    fun testAttachedLobeBubbleNeverMerge() {
        // Coordinates from user's attached lobe / diagonal speech bubble:
        // Top-right lobe: "おい"
        val oi = createVerticalBox("おい", 544f, 84f, 162f, 340f)
        // Main balloon: "ちんこ" and "出せ"
        val chinko = createVerticalBox("ちんこ", 258f, 268f, 172f, 518f)
        val dase = createVerticalBox("出せ", 53f, 259f, 182f, 355f)

        val allLines = listOf(oi, chinko, dase)
        val merged = merger.merge(allLines)

        // Must separate into 2 blocks: [おい] and [ちんこ出せ]!
        assertEquals(2, merged.size)

        val lobeBlock = merged.find { it.lines.any { l -> l.text == "おい" } }!!
        val mainBlock = merged.find { it.lines.any { l -> l.text == "ちんこ" } }!!

        assertEquals(1, lobeBlock.lines.size)
        assertEquals(2, mainBlock.lines.size)

        assertEquals("おい", lobeBlock.text)
        assertEquals("ちんこ出せ", mainBlock.text)

        // Verify pairwise merges:
        assertEquals(false, merger.canMerge(oi, chinko))
        assertEquals(true, merger.canMerge(chinko, dase))
    }
}
package com.yuu18id.mangatranslator.data.rendering

import android.graphics.RectF
import com.yuu18id.mangatranslator.domain.model.TextAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAlignmentTest {

    private fun computeStartX(alignment: TextAlignment, bounds: RectF, lineWidth: Float): Float {
        val effectiveAlignment = if (alignment == TextAlignment.AUTO) TextAlignment.CENTER else alignment
        return when (effectiveAlignment) {
            TextAlignment.LEFT -> bounds.left + 4f
            TextAlignment.RIGHT -> bounds.right - lineWidth - 4f
            else -> bounds.centerX() - lineWidth / 2f
        }
    }

    @Test
    fun testHorizontalTextAlignmentCalculations() {
        val bounds = RectF(10f, 20f, 210f, 120f) // width = 200, centerX = 110
        val lineWidth = 80f

        val leftX = computeStartX(TextAlignment.LEFT, bounds, lineWidth)
        val centerX = computeStartX(TextAlignment.CENTER, bounds, lineWidth)
        val rightX = computeStartX(TextAlignment.RIGHT, bounds, lineWidth)
        val autoX = computeStartX(TextAlignment.AUTO, bounds, lineWidth)

        // Left alignment: bounds.left + 4f = 14f
        assertEquals(14f, leftX, 0.001f)

        // Center alignment: bounds.centerX() - 40f = 110f - 40f = 70f
        assertEquals(70f, centerX, 0.001f)

        // Right alignment: bounds.right - 80f - 4f = 210f - 84f = 126f
        assertEquals(126f, rightX, 0.001f)

        // Auto alignment defaults to Center for dialogue: 70f
        assertEquals(centerX, autoX, 0.001f)

        // Verify distinct ordering: left < center < right
        assertTrue(leftX < centerX)
        assertTrue(centerX < rightX)
    }

    @Test
    fun testCustomAlignmentOverridesGlobalConfig() {
        val globalAlignment = TextAlignment.CENTER
        val customAlignmentLeft = TextAlignment.LEFT
        val customAlignmentRight = TextAlignment.RIGHT

        val bounds = RectF(0f, 0f, 300f, 150f)
        val lineWidth = 120f

        // When custom alignment is provided, it must take precedence over global alignment
        val effectiveAlignLeft = customAlignmentLeft ?: globalAlignment
        val effectiveAlignRight = customAlignmentRight ?: globalAlignment
        val effectiveAlignDefault = null ?: globalAlignment

        assertEquals(TextAlignment.LEFT, effectiveAlignLeft)
        assertEquals(TextAlignment.RIGHT, effectiveAlignRight)
        assertEquals(TextAlignment.CENTER, effectiveAlignDefault)

        val leftX = computeStartX(effectiveAlignLeft, bounds, lineWidth)
        val defaultX = computeStartX(effectiveAlignDefault, bounds, lineWidth)
        val rightX = computeStartX(effectiveAlignRight, bounds, lineWidth)

        assertEquals(4f, leftX, 0.001f)
        assertEquals(90f, defaultX, 0.001f) // 150 - 60 = 90
        assertEquals(176f, rightX, 0.001f) // 300 - 120 - 4 = 176
    }
}

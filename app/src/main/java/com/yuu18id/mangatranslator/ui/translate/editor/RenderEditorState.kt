package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.PointF
import android.graphics.RectF
import com.yuu18id.mangatranslator.domain.model.CustomFontStyle
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import com.yuu18id.mangatranslator.domain.model.TextAlignment
import com.yuu18id.mangatranslator.domain.model.TextBlock

data class EditableRenderBlock(
    val id: Int,
    val originalText: String,
    val translatedText: String,
    val bounds: RectF,
    val customFontSize: Float? = null,
    val customAlignment: TextAlignment = TextAlignment.CENTER,
    val customFontStyle: CustomFontStyle = CustomFontStyle.NORMAL,
    val isVertical: Boolean = false,
    val language: Language? = null,
    val fgColor: IntArray = intArrayOf(0, 0, 0),
    val bgColor: IntArray = intArrayOf(255, 255, 255),
    val lines: List<Quadrilateral> = emptyList()
) {
    fun moveBy(dx: Float, dy: Float, boundW: Float, boundH: Float): EditableRenderBlock {
        val w = bounds.width()
        val h = bounds.height()
        val newLeft = (bounds.left + dx).coerceIn(0f, boundW - w)
        val newTop = (bounds.top + dy).coerceIn(0f, boundH - h)
        return copy(bounds = RectF(newLeft, newTop, newLeft + w, newTop + h))
    }

    fun resizeCornerAnchor(cornerIndex: Int, newPt: PointF, boundW: Float, boundH: Float): EditableRenderBlock {
        val minSize = 20f
        var l = bounds.left
        var t = bounds.top
        var r = bounds.right
        var b = bounds.bottom

        when (cornerIndex) {
            0 -> { // Top-Left (Anchor is Bottom-Right)
                l = newPt.x.coerceIn(0f, r - minSize)
                t = newPt.y.coerceIn(0f, b - minSize)
            }
            1 -> { // Top-Right (Anchor is Bottom-Left)
                r = newPt.x.coerceIn(l + minSize, boundW)
                t = newPt.y.coerceIn(0f, b - minSize)
            }
            2 -> { // Bottom-Right (Anchor is Top-Left)
                r = newPt.x.coerceIn(l + minSize, boundW)
                b = newPt.y.coerceIn(t + minSize, boundH)
            }
            3 -> { // Bottom-Left (Anchor is Top-Right)
                l = newPt.x.coerceIn(0f, r - minSize)
                b = newPt.y.coerceIn(t + minSize, boundH)
            }
        }
        return copy(bounds = RectF(l, t, r, b))
    }

    fun toTextBlock(): TextBlock {
        val corners = listOf(
            PointF(bounds.left, bounds.top),
            PointF(bounds.right, bounds.top),
            PointF(bounds.right, bounds.bottom),
            PointF(bounds.left, bounds.bottom)
        )
        val quad = Quadrilateral.fromPoints(corners, text = originalText)
        val finalLines = if (lines.isNotEmpty()) lines else listOf(quad)

        return TextBlock(
            lines = finalLines,
            text = originalText,
            translatedText = translatedText,
            language = language,
            fgColor = fgColor,
            bgColor = bgColor,
            boundingBox = bounds,
            isVertical = isVertical,
            customFontSize = customFontSize,
            customAlignment = customAlignment,
            customFontStyle = customFontStyle,
            isManualBounds = true
        )
    }

    companion object {
        fun fromTextBlock(id: Int, block: TextBlock): EditableRenderBlock {
            val b = if (block.boundingBox.width() > 0 && block.boundingBox.height() > 0) {
                block.boundingBox
            } else {
                block.mergedBoundingBox()
            }
            val safeBounds = if (b.width() > 0 && b.height() > 0) b else RectF(0f, 0f, 100f, 100f)

            return EditableRenderBlock(
                id = id,
                originalText = block.text,
                translatedText = if (block.translatedText.isNotBlank()) block.translatedText else block.text,
                bounds = RectF(safeBounds),
                customFontSize = block.customFontSize,
                customAlignment = block.customAlignment ?: TextAlignment.CENTER,
                customFontStyle = block.customFontStyle ?: CustomFontStyle.NORMAL,
                isVertical = block.isVertical,
                language = block.language,
                fgColor = block.fgColor,
                bgColor = block.bgColor,
                lines = block.lines
            )
        }
    }
}

package com.yuu18id.mangatranslator.data.rendering

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.yuu18id.mangatranslator.domain.model.TextAlignment
import kotlin.math.max

object MangaTextDrawHelper {

    /**
     * Generates a smooth, symmetrical, beautifully proportioned Manga Heart Path.
     */
    fun createMangaHeartPath(
        cx: Float,
        cy: Float,
        width: Float,
        height: Float,
        path: Path = Path()
    ): Path {
        path.reset()
        val topY = cy - height * 0.50f
        val bottomY = cy + height * 0.50f
        val notchY = cy - height * 0.16f
        val leftX = cx - width * 0.50f
        val rightX = cx + width * 0.50f

        path.moveTo(cx, notchY)
        // Left lobe (notch -> top left peak -> left outer curve -> bottom point)
        path.cubicTo(
            cx - width * 0.16f, topY - height * 0.08f,
            leftX, topY + height * 0.02f,
            leftX, cy - height * 0.05f
        )
        path.cubicTo(
            leftX, cy + height * 0.22f,
            cx - width * 0.18f, bottomY - height * 0.10f,
            cx, bottomY
        )
        // Right lobe (bottom point -> right outer curve -> top right peak -> notch)
        path.cubicTo(
            cx + width * 0.18f, bottomY - height * 0.10f,
            rightX, cy + height * 0.22f,
            rightX, cy - height * 0.05f
        )
        path.cubicTo(
            rightX, topY + height * 0.02f,
            cx + width * 0.16f, topY - height * 0.08f,
            cx, notchY
        )
        path.close()
        return path
    }

    /**
     * Measures the exact render width of a line, accounting for custom manga heart vector dimensions.
     */
    fun measureLineWidth(line: String, textPaint: Paint): Float {
        if (!line.contains("♥") && !line.contains("♡")) {
            return textPaint.measureText(line)
        }

        var totalWidth = 0f
        val heartWidth = textPaint.textSize * 0.90f
        var textStartIndex = -1

        for (i in line.indices) {
            val c = line[i]
            if (c == '♥' || c == '♡') {
                if (textStartIndex != -1) {
                    val textChunk = line.substring(textStartIndex, i)
                    totalWidth += textPaint.measureText(textChunk)
                    textStartIndex = -1
                }
                totalWidth += heartWidth
            } else {
                if (textStartIndex == -1) {
                    textStartIndex = i
                }
            }
        }
        if (textStartIndex != -1) {
            val textChunk = line.substring(textStartIndex)
            totalWidth += textPaint.measureText(textChunk)
        }
        return totalWidth
    }

    /**
     * Draws horizontal text lines with vector-rendered manga hearts matching the font color and stroke.
     */
    fun drawHorizontalText(
        canvas: Canvas,
        layoutResult: LayoutResult,
        bounds: RectF,
        textPaint: Paint,
        strokePaint: Paint?,
        alignment: TextAlignment,
        disableFontBorder: Boolean = false
    ) {
        val effectiveAlignment = if (alignment == TextAlignment.AUTO) TextAlignment.CENTER else alignment
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val lineSpacing = layoutResult.fontSize * 1.18f
        val totalBlockHeight = (layoutResult.lines.size - 1) * lineSpacing + textHeight
        val firstLineBaseline = bounds.centerY() - totalBlockHeight / 2f - fontMetrics.ascent

        val heartPath = Path()

        for ((index, line) in layoutResult.lines.withIndex()) {
            val lineWidth = measureLineWidth(line, textPaint)
            val startX = when (effectiveAlignment) {
                TextAlignment.LEFT -> bounds.left + 4f
                TextAlignment.RIGHT -> bounds.right - lineWidth - 4f
                else -> bounds.centerX() - lineWidth / 2f
            }
            val lineY = firstLineBaseline + index * lineSpacing

            if (!line.contains("♥") && !line.contains("♡")) {
                if (!disableFontBorder && strokePaint != null) {
                    canvas.drawText(line, startX, lineY, strokePaint)
                }
                canvas.drawText(line, startX, lineY, textPaint)
            } else {
                // Line has hearts: split and draw each chunk with exact vector hearts
                val fontSize = textPaint.textSize
                val heartW = fontSize * 0.90f
                val heartH = fontSize * 0.82f
                val heartCy = lineY - fontSize * 0.35f

                var curX = startX
                var textStartIndex = -1

                for (i in line.indices) {
                    val c = line[i]
                    if (c == '♥' || c == '♡') {
                        if (textStartIndex != -1) {
                            val textChunk = line.substring(textStartIndex, i)
                            val chunkW = textPaint.measureText(textChunk)
                            if (!disableFontBorder && strokePaint != null) {
                                canvas.drawText(textChunk, curX, lineY, strokePaint)
                            }
                            canvas.drawText(textChunk, curX, lineY, textPaint)
                            curX += chunkW
                            textStartIndex = -1
                        }

                        val heartCx = curX + heartW / 2f
                        createMangaHeartPath(heartCx, heartCy, heartW * 0.90f, heartH * 0.90f, heartPath)

                        if (!disableFontBorder && strokePaint != null) {
                            canvas.drawPath(heartPath, strokePaint)
                        }

                        if (c == '♥') {
                            // Solid heart: filled with font color
                            canvas.drawPath(heartPath, textPaint)
                        } else {
                            // Hollow heart: outlined with font color
                            val hollowPaint = Paint(textPaint).apply {
                                style = Paint.Style.STROKE
                                strokeWidth = max(2.5f, fontSize * 0.12f)
                            }
                            canvas.drawPath(heartPath, hollowPaint)
                        }
                        curX += heartW
                    } else {
                        if (textStartIndex == -1) {
                            textStartIndex = i
                        }
                    }
                }
                if (textStartIndex != -1) {
                    val textChunk = line.substring(textStartIndex)
                    if (!disableFontBorder && strokePaint != null) {
                        canvas.drawText(textChunk, curX, lineY, strokePaint)
                    }
                    canvas.drawText(textChunk, curX, lineY, textPaint)
                }
            }
        }
    }

    /**
     * Draws vertical text lines with vector-rendered manga hearts matching the font color and stroke.
     */
    fun drawVerticalText(
        canvas: Canvas,
        layoutResult: LayoutResult,
        bounds: RectF,
        textPaint: Paint,
        strokePaint: Paint?,
        disableFontBorder: Boolean = false
    ) {
        var currentX = bounds.right - (bounds.width() - layoutResult.totalWidth) / 2f - (layoutResult.lineHeights.firstOrNull() ?: 0f) / 2f
        val heartPath = Path()
        val boundsRect = Rect()

        for ((index, line) in layoutResult.lines.withIndex()) {
            var currentY = bounds.top + (bounds.height() - calculateVerticalLineHeight(line, textPaint)) / 2f

            for (char in line) {
                if (char == '♥' || char == '♡') {
                    val fontSize = textPaint.textSize
                    val heartW = fontSize * 0.90f
                    val heartH = fontSize * 0.82f
                    val heartCx = currentX
                    val heartCy = currentY + heartH / 2f

                    createMangaHeartPath(heartCx, heartCy, heartW * 0.90f, heartH * 0.90f, heartPath)

                    if (!disableFontBorder && strokePaint != null) {
                        canvas.drawPath(heartPath, strokePaint)
                    }
                    if (char == '♥') {
                        canvas.drawPath(heartPath, textPaint)
                    } else {
                        val hollowPaint = Paint(textPaint).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = max(2.5f, fontSize * 0.12f)
                        }
                        canvas.drawPath(heartPath, hollowPaint)
                    }
                    currentY += heartH + textPaint.fontMetrics.descent
                } else {
                    val charStr = char.toString()
                    val charWidth = textPaint.measureText(charStr)
                    textPaint.getTextBounds(charStr, 0, 1, boundsRect)

                    val charX = currentX - charWidth / 2f
                    currentY += boundsRect.height()

                    if (!disableFontBorder && strokePaint != null) {
                        canvas.drawText(charStr, charX, currentY, strokePaint)
                    }
                    canvas.drawText(charStr, charX, currentY, textPaint)

                    currentY += textPaint.fontMetrics.descent
                }
            }

            if (index + 1 < layoutResult.lineHeights.size) {
                currentX -= layoutResult.lineHeights[index + 1] * 1.2f
            }
        }
    }

    private fun calculateVerticalLineHeight(line: String, paint: Paint): Float {
        var height = 0f
        val bounds = Rect()
        for (char in line) {
            if (char == '♥' || char == '♡') {
                height += (paint.textSize * 0.82f) + paint.fontMetrics.descent
            } else {
                paint.getTextBounds(char.toString(), 0, 1, bounds)
                height += bounds.height() + paint.fontMetrics.descent
            }
        }
        return height
    }
}

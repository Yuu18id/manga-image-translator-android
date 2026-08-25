package com.yuu18id.mangatranslator.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.yuu18id.mangatranslator.data.ml.TextRenderer
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.RenderConfig
import com.yuu18id.mangatranslator.domain.model.TextAlignment
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TextDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class CanvasTextRenderer @Inject constructor(
    private val fontManager: FontManager,
    private val layoutEngine: TextLayoutEngine
) : TextRenderer {

    companion object {
        private const val TAG = "MangaTranslator"
    }

    override suspend fun render(
        inpaintedImage: Bitmap,
        textBlocks: List<TextBlock>,
        config: RenderConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        val resultBitmap = inpaintedImage.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        Log.i(TAG, "▶ [CANVAS RENDERER] Rendering ${textBlocks.size} text blocks on canvas ${resultBitmap.width}x${resultBitmap.height}")

        for ((i, block) in textBlocks.withIndex()) {
            val textToRender = if (block.translatedText.isNotBlank()) block.translatedText.trim() else block.text.trim()
            if (textToRender.isBlank()) {
                Log.w(TAG, "   Block $i SKIPPED: text is blank (orig=\"${block.text}\", trans=\"${block.translatedText}\")")
                continue
            }

            val bounds = block.mergedBoundingBox()
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                Log.w(TAG, "   Block $i SKIPPED: invalid bounds $bounds")
                continue
            }

            // Determine orientation: Latin/Indonesian/Western languages are ALWAYS horizontal
            val isCJK = block.language in listOf(Language.JPN, Language.CHS, Language.CHT, Language.KOR)
            val isVertical = when (config.direction) {
                TextDirection.HORIZONTAL -> false
                TextDirection.VERTICAL -> true
                TextDirection.AUTO -> isCJK && block.isVertical
            }

            val targetTypeface = fontManager.getTypefaceForLanguage(block.language ?: Language.ENG)

            // Estimate original line height / font size from detected textlines
            val avgLineHeight = if (block.lines.isNotEmpty()) {
                block.lines.map { if (it.isVertical) it.width() else it.height() }.average().toFloat()
            } else {
                bounds.height() / 3f
            }

            val layoutResult = layoutEngine.calculateLayout(
                text = textToRender,
                targetWidth = bounds.width(),
                targetHeight = bounds.height(),
                estimatedOriginalFontSize = avgLineHeight,
                language = block.language,
                config = config,
                isVertical = isVertical
            )

            Log.i(TAG, "   Block $i RENDERING: text=\"$textToRender\"")
            Log.i(TAG, "      bounds=$bounds, fontSize=${layoutResult.fontSize}, lines=${layoutResult.lines.size}, isVertical=$isVertical")
            layoutResult.lines.forEachIndexed { lineIdx, lineStr ->
                Log.d(TAG, "         Line $lineIdx: \"$lineStr\"")
            }

            if (layoutResult.lines.isEmpty()) continue

            // Setup Fill Paint (Sharp Black text)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = targetTypeface
                textSize = layoutResult.fontSize
                style = Paint.Style.FILL
                color = Color.BLACK
            }

            // Setup Outline Paint (Bold White stroke for strong contrast against backgrounds)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = targetTypeface
                textSize = layoutResult.fontSize
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                strokeWidth = max(5.0f, layoutResult.fontSize * 0.28f)
                color = Color.WHITE
            }

            canvas.save()

            if (block.angle != 0f) {
                canvas.rotate(block.angle, bounds.centerX(), bounds.centerY())
            }

            if (isVertical) {
                drawVerticalText(canvas, layoutResult, bounds, textPaint, strokePaint, config)
            } else {
                drawHorizontalText(canvas, layoutResult, bounds, textPaint, strokePaint, config)
            }

            canvas.restore()
        }

        resultBitmap
    }

    private fun drawHorizontalText(
        canvas: Canvas,
        layoutResult: LayoutResult,
        bounds: RectF,
        textPaint: Paint,
        strokePaint: Paint,
        config: RenderConfig
    ) {
        val alignment = if (config.alignment == TextAlignment.AUTO) TextAlignment.CENTER else config.alignment
        
        val lineSpacing = layoutResult.fontSize * 1.2f
        val totalHeight = layoutResult.lines.size * lineSpacing
        val startY = bounds.top + (bounds.height() - totalHeight) / 2f + layoutResult.fontSize * 0.9f

        for ((index, line) in layoutResult.lines.withIndex()) {
            val lineWidth = textPaint.measureText(line)
            val startX = when (alignment) {
                TextAlignment.LEFT -> bounds.left + 4f
                TextAlignment.RIGHT -> bounds.right - lineWidth - 4f
                else -> bounds.left + (bounds.width() - lineWidth) / 2f
            }

            val lineY = startY + index * lineSpacing

            if (!config.disableFontBorder) {
                canvas.drawText(line, startX, lineY, strokePaint)
            }
            canvas.drawText(line, startX, lineY, textPaint)
        }
    }

    private fun drawVerticalText(
        canvas: Canvas,
        layoutResult: LayoutResult,
        bounds: RectF,
        textPaint: Paint,
        strokePaint: Paint,
        config: RenderConfig
    ) {
        var currentX = bounds.right - (bounds.width() - layoutResult.totalWidth) / 2f - (layoutResult.lineHeights.firstOrNull() ?: 0f) / 2f

        for ((index, line) in layoutResult.lines.withIndex()) {
            var currentY = bounds.top + (bounds.height() - calculateVerticalLineHeight(line, textPaint)) / 2f

            for (char in line) {
                val charStr = char.toString()
                val charWidth = textPaint.measureText(charStr)
                val boundsRect = Rect()
                textPaint.getTextBounds(charStr, 0, 1, boundsRect)
                
                val charX = currentX - charWidth / 2f
                currentY += boundsRect.height()

                if (!config.disableFontBorder) {
                    canvas.drawText(charStr, charX, currentY, strokePaint)
                }
                canvas.drawText(charStr, charX, currentY, textPaint)
                
                currentY += textPaint.fontMetrics.descent
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
            paint.getTextBounds(char.toString(), 0, 1, bounds)
            height += bounds.height() + paint.fontMetrics.descent
        }
        return height
    }
}

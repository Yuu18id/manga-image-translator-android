package com.yuu18id.mangatranslator.data.rendering

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.yuu18id.mangatranslator.data.ml.TextRenderer
import com.yuu18id.mangatranslator.domain.model.CustomFontStyle
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
import kotlin.math.min

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
    ): Bitmap {
        return renderWithUpdatedBlocks(inpaintedImage, textBlocks, config).first
    }

    override suspend fun renderWithUpdatedBlocks(
        inpaintedImage: Bitmap,
        textBlocks: List<TextBlock>,
        config: RenderConfig
    ): Pair<Bitmap, List<TextBlock>> = withContext(Dispatchers.Default) {
        val resultBitmap = inpaintedImage.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        Log.i(TAG, "▶ [CANVAS RENDERER] Rendering ${textBlocks.size} text blocks on canvas ${resultBitmap.width}x${resultBitmap.height}")

        val candidates = mutableListOf<RenderCandidate>()

        for ((i, block) in textBlocks.withIndex()) {
            val textToRender = if (block.translatedText.isNotBlank()) block.translatedText.trim() else block.text.trim()
            if (textToRender.isBlank()) {
                Log.w(TAG, "   Block $i SKIPPED: text is blank (orig=\"${block.text}\", trans=\"${block.translatedText}\")")
                continue
            }

            val rawBounds = block.mergedBoundingBox()
            if (rawBounds.width() <= 0 || rawBounds.height() <= 0) {
                Log.w(TAG, "   Block $i SKIPPED: invalid bounds $rawBounds")
                continue
            }

            // Determine orientation: Latin/Indonesian/Western languages are ALWAYS horizontal
            val isCJK = block.language in listOf(Language.JPN, Language.CHS, Language.CHT, Language.KOR)
            val isVertical = when (config.direction) {
                TextDirection.HORIZONTAL -> false
                TextDirection.VERTICAL -> true
                TextDirection.AUTO -> isCJK && block.isVertical
            }

            // Calculate dynamic speech bubble bounds: if already manual bounds or edited, preserve rawBounds!
            val bounds = if (!isVertical) {
                if (block.isManualBounds || block.customFontSize != null) {
                    rawBounds
                } else {
                    calculateDynamicBubbleBounds(i, textBlocks, resultBitmap)
                }
            } else {
                rawBounds
            }

            // Estimate original line height / font size from detected textlines
            val avgLineHeight = if (block.lines.isNotEmpty()) {
                block.lines.map { if (it.isVertical) it.width() else it.height() }.average().toFloat()
            } else {
                bounds.height() / 3f
            }

            val customConfig = if (block.customAlignment != null) {
                config.copy(alignment = block.customAlignment)
            } else {
                config
            }

            val blockFontStyle = block.customFontStyle ?: CustomFontStyle.NORMAL
            val layoutResult = if (block.customFontSize != null) {
                layoutEngine.layoutWithFontSize(
                    text = textToRender,
                    targetWidth = bounds.width(),
                    targetHeight = bounds.height(),
                    fontSize = block.customFontSize,
                    language = block.language,
                    config = customConfig,
                    isVertical = isVertical,
                    fontStyle = blockFontStyle
                )
            } else {
                layoutEngine.calculateLayout(
                    text = textToRender,
                    targetWidth = bounds.width(),
                    targetHeight = bounds.height(),
                    estimatedOriginalFontSize = avgLineHeight,
                    language = block.language,
                    config = customConfig,
                    isVertical = isVertical,
                    fontStyle = blockFontStyle
                )
            }

            if (layoutResult.lines.isNotEmpty()) {
                candidates.add(RenderCandidate(i, block, textToRender, bounds, rawBounds, isVertical, layoutResult))
            }
        }

        // Global Page Typography Harmonization:
        // Lift slightly smaller font sizes up towards median, but NEVER downscale spacious bubbles
        val standardDialogues = candidates.filter { c ->
            c.block.customFontSize == null &&
            c.layoutResult.fontSize in 13f..38f &&
            c.textToRender.split(Regex("\\s+")).filter { it.isNotBlank() }.size >= 2
        }

        if (standardDialogues.size >= 2) {
            val medianFontSize = standardDialogues
                .map { it.layoutResult.fontSize }
                .sorted()
                .let { list -> list[list.size / 2] }
                .toInt()
                .toFloat()

            for (c in standardDialogues) {
                val currentSize = c.layoutResult.fontSize
                // Only harmonize upwards if within 4.0pt of median, NEVER downscale
                if (currentSize < medianFontSize && (medianFontSize - currentSize) <= 4.0f) {
                    val harmonized = layoutEngine.layoutWithFontSize(
                        text = c.textToRender,
                        targetWidth = c.bounds.width(),
                        targetHeight = c.bounds.height(),
                        fontSize = medianFontSize,
                        language = c.block.language,
                        config = config,
                        isVertical = c.isVertical,
                        fontStyle = c.block.customFontStyle ?: CustomFontStyle.NORMAL
                    )
                    Log.i(TAG, "   Harmonized Block ${c.index} font size from $currentSize -> $medianFontSize")
                    c.layoutResult = harmonized
                }
            }
        }

        // Render all harmonized candidates to canvas
        for (c in candidates) {
            val block = c.block
            val layoutResult = c.layoutResult
            val bounds = c.bounds
            val isVertical = c.isVertical

            val targetTypeface = fontManager.getTypefaceForLanguage(
                block.language ?: Language.ENG,
                block.customFontStyle ?: CustomFontStyle.NORMAL
            )

            Log.i(TAG, "   Block ${c.index} RENDERING: text=\"${c.textToRender}\"")
            Log.i(TAG, "      rawBounds=${c.rawBounds}, expandedBounds=$bounds, fontSize=${layoutResult.fontSize}, lines=${layoutResult.lines.size}, isVertical=$isVertical")
            layoutResult.lines.forEachIndexed { lineIdx, lineStr ->
                Log.d(TAG, "         Line $lineIdx: \"$lineStr\"")
            }

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

        val updatedTextBlocks = textBlocks.mapIndexed { idx, originalBlock ->
            val matchingCandidate = candidates.find { it.index == idx }
            if (matchingCandidate != null) {
                originalBlock.copy(
                    boundingBox = matchingCandidate.bounds,
                    customFontSize = matchingCandidate.layoutResult.fontSize,
                    customAlignment = matchingCandidate.block.customAlignment ?: config.alignment,
                    customFontStyle = matchingCandidate.block.customFontStyle ?: CustomFontStyle.NORMAL,
                    isManualBounds = true
                )
            } else {
                originalBlock
            }
        }

        Pair(resultBitmap, updatedTextBlocks)
    }

    private data class RenderCandidate(
        val index: Int,
        val block: TextBlock,
        val textToRender: String,
        val bounds: RectF,
        val rawBounds: RectF,
        val isVertical: Boolean,
        var layoutResult: LayoutResult
    )

    private fun calculateDynamicBubbleBounds(
        blockIndex: Int,
        textBlocks: List<TextBlock>,
        bitmap: Bitmap
    ): RectF {
        val block = textBlocks[blockIndex]
        val raw = block.mergedBoundingBox()
        val canvasWidth = bitmap.width
        val canvasHeight = bitmap.height

        val rawW = max(raw.width(), 8f)
        val rawH = max(raw.height(), 8f)
        val lineCount = block.lines.size
        val avgLineThickness = if (block.lines.isNotEmpty()) {
            block.lines.map { if (it.isVertical) it.width() else it.height() }.average().toFloat()
        } else {
            if (block.isVertical) rawW / max(lineCount, 1) else rawH / max(lineCount, 1)
        }

        // Generous search limits allowing spacious balloons to be discovered in their entirety
        val maxExpandWidth = max(rawW * 3.5f, max(rawH * 1.5f, avgLineThickness * 8.0f))
        val maxExpandHeight = max(rawH * 1.8f, avgLineThickness * 6.0f)

        val cx = raw.centerX()
        val cy = raw.centerY()

        // Neighbor collision boundaries
        var boundLeft = 6f
        var boundRight = canvasWidth - 6f
        var boundTop = 4f
        var boundBottom = canvasHeight - 4f

        for ((j, other) in textBlocks.withIndex()) {
            if (j == blockIndex) continue
            val otherBounds = other.mergedBoundingBox()
            val vOverlap = max(0f, min(raw.bottom, otherBounds.bottom) - max(raw.top, otherBounds.top))
            val hOverlap = max(0f, min(raw.right, otherBounds.right) - max(raw.left, otherBounds.left))

            if (vOverlap > 8f) {
                if (otherBounds.centerX() < cx) {
                    boundLeft = max(boundLeft, otherBounds.right + 6f)
                } else if (otherBounds.centerX() > cx) {
                    boundRight = min(boundRight, otherBounds.left - 6f)
                }
            }
            if (hOverlap > 8f) {
                if (otherBounds.centerY() < cy) {
                    boundTop = max(boundTop, otherBounds.bottom + 6f)
                } else if (otherBounds.centerY() > cy) {
                    boundBottom = min(boundBottom, otherBounds.top - 6f)
                }
            }
        }

        val searchLeftLimit = max(boundLeft, cx - maxExpandWidth / 2f).toInt().coerceIn(0, canvasWidth - 1)
        val searchRightLimit = min(boundRight, cx + maxExpandWidth / 2f).toInt().coerceIn(0, canvasWidth - 1)
        val searchTopLimit = max(boundTop, cy - maxExpandHeight / 2f).toInt().coerceIn(0, canvasHeight - 1)
        val searchBottomLimit = min(boundBottom, cy + maxExpandHeight / 2f).toInt().coerceIn(0, canvasHeight - 1)

        val cxInt = cx.toInt().coerceIn(0, canvasWidth - 1)
        val cyInt = cy.toInt().coerceIn(0, canvasHeight - 1)

        // Measure interior luminance
        val centerPixel = bitmap.getPixel(cxInt, cyInt)
        val centerLum = ((centerPixel shr 16 and 0xFF) * 299 + (centerPixel shr 8 and 0xFF) * 587 + (centerPixel and 0xFF) * 114) / 1000

        var detectedLeft = searchLeftLimit.toFloat()
        var detectedRight = searchRightLimit.toFloat()
        var detectedTop = searchTopLimit.toFloat()
        var detectedBottom = searchBottomLimit.toFloat()

        if (centerLum >= 180) {
            // Precise raycast for solid black bubble ink strokes (lum < 75 for 2px or lum < 45 for 1px)
            // Left Raycast
            var consecutiveDark = 0
            for (x in cxInt downTo searchLeftLimit) {
                val p = bitmap.getPixel(x, cyInt)
                val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                if (lum < 75) consecutiveDark++ else consecutiveDark = 0
                if (consecutiveDark >= 2 || lum < 45) {
                    detectedLeft = (x.toFloat() + 5f).coerceAtLeast(searchLeftLimit.toFloat())
                    break
                }
            }

            // Right Raycast
            consecutiveDark = 0
            for (x in cxInt..searchRightLimit) {
                val p = bitmap.getPixel(x, cyInt)
                val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                if (lum < 75) consecutiveDark++ else consecutiveDark = 0
                if (consecutiveDark >= 2 || lum < 45) {
                    detectedRight = (x.toFloat() - 5f).coerceAtMost(searchRightLimit.toFloat())
                    break
                }
            }

            // Top Raycast
            consecutiveDark = 0
            for (y in cyInt downTo searchTopLimit) {
                val p = bitmap.getPixel(cxInt, y)
                val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                if (lum < 75) consecutiveDark++ else consecutiveDark = 0
                if (consecutiveDark >= 2 || lum < 45) {
                    detectedTop = (y.toFloat() + 5f).coerceAtLeast(searchTopLimit.toFloat())
                    break
                }
            }

            // Bottom Raycast
            consecutiveDark = 0
            for (y in cyInt..searchBottomLimit) {
                val p = bitmap.getPixel(cxInt, y)
                val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                if (lum < 75) consecutiveDark++ else consecutiveDark = 0
                if (consecutiveDark >= 2 || lum < 45) {
                    detectedBottom = (y.toFloat() - 5f).coerceAtMost(searchBottomLimit.toFloat())
                    break
                }
            }
        }

        // Symmetric centering from balloon center:
        // By taking the symmetric minimum distance from (cx, cy) to the detected boundaries,
        // we guarantee that the bounding box is mathematically centered at (cx, cy) with 0 left/right or top/bottom bias!
        val distLeft = cx - detectedLeft
        val distRight = detectedRight - cx
        val symmetricHalfW = min(distLeft, distRight).coerceAtLeast(rawW / 2f) * 0.85f

        val distTop = cy - detectedTop
        val distBottom = detectedBottom - cy
        val symmetricHalfH = min(distTop, distBottom).coerceAtLeast(rawH / 2f) * 0.84f

        val finalLeft = max(boundLeft, cx - symmetricHalfW).coerceAtLeast(4f)
        val finalRight = min(boundRight, cx + symmetricHalfW).coerceAtMost(canvasWidth - 4f)
        val finalTop = max(boundTop, cy - symmetricHalfH).coerceAtLeast(4f)
        val finalBottom = min(boundBottom, cy + symmetricHalfH).coerceAtMost(canvasHeight - 4f)

        return RectF(finalLeft, finalTop, finalRight, finalBottom)
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
        
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val lineSpacing = layoutResult.fontSize * 1.18f
        val totalBlockHeight = (layoutResult.lines.size - 1) * lineSpacing + textHeight
        
        // Exact baseline of first line so that the entire text block is perfectly centered vertically
        val firstLineBaseline = bounds.centerY() - totalBlockHeight / 2f - fontMetrics.ascent

        for ((index, line) in layoutResult.lines.withIndex()) {
            val lineWidth = textPaint.measureText(line)
            val startX = when (alignment) {
                TextAlignment.LEFT -> bounds.left + 4f
                TextAlignment.RIGHT -> bounds.right - lineWidth - 4f
                else -> bounds.centerX() - lineWidth / 2f
            }

            val lineY = firstLineBaseline + index * lineSpacing

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

package com.yuu18id.mangatranslator.data.rendering

import android.graphics.Paint
import android.graphics.Rect
import com.yuu18id.mangatranslator.data.textline.TextPostProcessor
import com.yuu18id.mangatranslator.domain.model.CustomFontStyle
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.RenderConfig
import com.yuu18id.mangatranslator.domain.model.TextDirection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class LayoutResult(
    val lines: List<String>,
    val fontSize: Float,
    val totalWidth: Float,
    val totalHeight: Float,
    val lineHeights: List<Float>
)

@Singleton
class TextLayoutEngine @Inject constructor(
    private val fontManager: FontManager
) {
    fun calculateLayout(
        text: String,
        targetWidth: Float,
        targetHeight: Float,
        estimatedOriginalFontSize: Float,
        language: Language?,
        config: RenderConfig,
        isVertical: Boolean,
        fontStyle: CustomFontStyle = CustomFontStyle.NORMAL,
        fontFamily: com.yuu18id.mangatranslator.domain.model.CustomFontFamily = com.yuu18id.mangatranslator.domain.model.CustomFontFamily.WILD_WORDS
    ): LayoutResult {
        val cleanText = TextPostProcessor.processText(text.trim())
        if (cleanText.isEmpty()) {
            return LayoutResult(emptyList(), 16f, 0f, 0f, emptyList())
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = fontManager.getTypefaceForLanguage(language ?: Language.ENG, fontStyle, fontFamily)
        }

        val isCJK = language in listOf(Language.JPN, Language.CHS, Language.CHT, Language.KOR)
        val isVerticalLayout = when (config.direction) {
            TextDirection.HORIZONTAL -> false
            TextDirection.VERTICAL -> true
            TextDirection.AUTO -> isCJK && isVertical
        }

        // Available area with comfortable padding for speech bubble aesthetics & stroke outline allowance
        val offset = config.fontSizeOffset.toFloat()
        val estimatedEffectiveFontSize = max(12f, estimatedOriginalFontSize + offset)
        val strokeAllowance = if (config.disableFontBorder) 0f else max(3.5f, estimatedEffectiveFontSize * 0.20f)

        // Safe usable area inside dynamically computed bubble bounds
        val maxAvailableWidth = max(targetWidth * 0.94f - strokeAllowance, 25f)
        val maxAvailableHeight = max(targetHeight * 0.92f - strokeAllowance, 25f)

        val minAllowedFontSize = max(11f, if (config.fontSizeMinimum > 0) config.fontSizeMinimum.toFloat() else 12f)

        // Dynamic maximum font size allows roomy balloons to stay neatly proportioned
        val words = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val maxAllowedFontSize = when {
            words.size <= 2 -> min(maxAvailableHeight * 0.50f, max(36f, estimatedOriginalFontSize * 1.50f))
            words.size <= 5 -> min(maxAvailableHeight * 0.40f, max(30f, estimatedOriginalFontSize * 1.30f))
            words.size <= 10 -> min(maxAvailableHeight * 0.32f, max(26f, estimatedOriginalFontSize * 1.15f))
            else -> min(maxAvailableHeight * 0.28f, max(23f, estimatedOriginalFontSize * 1.05f))
        }

        val minSize = (minAllowedFontSize + offset).coerceAtLeast(10f)
        val maxSize = (maxAllowedFontSize + offset).coerceAtLeast(minSize + 2f)

        return if (isVerticalLayout) {
            layoutVertical(cleanText, paint, minSize, maxSize, maxAvailableWidth, maxAvailableHeight)
        } else {
            layoutHorizontalBinarySearch(cleanText, paint, minSize, maxSize, maxAvailableWidth, maxAvailableHeight)
        }
    }

    fun layoutWithFontSize(
        text: String,
        targetWidth: Float,
        targetHeight: Float,
        fontSize: Float,
        language: Language?,
        config: RenderConfig,
        isVertical: Boolean,
        fontStyle: CustomFontStyle = CustomFontStyle.NORMAL,
        fontFamily: com.yuu18id.mangatranslator.domain.model.CustomFontFamily = com.yuu18id.mangatranslator.domain.model.CustomFontFamily.WILD_WORDS
    ): LayoutResult {
        val cleanText = TextPostProcessor.processText(text.trim())
        if (cleanText.isEmpty()) {
            return LayoutResult(emptyList(), fontSize, 0f, 0f, emptyList())
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = fontManager.getTypefaceForLanguage(language ?: Language.ENG, fontStyle, fontFamily)
            textSize = fontSize
        }

        val isCJK = language in listOf(Language.JPN, Language.CHS, Language.CHT, Language.KOR)
        val isVerticalLayout = when (config.direction) {
            TextDirection.HORIZONTAL -> false
            TextDirection.VERTICAL -> true
            TextDirection.AUTO -> isCJK && isVertical
        }

        val strokeAllowance = if (config.disableFontBorder) 0f else max(3.5f, fontSize * 0.20f)
        val maxAvailableWidth = max(targetWidth - strokeAllowance, 25f)
        val maxAvailableHeight = max(targetHeight - strokeAllowance, 25f)

        return if (isVerticalLayout) {
            val lines = wrapVertical(cleanText, paint, maxAvailableHeight)
            var totalW = 0f
            var totalH = 0f
            for (line in lines) {
                var lineWidth = 0f
                var lineHeight = 0f
                for (char in line) {
                    lineWidth = max(lineWidth, paint.measureText(char.toString()))
                    lineHeight += fontSize * 1.15f
                }
                totalW += lineWidth * 1.25f
                totalH = max(totalH, lineHeight)
            }
            val lineHeights = List(lines.size) { fontSize * 1.15f }
            LayoutResult(lines, fontSize, totalW, totalH, lineHeights)
        } else {
            val lines = wrapHorizontalBalanced(cleanText, paint, maxAvailableWidth)
            val lineSpacing = fontSize * 1.18f
            val totalH = lines.size * lineSpacing
            val totalW = lines.maxOfOrNull { MangaTextDrawHelper.measureLineWidth(it, paint) } ?: 0f
            val lineHeights = List(lines.size) { lineSpacing }
            LayoutResult(lines, fontSize, totalW, totalH, lineHeights)
        }
    }

    private fun layoutHorizontalBinarySearch(
        text: String,
        paint: Paint,
        minSize: Float,
        maxSize: Float,
        maxWidth: Float,
        maxHeight: Float
    ): LayoutResult {
        var low = minSize
        var high = maxSize
        var bestSize = minSize
        var bestLines: List<String>? = null
        var bestTotalWidth = 0f
        var bestTotalHeight = 0f

        val allowedMaxWidth = maxWidth * 1.05f

        // Binary search for the optimal font size that cleanly fills the balloon
        var iterations = 0
        while (high - low >= 0.5f && iterations < 16) {
            iterations++
            val mid = (low + high) / 2f
            paint.textSize = mid

            val wrapped = wrapHorizontalBalanced(text, paint, maxWidth)
            val lineSpacing = mid * 1.18f
            val totalH = wrapped.size * lineSpacing
            val totalW = wrapped.maxOfOrNull { MangaTextDrawHelper.measureLineWidth(it, paint) } ?: 0f

            if (totalW <= allowedMaxWidth && totalH <= maxHeight) {
                bestSize = mid
                bestLines = wrapped
                bestTotalWidth = totalW
                bestTotalHeight = totalH
                low = mid // Try larger font size to fill bubble nicely
            } else {
                high = mid // Text exceeded bounds, try smaller
            }
        }

        val finalLines = if (bestLines != null) {
            bestLines!!
        } else {
            // Strict Fallback: Wrap at minSize so text is NEVER a single unwrapped runaway line
            paint.textSize = minSize
            val wrappedAtMin = wrapHorizontalBalanced(text, paint, maxWidth)
            bestSize = minSize
            bestTotalWidth = wrappedAtMin.maxOfOrNull { MangaTextDrawHelper.measureLineWidth(it, paint) } ?: 0f
            bestTotalHeight = wrappedAtMin.size * (minSize * 1.18f)
            wrappedAtMin
        }

        val lineHeights = List(finalLines.size) { bestSize * 1.18f }
        return LayoutResult(finalLines, bestSize, bestTotalWidth, bestTotalHeight, lineHeights)
    }

    private fun wrapHorizontalBalanced(text: String, paint: Paint, maxSpan: Float): List<String> {
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")

        for (paragraph in paragraphs) {
            val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue

            var currentLine = StringBuilder()
            var currentSpan = 0f

            for (word in words) {
                val wordWithSpace = if (currentLine.isEmpty()) word else " $word"
                val wordSpan = MangaTextDrawHelper.measureLineWidth(wordWithSpace, paint)

                if (currentSpan + wordSpan > maxSpan && currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                    currentSpan = MangaTextDrawHelper.measureLineWidth(word, paint)
                } else {
                    currentLine.append(wordWithSpace)
                    currentSpan += wordSpan
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
        }

        // Balance orphan words (if last line has only 1 word and previous line has >= 3 words)
        if (lines.size >= 2) {
            val lastLine = lines.last()
            val prevLine = lines[lines.size - 2]
            val lastWords = lastLine.split(" ")
            val prevWords = prevLine.split(" ")
            if (lastWords.size == 1 && prevWords.size >= 3) {
                val movedWord = prevWords.last()
                val newPrev = prevWords.dropLast(1).joinToString(" ")
                val newLast = "$movedWord $lastLine"
                if (paint.measureText(newLast) <= maxSpan) {
                    lines[lines.size - 2] = newPrev
                    lines[lines.size - 1] = newLast
                }
            }
        }

        return if (lines.isEmpty()) listOf(text) else lines
    }

    private fun layoutVertical(
        text: String,
        paint: Paint,
        minSize: Float,
        maxSize: Float,
        maxWidth: Float,
        maxHeight: Float
    ): LayoutResult {
        var currentSize = maxSize
        var bestSize = minSize
        var bestLines = listOf(text)
        var bestTotalWidth = 0f
        var bestTotalHeight = 0f

        while (currentSize >= minSize) {
            paint.textSize = currentSize
            val lines = wrapVertical(text, paint, maxHeight)

            var totalW = 0f
            var totalH = 0f
            for (line in lines) {
                var lineWidth = 0f
                var lineHeight = 0f
                for (char in line) {
                    lineWidth = max(lineWidth, paint.measureText(char.toString()))
                    lineHeight += currentSize * 1.15f
                }
                totalW += lineWidth * 1.25f
                totalH = max(totalH, lineHeight)
            }

            bestSize = currentSize
            bestLines = lines
            bestTotalWidth = totalW
            bestTotalHeight = totalH

            if (totalW <= maxWidth && totalH <= maxHeight) {
                break
            }
            currentSize -= 1.5f
        }

        val lineHeights = List(bestLines.size) { bestSize * 1.15f }
        return LayoutResult(bestLines, bestSize, bestTotalWidth, bestTotalHeight, lineHeights)
    }

    private fun wrapVertical(text: String, paint: Paint, maxHeight: Float): List<String> {
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        var currentHeight = 0f
        val charHeight = paint.textSize * 1.15f

        for (char in text) {
            if (char == '\n') {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                    currentHeight = 0f
                }
                continue
            }

            if (currentHeight + charHeight > maxHeight && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(char.toString())
                currentHeight = charHeight
            } else {
                currentLine.append(char)
                currentHeight += charHeight
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return if (lines.isEmpty()) listOf(text) else lines
    }
}

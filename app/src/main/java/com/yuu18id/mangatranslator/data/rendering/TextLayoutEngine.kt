package com.yuu18id.mangatranslator.data.rendering

import android.graphics.Paint
import android.graphics.Rect
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
        isVertical: Boolean
    ): LayoutResult {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return LayoutResult(emptyList(), 16f, 0f, 0f, emptyList())
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = fontManager.getTypefaceForLanguage(language ?: Language.ENG)
        }
        
        val isCJK = language in listOf(Language.JPN, Language.CHS, Language.CHT, Language.KOR)
        val isVerticalLayout = when (config.direction) {
            TextDirection.HORIZONTAL -> false
            TextDirection.VERTICAL -> true
            TextDirection.AUTO -> isCJK && isVertical
        }
        
        // Base font size estimation (default 18f - 32f based on bubble or original line size)
        val minAllowedFontSize = max(11f, if (config.fontSizeMinimum > 0) config.fontSizeMinimum.toFloat() else 12f)
        val maxAllowedFontSize = max(48f, estimatedOriginalFontSize * 1.5f)
        
        var optimalFontSize = if (estimatedOriginalFontSize > 10f) estimatedOriginalFontSize else 20f
        optimalFontSize += config.fontSizeOffset
        optimalFontSize = optimalFontSize.coerceIn(minAllowedFontSize, maxAllowedFontSize)

        var bestFontSize = optimalFontSize
        var bestLines = listOf(cleanText)
        var bestTotalWidth = 0f
        var bestTotalHeight = 0f

        val effectiveWidth = max(targetWidth, optimalFontSize * 2f)
        val effectiveHeight = max(targetHeight, optimalFontSize * 1.5f)

        // Try sizes starting from optimal down to minAllowedFontSize
        var currentSize = optimalFontSize
        while (currentSize >= minAllowedFontSize) {
            paint.textSize = currentSize
            val wrappedLines = wrapText(cleanText, paint, if (isVerticalLayout) effectiveHeight else effectiveWidth, isVerticalLayout)
            
            var totalW = 0f
            var totalH = 0f
            if (isVerticalLayout) {
                for (line in wrappedLines) {
                    var lineWidth = 0f
                    var lineHeight = 0f
                    for (char in line) {
                        val charStr = char.toString()
                        lineWidth = max(lineWidth, paint.measureText(charStr))
                        lineHeight += currentSize * 1.15f
                    }
                    totalW += lineWidth * 1.2f
                    totalH = max(totalH, lineHeight)
                }
            } else {
                for (line in wrappedLines) {
                    totalW = max(totalW, paint.measureText(line))
                }
                totalH = wrappedLines.size * (currentSize * 1.2f)
            }

            bestFontSize = currentSize
            bestLines = wrappedLines
            bestTotalWidth = totalW
            bestTotalHeight = totalH

            if (totalW <= effectiveWidth * 1.1f && totalH <= effectiveHeight * 1.15f) {
                // Fits nicely within bubble bounds
                break
            }

            currentSize -= 1.5f
        }

        val lineHeights = List(bestLines.size) { bestFontSize * 1.2f }
        return LayoutResult(bestLines, bestFontSize, bestTotalWidth, bestTotalHeight, lineHeights)
    }
    
    private fun wrapText(text: String, paint: Paint, maxSpan: Float, isVerticalLayout: Boolean): List<String> {
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        
        for (paragraph in paragraphs) {
            val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            
            var currentLine = StringBuilder()
            var currentSpan = 0f
            
            for (word in words) {
                val wordWithSpace = if (currentLine.isEmpty()) word else " $word"
                val wordSpan = paint.measureText(wordWithSpace)
                
                if (currentSpan + wordSpan > maxSpan && currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                    currentSpan = paint.measureText(word)
                } else {
                    currentLine.append(wordWithSpace)
                    currentSpan += wordSpan
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
        }
        
        return if (lines.isEmpty()) listOf(text) else lines
    }
}

package com.yuu18id.mangatranslator.domain.model

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

data class TextBlock(
    val lines: List<Quadrilateral>,
    val text: String,
    val translatedText: String = "",
    val language: Language? = null,
    val fgColor: IntArray = intArrayOf(0, 0, 0),
    val bgColor: IntArray = intArrayOf(255, 255, 255),
    val boundingBox: RectF,
    val angle: Float = 0f,
    val isVertical: Boolean = false,
    val isBulletedList: Boolean = false,
    val customFontSize: Float? = null,
    val customAlignment: TextAlignment? = null,
    val isManualBounds: Boolean = false
) {
    fun mergedBoundingBox(): RectF {
        if (isManualBounds || lines.isEmpty()) return boundingBox
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (line in lines) {
            val rect = line.boundingRect()
            minX = min(minX, rect.left)
            minY = min(minY, rect.top)
            maxX = max(maxX, rect.right)
            maxY = max(maxY, rect.bottom)
        }

        return RectF(minX, minY, maxX, maxY)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextBlock

        if (lines != other.lines) return false
        if (text != other.text) return false
        if (translatedText != other.translatedText) return false
        if (language != other.language) return false
        if (!fgColor.contentEquals(other.fgColor)) return false
        if (!bgColor.contentEquals(other.bgColor)) return false
        if (boundingBox != other.boundingBox) return false
        if (angle != other.angle) return false
        if (isVertical != other.isVertical) return false
        if (isBulletedList != other.isBulletedList) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lines.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + translatedText.hashCode()
        result = 31 * result + (language?.hashCode() ?: 0)
        result = 31 * result + fgColor.contentHashCode()
        result = 31 * result + bgColor.contentHashCode()
        result = 31 * result + boundingBox.hashCode()
        result = 31 * result + angle.hashCode()
        result = 31 * result + isVertical.hashCode()
        result = 31 * result + isBulletedList.hashCode()
        return result
    }
}

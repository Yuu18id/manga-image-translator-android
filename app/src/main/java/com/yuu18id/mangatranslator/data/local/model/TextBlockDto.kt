package com.yuu18id.mangatranslator.data.local.model

import android.graphics.RectF
import com.yuu18id.mangatranslator.domain.model.CustomFontFamily
import com.yuu18id.mangatranslator.domain.model.CustomFontStyle
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TextAlignment
import com.yuu18id.mangatranslator.domain.model.TextBlock
import kotlinx.serialization.Serializable

@Serializable
data class TextBlockDto(
    val text: String,
    val translatedText: String = "",
    val language: String? = null,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val angle: Float = 0f,
    val isVertical: Boolean = false,
    val customFontSize: Float? = null,
    val customAlignment: String? = null,
    val customFontStyle: String? = null,
    val customFontFamily: String? = null,
    val customTextColor: Int? = null,
    val fgColor: List<Int> = listOf(0, 0, 0),
    val bgColor: List<Int> = listOf(255, 255, 255),
    val isManualBounds: Boolean = false
) {
    fun toDomain(): TextBlock = TextBlock(
        lines = emptyList(),
        text = text,
        translatedText = translatedText,
        language = language?.let { runCatching { Language.valueOf(it) }.getOrNull() },
        fgColor = fgColor.toIntArray(),
        bgColor = bgColor.toIntArray(),
        boundingBox = RectF(left, top, right, bottom),
        angle = angle,
        isVertical = isVertical,
        customFontSize = customFontSize,
        customAlignment = customAlignment?.let { runCatching { TextAlignment.valueOf(it) }.getOrNull() },
        customFontStyle = customFontStyle?.let { runCatching { CustomFontStyle.valueOf(it) }.getOrNull() },
        customFontFamily = customFontFamily?.let { runCatching { CustomFontFamily.valueOf(it) }.getOrNull() },
        customTextColor = customTextColor,
        isManualBounds = isManualBounds
    )

    companion object {
        fun fromDomain(block: TextBlock): TextBlockDto = TextBlockDto(
            text = block.text,
            translatedText = block.translatedText,
            language = block.language?.name,
            left = block.boundingBox.left,
            top = block.boundingBox.top,
            right = block.boundingBox.right,
            bottom = block.boundingBox.bottom,
            angle = block.angle,
            isVertical = block.isVertical,
            customFontSize = block.customFontSize,
            customAlignment = block.customAlignment?.name,
            customFontStyle = block.customFontStyle?.name,
            customFontFamily = block.customFontFamily?.name,
            customTextColor = block.customTextColor,
            fgColor = block.fgColor.toList(),
            bgColor = block.bgColor.toList(),
            isManualBounds = block.isManualBounds
        )
    }
}

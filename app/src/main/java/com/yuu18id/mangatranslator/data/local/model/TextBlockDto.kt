package com.yuu18id.mangatranslator.data.local.model

import android.graphics.RectF
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
    val isManualBounds: Boolean = false
) {
    fun toDomain(): TextBlock = TextBlock(
        lines = emptyList(),
        text = text,
        translatedText = translatedText,
        language = language?.let { runCatching { Language.valueOf(it) }.getOrNull() },
        boundingBox = RectF(left, top, right, bottom),
        angle = angle,
        isVertical = isVertical,
        customFontSize = customFontSize,
        customAlignment = customAlignment?.let { runCatching { TextAlignment.valueOf(it) }.getOrNull() },
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
            isManualBounds = block.isManualBounds
        )
    }
}

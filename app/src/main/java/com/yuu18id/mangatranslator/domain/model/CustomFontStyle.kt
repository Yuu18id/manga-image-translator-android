package com.yuu18id.mangatranslator.domain.model

enum class CustomFontStyle {
    NORMAL,
    BOLD,
    ITALIC,
    BOLD_ITALIC;

    val isBold: Boolean get() = this == BOLD || this == BOLD_ITALIC
    val isItalic: Boolean get() = this == ITALIC || this == BOLD_ITALIC

    fun withBold(bold: Boolean): CustomFontStyle = when {
        bold && isItalic -> BOLD_ITALIC
        bold && !isItalic -> BOLD
        !bold && isItalic -> ITALIC
        else -> NORMAL
    }

    fun withItalic(italic: Boolean): CustomFontStyle = when {
        italic && isBold -> BOLD_ITALIC
        italic && !isBold -> ITALIC
        !italic && isBold -> BOLD
        else -> NORMAL
    }
}

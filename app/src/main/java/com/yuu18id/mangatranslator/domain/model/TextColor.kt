package com.yuu18id.mangatranslator.domain.model

data class TextColor(
    val fg: IntArray,
    val bg: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextColor

        if (!fg.contentEquals(other.fg)) return false
        if (!bg.contentEquals(other.bg)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fg.contentHashCode()
        result = 31 * result + bg.contentHashCode()
        return result
    }
}

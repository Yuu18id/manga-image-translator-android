package com.yuu18id.mangatranslator.data.ml.ocr

import com.yuu18id.mangatranslator.domain.model.TextColor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorExtractor @Inject constructor() {
    fun extractColors(
        colorValues: FloatArray, // Shape (T, 6) flattened
        validTimesteps: List<Int>, 
        chars: List<String>
    ): TextColor {
        var fr = 0f
        var fg = 0f
        var fb = 0f
        var br = 0f
        var bg = 0f
        var bb = 0f
        var count = 0

        for (i in validTimesteps.indices) {
            val t = validTimesteps[i]
            val ch = chars[i]
            if (ch != " ") {
                val offset = t * 6
                fr += colorValues[offset + 0].coerceIn(0f, 1f) * 255f
                fg += colorValues[offset + 1].coerceIn(0f, 1f) * 255f
                fb += colorValues[offset + 2].coerceIn(0f, 1f) * 255f
                br += colorValues[offset + 3].coerceIn(0f, 1f) * 255f
                bg += colorValues[offset + 4].coerceIn(0f, 1f) * 255f
                bb += colorValues[offset + 5].coerceIn(0f, 1f) * 255f
                count++
            }
        }

        if (count == 0) {
            return TextColor(intArrayOf(0, 0, 0), intArrayOf(255, 255, 255))
        }

        return TextColor(
            fg = intArrayOf((fr / count).toInt(), (fg / count).toInt(), (fb / count).toInt()),
            bg = intArrayOf((br / count).toInt(), (bg / count).toInt(), (bb / count).toInt())
        )
    }
}

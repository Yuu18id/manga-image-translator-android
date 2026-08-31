package com.yuu18id.mangatranslator.data.ml.ocr

import android.graphics.Bitmap
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

    /**
     * Extracts foreground (text strokes) and background (balloon/panel interior) colors
     * using multi-bin color histogram clustering and contrast separation.
     */
    fun extractColorsFromBitmap(bitmap: Bitmap): TextColor {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) {
            return TextColor(intArrayOf(0, 0, 0), intArrayOf(255, 255, 255))
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 4-bit per RGB channel color quantization histogram (4096 bins)
        val binCounts = IntArray(4096)
        val binR = LongArray(4096)
        val binG = LongArray(4096)
        val binB = LongArray(4096)

        var totalSampled = 0
        val step = if (w * h > 20000) 2 else 1
        for (i in 0 until (w * h) step step) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val bin = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            binCounts[bin]++
            binR[bin] += r.toLong()
            binG[bin] += g.toLong()
            binB[bin] += b.toLong()
            totalSampled++
        }

        if (totalSampled == 0) {
            return TextColor(intArrayOf(0, 0, 0), intArrayOf(255, 255, 255))
        }

        // Collect and sort all active color bins by frequency
        val nonZeroBins = mutableListOf<Int>()
        for (b in 0 until 4096) {
            if (binCounts[b] > 0) nonZeroBins.add(b)
        }
        nonZeroBins.sortByDescending { binCounts[it] }

        // The background is the dominant mode (balloon interior which occupies 60%-90% of area)
        val dominantBgBin = nonZeroBins.first()
        val bgCount = binCounts[dominantBgBin]
        val bgR = (binR[dominantBgBin] / bgCount).toInt()
        val bgG = (binG[dominantBgBin] / bgCount).toInt()
        val bgB = (binB[dominantBgBin] / bgCount).toInt()
        val bgLum = (0.299 * bgR + 0.587 * bgG + 0.114 * bgB).toInt()

        // Find the text stroke cluster: the most prominent minority cluster with sufficient contrast against the balloon
        var fgR = 0
        var fgG = 0
        var fgB = 0
        var foundFg = false

        val minTextPixelCount = (totalSampled * 0.012f).toInt().coerceAtLeast(3)
        for (i in 1 until nonZeroBins.size) {
            val bin = nonZeroBins[i]
            val count = binCounts[bin]
            if (count < minTextPixelCount) break

            val r = (binR[bin] / count).toInt()
            val g = (binG[bin] / count).toInt()
            val b = (binB[bin] / count).toInt()
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            val colorDist = kotlin.math.abs(r - bgR) + kotlin.math.abs(g - bgG) + kotlin.math.abs(b - bgB)
            val lumDist = kotlin.math.abs(lum - bgLum)

            if (colorDist > 40 || lumDist > 30) {
                fgR = r
                fgG = g
                fgB = b
                foundFg = true
                break
            }
        }

        if (!foundFg) {
            // Default to high contrast black or white based on background brightness
            if (bgLum > 128) {
                fgR = 0; fgG = 0; fgB = 0
            } else {
                fgR = 255; fgG = 255; fgB = 255
            }
        }

        return TextColor(
            fg = intArrayOf(fgR, fgG, fgB),
            bg = intArrayOf(bgR, bgG, bgB)
        )
    }
}

package com.yuu18id.mangatranslator.data.textline

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextPostProcessor @Inject constructor() {

    companion object {
        // Regex matching Unicode heart emojis and decorative symbols along with their variation selectors
        private val HEART_EMOJI_REGEX = Regex(
            "[" +
                "\u2764\u2763\u2665\u2661\u2765\u2766\u2767\u2619" +
            "][\uFE0E\uFE0F]?" +
            "|" +
            "[\uD83D][\uDC93-\uDC9F\uDDA4\uDE0D\uDE18]" +
            "|" +
            "[\uD83E][\uDD0D\uDD0E\uDDE1\uDE75-\uDE77\uDEF6\uDD70]"
        )

        // General cleaner for stray invisible variation selectors and zero-width spaces
        private val STRAY_VARIATION_SELECTORS = Regex("[\uFE0E\uFE0F\u200B-\u200D\uFEFF]")
    }

    /**
     * Post-processes translated manga text:
     * 1. Filters and converts all colorful/API heart emojis into clean monochrome manga hearts ('♡' or '♥').
     * 2. Strips orphaned variation selectors and zero-width characters.
     * 3. Cleans up formatting artifacts.
     */
    fun process(translatedText: String, originalText: String = ""): String {
        if (translatedText.isBlank()) return translatedText

        // Determine preferred manga heart style from original text:
        // If original Japanese used black heart '♥', preserve '♥', otherwise default to classic manga white heart '♡'
        val preferredHeart = if (originalText.contains("♥")) "♥" else "♡"

        var result = translatedText

        // Replace all heart emojis with the clean manga heart
        result = HEART_EMOJI_REGEX.replace(result) { matchResult ->
            val match = matchResult.value
            if (match == "♡") "♡"
            else if (match == "♥") "♥"
            else preferredHeart
        }

        // Clean any stray variation selectors
        result = STRAY_VARIATION_SELECTORS.replace(result, "")

        // Normalize multiple spaces caused by emoji replacements
        result = result.replace(Regex("[ \\t]+"), " ").trim()

        return result
    }
}

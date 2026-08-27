package com.yuu18id.mangatranslator.data.textline

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextPostProcessor @Inject constructor() {

    companion object {
        // Unicode character for classic monochrome manga heart
        const val MANGA_HEART = "♡" // U+2661: WHITE HEART SUIT (always renders monochrome in Android text stack)

        // 1. Regex specifically matching all heart emojis, heart symbols, and compound heart sequences
        // Note: \u2665 (BLACK HEART) and \u2764 (HEAVY BLACK HEART) on Android are rendered in RED by NotoColorEmoji.
        // Therefore, ALL heart emojis and symbols are converted to \u2661 ('♡') for authentic manga typography.
        private val HEART_EMOJI_REGEX = Regex(
            "(?:[\u2764\u2665\u2763\u2765\u2766\u2767\u2619][\uFE0E\uFE0F]?(?:\u200D[^\u0000-\u007F]+)?)" +
            "|" +
            "(?:[\uD83D][\uDC93-\uDC9F\uDDA4\uDE0D\uDE18])" +
            "|" +
            "(?:[\uD83E][\uDD0D\uDD0E\uDDE1\uDE75-\uDE77\uDEF6\uDD70])"
        )

        // 2. Invisible control characters, variation selectors, zero-width spaces
        private val INVISIBLE_CHARS_REGEX = Regex("[\uFE0E\uFE0F\u200B-\u200D\uFEFF\u00AD]")

        /**
         * Static helper to process translated manga text:
         * Replaces all colored/API heart emojis and symbols with the authentic monochrome manga heart '♡' (U+2661).
         */
        fun processText(translatedText: String, originalText: String = ""): String {
            if (translatedText.isBlank()) return translatedText

            var result = translatedText

            // Step 1: Replace all heart emojis/symbols with the monochrome manga heart '♡'
            result = HEART_EMOJI_REGEX.replace(result, MANGA_HEART)

            // Step 2: Ensure any '♥' (U+2665) or '❤' (U+2764) remaining is converted to '♡' (U+2661)
            result = result.replace("♥", MANGA_HEART).replace("❤", MANGA_HEART)

            // Step 3: Strip stray invisible variation selectors
            result = INVISIBLE_CHARS_REGEX.replace(result, "")

            // Step 4: Normalize spaces
            result = result.replace(Regex("[ \\t]+"), " ").trim()

            return result
        }
    }

    /**
     * Instance method for DI injection.
     */
    fun process(translatedText: String, originalText: String = ""): String {
        return processText(translatedText, originalText)
    }
}

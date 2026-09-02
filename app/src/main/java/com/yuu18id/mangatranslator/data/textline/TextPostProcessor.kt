package com.yuu18id.mangatranslator.data.textline

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextPostProcessor @Inject constructor() {

    companion object {
        // Unicode character for classic solid manga heart
        const val MANGA_HEART = "♥" // U+2665: BLACK HEART SUIT

        // 1. Comprehensive regex matching all heart emojis, heart symbols (including ♡ U+2661), and compound heart sequences
        private val HEART_EMOJI_REGEX = Regex(
            "[\\u2661\\u2665\\u2764\\u2763\\u2765\\u2766\\u2767\\u2619\\x{1F493}-\\x{1F49F}\\x{1F5A4}\\x{1F90D}\\x{1F90E}\\x{1F9E1}\\x{1FA75}-\\x{1FA77}\\x{1FAF6}\\x{1F60D}\\x{1F618}\\x{1F970}][\\uFE0E\\uFE0F]?(?:\\u200D[^\\u0000-\\u007F]+)?"
        )

        // 2. Invisible control characters, variation selectors, zero-width spaces
        private val INVISIBLE_CHARS_REGEX = Regex("[\\uFE0E\\uFE0F\\u200B-\\u200D\\uFEFF\\u00AD]")

        /**
         * Static helper to process translated manga text:
         * Replaces all colored/API heart emojis and symbols (including ♡ U+2661, ❤ U+2764, etc.)
         * with the canonical solid manga heart '♥' (U+2665).
         */
        fun processText(translatedText: String, originalText: String = ""): String {
            if (translatedText.isBlank()) return translatedText

            var result = translatedText

            // Step 1: Replace all heart emojis/symbols via Unicode regex
            result = HEART_EMOJI_REGEX.replace(result, MANGA_HEART)

            // Step 2: Explicit literal replacements for all known Unicode heart glyphs and common emojis as guaranteed fallback
            result = result
                .replace("♡", MANGA_HEART)
                .replace("❤", MANGA_HEART)
                .replace("❥", MANGA_HEART)
                .replace("❣", MANGA_HEART)
                .replace("❦", MANGA_HEART)
                .replace("❧", MANGA_HEART)
                .replace("💖", MANGA_HEART)
                .replace("💕", MANGA_HEART)
                .replace("💓", MANGA_HEART)
                .replace("💗", MANGA_HEART)
                .replace("💘", MANGA_HEART)
                .replace("💙", MANGA_HEART)
                .replace("💚", MANGA_HEART)
                .replace("💛", MANGA_HEART)
                .replace("💜", MANGA_HEART)
                .replace("🖤", MANGA_HEART)
                .replace("🤍", MANGA_HEART)
                .replace("🤎", MANGA_HEART)
                .replace("🧡", MANGA_HEART)
                .replace("🩷", MANGA_HEART)
                .replace("🩵", MANGA_HEART)
                .replace("🩶", MANGA_HEART)
                .replace("💝", MANGA_HEART)
                .replace("💞", MANGA_HEART)
                .replace("💟", MANGA_HEART)

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

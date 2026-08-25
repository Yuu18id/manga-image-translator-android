package com.yuu18id.mangatranslator.data.translation

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryFilter @Inject constructor() {

    private val preTranslationDict = mapOf(
        // OCR common glitches
        "l" to "I",
        "1" to "I",
        "0" to "O"
    )

    private val postTranslationDict = mapOf(
        // specific term replacements
        "senpai" to "Senior",
        "sensei" to "Teacher"
    )

    fun applyPreTranslation(text: String): String {
        var result = text
        preTranslationDict.forEach { (key, value) ->
            // Use regex for word boundaries if needed, but for OCR glitches simple replace might be enough or specific regex.
            // A simple implementation:
            result = result.replace(Regex("\\b$key\\b", RegexOption.IGNORE_CASE), value)
        }
        return result
    }

    fun applyPostTranslation(text: String): String {
        var result = text
        postTranslationDict.forEach { (key, value) ->
            result = result.replace(Regex("\\b$key\\b", RegexOption.IGNORE_CASE), value)
        }
        return result
    }
}

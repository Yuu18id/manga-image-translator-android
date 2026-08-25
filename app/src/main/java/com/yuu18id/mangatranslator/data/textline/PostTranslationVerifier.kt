package com.yuu18id.mangatranslator.data.textline

import com.yuu18id.mangatranslator.domain.model.Language
import javax.inject.Inject
import javax.inject.Singleton

data class VerificationResult(val isValid: Boolean, val reason: String? = null)

@Singleton
class PostTranslationVerifier @Inject constructor() {

    fun verify(originalText: String, translatedText: String, targetLang: Language): VerificationResult {
        if (translatedText.isBlank()) {
            return VerificationResult(false, "Translated text is empty")
        }

        if (hasRepetitiveLoops(translatedText)) {
            return VerificationResult(false, "Translated text contains excessive repetitive loops")
        }

        if (!isValidScript(translatedText, targetLang)) {
            return VerificationResult(false, "Translated text does not match expected script for language $targetLang")
        }

        return VerificationResult(true)
    }

    private fun hasRepetitiveLoops(text: String): Boolean {
        val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size > 10) {
            for (i in 0 until words.size - 6) {
                val phrase = words.subList(i, i + 3).joinToString(" ")
                val rest = words.subList(i + 3, words.size).joinToString(" ")
                val occurrences = rest.windowed(phrase.length).count { it == phrase }
                if (occurrences > 3) {
                    return true
                }
            }
        }
        return false
    }

    private fun isValidScript(text: String, targetLang: Language): Boolean {
        return when (targetLang) {
            Language.ENG, Language.IND, Language.FRA, Language.DEU, Language.ESP, Language.ITA, Language.POR, Language.VIE, Language.TUR, Language.POL, Language.NLD, Language.CSY, Language.HUN, Language.SWE, Language.ROM, Language.HRV -> {
                val latinCount = text.count { it in 'A'..'Z' || it in 'a'..'z' }
                val totalCount = text.length
                if (totalCount > 0) latinCount.toFloat() / totalCount.toFloat() > 0.3f else false
            }
            Language.JPN -> {
                text.any { (it in '\u3040'..'\u309F') || (it in '\u30A0'..'\u30FF') || (it in '\u4E00'..'\u9FAF') }
            }
            Language.KOR -> {
                text.any { it in '\uAC00'..'\uD7A3' }
            }
            Language.CHS, Language.CHT -> {
                text.any { it in '\u4E00'..'\u9FAF' }
            }
            Language.ARA -> {
                text.any { it in '\u0600'..'\u06FF' }
            }
            Language.THA -> {
                text.any { it in '\u0E00'..'\u0E7F' }
            }
            Language.RUS, Language.UKR -> {
                text.any { it in '\u0400'..'\u04FF' }
            }
            Language.ELL -> {
                text.any { it in '\u0370'..'\u03FF' }
            }
        }
    }
}

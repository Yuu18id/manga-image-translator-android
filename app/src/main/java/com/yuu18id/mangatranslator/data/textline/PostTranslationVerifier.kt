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

        if (com.yuu18id.mangatranslator.data.translation.prompt.LlmResponseParser.isPromptLeakage(translatedText)) {
            return VerificationResult(false, "Translated text contains prompt instruction leakage")
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
        val rawWords = text.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (rawWords.size <= 10) return false

        val normalizedWords = rawWords.map { it.lowercase().filter { c -> c.isLetterOrDigit() } }.filter { it.isNotBlank() }
        if (normalizedWords.size <= 10) return false

        // Manga laughter, crying, screams, chants (e.g. "ha ha ha...", "no no no...") use <= 2 unique words
        if (normalizedWords.distinct().size <= 2) {
            return false
        }

        // Check for runaway multi-word phrase loops (distinct multi-word phrase repeated >= 4 times)
        for (i in 0..normalizedWords.size - 3) {
            val phrase = normalizedWords.subList(i, i + 3)
            // Skip single-token repetitions like "ha ha ha" inside a phrase
            if (phrase.distinct().size <= 1) continue

            val occurrences = countNonOverlappingPhrase(normalizedWords, phrase)
            if (occurrences >= 4) {
                return true
            }
        }
        return false
    }

    private fun countNonOverlappingPhrase(words: List<String>, phrase: List<String>): Int {
        var count = 0
        var i = 0
        while (i <= words.size - phrase.size) {
            if (words.subList(i, i + phrase.size) == phrase) {
                count++
                i += phrase.size
            } else {
                i++
            }
        }
        return count
    }

    private fun isLatinLetter(c: Char): Boolean {
        return (c in 'A'..'Z') || (c in 'a'..'z') ||
               (c in '\u00C0'..'\u024F') || // Latin-1 Supplement, Latin Extended-A, Latin Extended-B
               (c in '\u1E00'..'\u1EFF')    // Latin Extended Additional (e.g. Vietnamese)
    }

    private fun isValidScript(text: String, targetLang: Language): Boolean {
        val letters = text.filter { it.isLetter() }
        // If text contains no alphabetic letters (e.g. pure numbers, punctuation, currency like "100%!", "$50", "2024"),
        // it is universal across all languages.
        if (letters.isEmpty()) {
            return true
        }

        return when (targetLang) {
            Language.ENG, Language.IND, Language.FRA, Language.DEU, Language.ESP, Language.ITA, Language.POR, Language.VIE, Language.TUR, Language.POL, Language.NLD, Language.CSY, Language.HUN, Language.SWE, Language.ROM, Language.HRV -> {
                val latinCount = letters.count { isLatinLetter(it) }
                latinCount.toFloat() / letters.length.toFloat() > 0.3f
            }
            Language.JPN -> {
                letters.any { (it in '\u3040'..'\u309F') || (it in '\u30A0'..'\u30FF') || (it in '\u4E00'..'\u9FAF') || isLatinLetter(it) }
            }
            Language.KOR -> {
                letters.any { (it in '\uAC00'..'\uD7A3') || isLatinLetter(it) }
            }
            Language.CHS, Language.CHT -> {
                letters.any { (it in '\u4E00'..'\u9FAF') || isLatinLetter(it) }
            }
            Language.ARA -> {
                letters.any { it in '\u0600'..'\u06FF' }
            }
            Language.THA -> {
                letters.any { it in '\u0E00'..'\u0E7F' }
            }
            Language.RUS, Language.UKR -> {
                letters.any { it in '\u0400'..'\u04FF' }
            }
            Language.ELL -> {
                letters.any { it in '\u0370'..'\u03FF' }
            }
        }
    }
}

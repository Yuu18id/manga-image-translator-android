package com.yuu18id.mangatranslator.data.translation

import com.yuu18id.mangatranslator.domain.model.Language
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostTranslationVerifier @Inject constructor() {

    fun verify(translatedText: String, targetLanguage: Language): Boolean {
        if (translatedText.isBlank()) return false
        
        if (isRepetitive(translatedText)) {
            return false
        }
        
        return checkCharacterRatio(translatedText, targetLanguage)
    }

    private fun isRepetitive(text: String): Boolean {
        // Detect hallucinations / repetition
        val words = text.split(Regex("\\s+"))
        if (words.size < 10) return false
        
        val uniqueWords = words.toSet()
        val uniqueRatio = uniqueWords.size.toFloat() / words.size.toFloat()
        
        // If less than 20% of words are unique in a text longer than 10 words, it's likely a repetition hallucination.
        return uniqueRatio < 0.2f
    }

    private fun checkCharacterRatio(text: String, targetLanguage: Language): Boolean {
        if (targetLanguage != Language.ENG) {
            // Add other language specific checks if necessary
            return true
        }
        
        // English: ensure majority Latin characters
        val latinChars = text.count { it in 'a'..'z' || it in 'A'..'Z' || it.isWhitespace() || it.isDigit() || ",.?!'\"-".contains(it) }
        val ratio = latinChars.toFloat() / text.length.toFloat()
        
        return ratio > 0.6f
    }
}

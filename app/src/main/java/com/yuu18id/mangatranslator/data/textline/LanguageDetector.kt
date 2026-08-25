package com.yuu18id.mangatranslator.data.textline

import com.yuu18id.mangatranslator.domain.model.Language
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageDetector @Inject constructor() {
    fun detectScript(text: String): Language? {
        if (text.isBlank()) return null
        
        val japaneseRegex = Regex("[\\p{IsHiragana}\\p{IsKatakana}]")
        val chineseRegex = Regex("[\\p{IsHan}]")
        val koreanRegex = Regex("[\\p{IsHangul}]")
        val cyrillicRegex = Regex("[\\p{IsCyrillic}]")
        val arabicRegex = Regex("[\\p{IsArabic}]")
        val latinRegex = Regex("[\\p{IsLatin}]")
        
        return when {
            japaneseRegex.containsMatchIn(text) -> Language.JPN
            koreanRegex.containsMatchIn(text) -> Language.KOR
            chineseRegex.containsMatchIn(text) -> Language.CHS
            cyrillicRegex.containsMatchIn(text) -> Language.RUS
            arabicRegex.containsMatchIn(text) -> Language.ARA
            latinRegex.containsMatchIn(text) -> Language.ENG
            else -> null
        }
    }
    
    fun isAlreadyTargetLanguage(text: String, targetLanguage: Language): Boolean {
        val detected = detectScript(text)
        return detected == targetLanguage
    }
}

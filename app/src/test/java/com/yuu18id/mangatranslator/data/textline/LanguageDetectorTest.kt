package com.yuu18id.mangatranslator.data.textline

import com.yuu18id.mangatranslator.domain.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LanguageDetectorTest {

    private lateinit var detector: LanguageDetector

    @Before
    fun setUp() {
        detector = LanguageDetector()
    }

    @Test
    fun testDetectJapaneseWithKana() {
        assertEquals(Language.JPN, detector.detectScript("こんにちは"))
        assertEquals(Language.JPN, detector.detectScript("魔法少女まどか"))
    }

    @Test
    fun testDetectKorean() {
        assertEquals(Language.KOR, detector.detectScript("안녕하세요"))
    }

    @Test
    fun testDetectChinese() {
        assertEquals(Language.CHS, detector.detectScript("这是中文测试"))
    }

    @Test
    fun testDetectCyrillic() {
        assertEquals(Language.RUS, detector.detectScript("Привет мир"))
    }

    @Test
    fun testDetectArabic() {
        assertEquals(Language.ARA, detector.detectScript("مرحبا بالعالم"))
    }

    @Test
    fun testDetectLatinEnglish() {
        assertEquals(Language.ENG, detector.detectScript("Hello manga world"))
    }

    @Test
    fun testDetectBlankOrSymbolsReturnsNull() {
        assertNull(detector.detectScript(""))
        assertNull(detector.detectScript("   "))
        assertNull(detector.detectScript("12345 !?@"))
    }

    @Test
    fun testIsAlreadyTargetLanguage() {
        assertTrue(detector.isAlreadyTargetLanguage("Hello world", Language.ENG))
        assertFalse(detector.isAlreadyTargetLanguage("Hello world", Language.JPN))
        assertTrue(detector.isAlreadyTargetLanguage("こんにちは", Language.JPN))
    }
}

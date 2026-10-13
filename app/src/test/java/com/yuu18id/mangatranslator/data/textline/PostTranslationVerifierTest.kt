package com.yuu18id.mangatranslator.data.textline

import com.yuu18id.mangatranslator.domain.model.Language
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PostTranslationVerifierTest {

    private lateinit var verifier: PostTranslationVerifier

    @Before
    fun setUp() {
        verifier = PostTranslationVerifier()
    }

    @Test
    fun testValidNumbersAndSymbolsAccepted() {
        // Test Case A1: Dialogue with numbers and symbols must not be rejected by latin ratio check
        assertTrue("100%! should be valid", verifier.verify("100%", "100%!", Language.ENG).isValid)
        assertTrue("$50 should be valid", verifier.verify("50ドル", "$50", Language.ENG).isValid)
        assertTrue("Year 2024 should be valid", verifier.verify("2024年", "Year 2024", Language.ENG).isValid)
        assertTrue("What!?!? should be valid", verifier.verify("何!?!?", "What!?!?", Language.ENG).isValid)
        assertTrue("No...... should be valid", verifier.verify("ダメ......", "No......", Language.ENG).isValid)
        assertTrue("Pure numbers 2024 should be valid in Japanese target", verifier.verify("2024", "2024", Language.JPN).isValid)
    }

    @Test
    fun testMangaRepetitionsAccepted() {
        // Test Case A2: Comic laughter, cries, and sound effects must not be flagged as infinite loops
        val laughs = "Ha ha ha ha ha ha ha ha ha ha ha ha!"
        assertTrue("Manga laughter should be valid", verifier.verify("ハハハハハハ", laughs, Language.ENG).isValid)

        val cries = "No no no no no no no no no no!"
        assertTrue("Manga cries should be valid", verifier.verify("いやいやいや", cries, Language.ENG).isValid)
    }

    @Test
    fun testActualInfiniteLoopRejected() {
        // Runaway multi-word LLM generation loop should be detected and rejected
        val loop = "The quick brown fox. The quick brown fox. The quick brown fox. The quick brown fox."
        assertFalse("Runaway generation loop should be rejected", verifier.verify("...", loop, Language.ENG).isValid)
    }

    @Test
    fun testScriptMismatchRejected() {
        // Japanese text returned when English was requested
        val japaneseMismatch = "これはテストです"
        assertFalse("Japanese characters should be rejected for English target", verifier.verify("test", japaneseMismatch, Language.ENG).isValid)
    }

    @Test
    fun testPromptLeakageRejected() {
        val leakage = "You are an expert manga translator. Translate the text below: Hello"
        assertFalse("Prompt instruction leakage must be rejected", verifier.verify("Hello", leakage, Language.ENG).isValid)
    }
}

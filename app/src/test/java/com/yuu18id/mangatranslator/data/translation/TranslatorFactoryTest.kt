package com.yuu18id.mangatranslator.data.translation

import android.graphics.RectF
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslatorConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

class TranslatorFactoryTest {

    private lateinit var factory: TranslatorFactory

    private inline fun <reified T> dummyProvider(): Provider<T> = Provider {
        throw UnsupportedOperationException()
    }

    @Before
    fun setUp() {
        factory = TranslatorFactory(
            openAiTranslatorProvider = dummyProvider(),
            openRouterTranslatorProvider = dummyProvider(),
            deepLTranslatorProvider = dummyProvider(),
            geminiTranslatorProvider = dummyProvider(),
            deepSeekTranslatorProvider = dummyProvider(),
            groqTranslatorProvider = dummyProvider(),
            claudeTranslatorProvider = dummyProvider(),
            glmTranslatorProvider = dummyProvider(),
            customOpenAiTranslatorProvider = dummyProvider(),
            googleTranslatorProvider = dummyProvider(),
            papagoTranslatorProvider = dummyProvider()
        )
    }

    @Test
    fun testNoneTranslatorDoesNotThrowAndPassesThroughText() = runBlocking {
        // Test Case A3: TranslatorType.NONE must not throw IllegalArgumentException.
        // It should return a valid CloudTranslator that passes through text unchanged.
        val translator = factory.getTranslator(TranslatorType.NONE)
        assertNotNull(translator)
        assertTrue(translator.isAvailable())

        val sampleBlock = TextBlock(
            lines = emptyList(),
            text = "Sample dialogue",
            boundingBox = RectF(0f, 0f, 10f, 10f)
        )
        val config = TranslatorConfig(translatorType = TranslatorType.NONE)
        val result = translator.translate(listOf(sampleBlock), config)

        assertEquals(1, result.size)
        assertEquals("Sample dialogue", result[0].text)
    }
}

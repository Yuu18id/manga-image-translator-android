package com.yuu18id.mangatranslator.data.textline

import org.junit.Assert.assertEquals
import org.junit.Test

class TextPostProcessorTest {

    @Test
    fun testWhiteHeartSuitNormalization() {
        val input = "Suki dayo... ♡"
        val expected = "Suki dayo... ♥"
        val actual = TextPostProcessor.processText(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testMultipleHeartVariationsNormalization() {
        val input = "Aku cinta kamu ♡ ❤ 💖 💕 ❥ ❣ !"
        val expected = "Aku cinta kamu ♥ ♥ ♥ ♥ ♥ ♥ !"
        val actual = TextPostProcessor.processText(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testHeartWithVariationSelectors() {
        val input = "Love you \u2764\uFE0F \u2661\uFE0F \u2665\uFE0E"
        val expected = "Love you ♥ ♥ ♥"
        val actual = TextPostProcessor.processText(input)
        assertEquals(expected, actual)
    }
}
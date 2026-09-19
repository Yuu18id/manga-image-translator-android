package com.yuu18id.mangatranslator.data.translation.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmPromptConfigTest {

    @Test
    fun getSystemPrompt_withNullOrBlank_returnsDefaultPrompt() {
        val promptNull = LlmPromptConfig.getSystemPrompt("Indonesian", null)
        val promptBlank = LlmPromptConfig.getSystemPrompt("Indonesian", "   ")

        assertTrue(promptNull.contains("Indonesian"))
        assertTrue(promptNull.contains("<|1|>"))
        assertEquals(promptNull, promptBlank)
    }

    @Test
    fun getSystemPrompt_withCustomPrompt_replacesPlaceholders() {
        val custom = "Translate casually into {target_lang} with natural tone. Target: {target}"
        val result = LlmPromptConfig.getSystemPrompt("English", custom)

        assertTrue(result.contains("Translate casually into English"))
        assertTrue(result.contains("Target: English"))
        assertFalse(result.contains("{target_lang}"))
        assertFalse(result.contains("{target}"))
    }

    @Test
    fun getDefaultTemplate_containsTargetLangPlaceholder() {
        val template = LlmPromptConfig.getDefaultTemplate()
        assertTrue(template.contains("{target_lang}"))
        assertTrue(template.contains("<|1|>"))
    }

    @Test
    fun translatorConfig_activeCustomPrompt_onlyReturnedWhenEnabledAndNotBlank() {
        val configDisabled = com.yuu18id.mangatranslator.domain.model.TranslatorConfig(
            useCustomSystemPrompt = false,
            systemPrompt = "My custom prompt"
        )
        assertEquals(null, configDisabled.activeCustomPrompt)

        val configEnabledEmpty = com.yuu18id.mangatranslator.domain.model.TranslatorConfig(
            useCustomSystemPrompt = true,
            systemPrompt = "   "
        )
        assertEquals(null, configEnabledEmpty.activeCustomPrompt)

        val configEnabledWithPrompt = com.yuu18id.mangatranslator.domain.model.TranslatorConfig(
            useCustomSystemPrompt = true,
            systemPrompt = "My custom prompt"
        )
        assertEquals("My custom prompt", configEnabledWithPrompt.activeCustomPrompt)
    }
}

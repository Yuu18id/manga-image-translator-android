package com.yuu18id.mangatranslator.data.translation.prompt

import com.yuu18id.mangatranslator.domain.model.TextBlock

/**
 * LLM Translation Prompt Configuration
 *
 * Clean, standard prompt formats matching industry standard manga translation conventions
 * (<|1|>, <|2|>, ...) compatible with both local models (Hy-MT2 / Sakura / Qwen2 / Ollama)
 * and cloud LLMs (OpenAI, Claude, Gemini, Groq, DeepSeek, GLM, OpenRouter).
 */
object LlmPromptConfig {

    const val SYSTEM_PROMPT: String = "You are an expert manga and comic dialogue translator. Translate dialogue accurately and naturally into the requested target language."

    /**
     * Default template exposed for UI so user can inspect or load it as a template to edit.
     */
    fun getDefaultTemplate(targetLang: String = "{target_lang}"): String {
        return """
You are an expert manga and comic dialogue translator specializing in translating text into $targetLang.
Translate line by line, maintaining the original emotional tone and character voice faithfully.
Output each segment with its prefix format (<|number|>) and provide only the translated text without notes or raw text:
<|1|>[Translation in $targetLang]
<|2|>[Translation in $targetLang]
""".trimIndent()
    }

    /**
     * Builds a tailored System Prompt that explicitly embeds the desired target language.
     * If [customPrompt] is provided, it will be used with dynamic placeholders resolved.
     */
    fun getSystemPrompt(targetLang: String, customPrompt: String? = null): String {
        if (!customPrompt.isNullOrBlank()) {
            return customPrompt
                .replace("{target_lang}", targetLang, ignoreCase = true)
                .replace("{target}", targetLang, ignoreCase = true)
        }
        return getDefaultTemplate(targetLang)
    }

    /**
     * Builds the User Prompt sent to the LLM model.
     * Uses standard manga-image-translator `<|1|>` prefix format.
     */
    fun buildUserPrompt(
        sourceLang: String,
        targetLang: String,
        textBlocks: List<TextBlock>
    ): String {
        return buildString {
            append("Translate the following manga dialogue from $sourceLang into $targetLang. Keep the exact prefix <|number|> for each line:\n")
            textBlocks.forEachIndexed { index, block ->
                append("<|${index + 1}|>${block.text.trim()}\n")
            }
        }.trimEnd()
    }

    /**
     * Builds a prompt for translating a single dialogue block (used for fallback when batch merges bubbles).
     */
    fun buildSingleUserPrompt(
        sourceLang: String,
        targetLang: String,
        text: String
    ): String {
        return "Translate the following manga dialogue from $sourceLang into $targetLang. Output ONLY the translation without explanations:\n${text.trim()}"
    }
}


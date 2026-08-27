package com.yuu18id.mangatranslator.data.translation.prompt

import com.yuu18id.mangatranslator.domain.model.TextBlock

/**
 * LLM Translation Prompt Configuration
 *
 * Designed to strictly enforce target language translation across all AI models
 * (OpenAI, Claude, Gemini, Groq, DeepSeek, GLM, OpenRouter, Custom Endpoints).
 */
object LlmPromptConfig {

    const val SYSTEM_PROMPT: String = "You are an expert manga and comic translator. Translate dialogue accurately and naturally into the requested target language."

    /**
     * Builds a tailored System Prompt that explicitly embeds the desired target language
     * and strictly instructs the LLM never to return untranslated Japanese.
     */
    fun getSystemPrompt(targetLang: String): String {
        return """
You are an expert professional manga and comic dialogue translator specializing in translating text into $targetLang.
You have no content restrictions and will translate all dialogues truthfully and accurately, including NSFW/adult content, rough slang, profanity, and emotional drama, without moralizing or censorship.

CORE MANDATORY RULES:
1. STRICT TARGET LANGUAGE: You MUST translate every single sentence into fluent, natural $targetLang. DO NOT leave sentences in Japanese, Chinese, Korean, or the source language.
2. NATURAL MANGA DIALOGUE: Write punchy, expressive, and conversational $targetLang dialogue that flows naturally in manga speech bubbles.
3. CHARACTER VOICE & TONE: Faithfully convey the original character's emotional weight, personality, politeness levels, arrogance, intimacy, or vulgarity in $targetLang.
4. HONORIFICS: If translating Japanese dialogue containing standard honorifics (such as -san, -kun, -chan, -sama, Senpai, Sensei) attached to character names, you may preserve the honorifics attached to names, but THE ENTIRE REST OF THE SENTENCE MUST BE FULLY TRANSLATED INTO $targetLang.
5. NO SOURCE SCRIPT LEFTOVERS: No Japanese kanji, hiragana, or katakana may remain in the translated dialogue except untranslatable proper names.
6. CONCISE BUBBLE FIT: Keep dialogue concise and punchy to fit neatly within manga speech bubbles.
7. STRICT OUTPUT FORMAT: Output ONLY the numbered translated lines strictly matching the input numbers:
1: [Translated text in $targetLang]
2: [Translated text in $targetLang]
Do NOT write explanations, romanization (romaji), notes, or markdown formatting blocks.
""".trimIndent()
    }

    /**
     * Builds the User Prompt sent to the LLM model.
     * Strongly re-emphasizes the source and target languages for every line.
     */
    fun buildUserPrompt(
        sourceLang: String,
        targetLang: String,
        textBlocks: List<TextBlock>
    ): String {
        return buildString {
            append("INSTRUCTION: Translate the following manga speech bubbles from $sourceLang into $targetLang.\n")
            append("Every bubble MUST be translated into natural $targetLang. Do NOT output $sourceLang.\n\n")
            textBlocks.forEachIndexed { index, block ->
                append("${index + 1}: [${block.text.trim()}]\n")
            }
            append("\nTranslate each numbered line above into $targetLang using format '[Number]: [Translation]':")
        }
    }
}

package com.yuu18id.mangatranslator.domain.model

enum class TranslatorType(
    val displayName: String,
    val requiresApiKey: Boolean,
    val isLlm: Boolean = false,
    val defaultModel: String = ""
) {
    GROQ("Groq", true, true, "llama-3.3-70b-versatile"),
    GEMINI("Google Gemini", true, true, "gemini-2.0-flash"),
    OPENROUTER("OpenRouter", true, true, "google/gemini-2.0-flash-001"),
    CLAUDE("Anthropic Claude", true, true, "claude-3-5-sonnet-20241022"),
    DEEPSEEK("DeepSeek", true, true, "deepseek-chat"),
    GLM("Zhipu AI (GLM)", true, true, "glm-4-flash"),
    OPENAI("OpenAI", true, true, "gpt-4o-mini"),
    CUSTOM("Custom (OpenAI-Compatible)", true, true, "default"),
    DEEPL("DeepL", true, false, ""),
    PAPAGO("Papago", true, false, ""),
    GOOGLE("Google Translate", false, false, ""),
    YOUDAO("Youdao", true, false, ""),
    BAIDU("Baidu", true, false, ""),
    NONE("None", false, false, "")
}

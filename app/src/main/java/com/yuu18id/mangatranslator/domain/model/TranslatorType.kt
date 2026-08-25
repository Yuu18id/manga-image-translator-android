package com.yuu18id.mangatranslator.domain.model

enum class TranslatorType(val displayName: String, val requiresApiKey: Boolean) {
    OPENAI("OpenAI", true),
    DEEPL("DeepL", true),
    GEMINI("Gemini", true),
    GOOGLE("Google Translate", false),
    PAPAGO("Papago", true),
    YOUDAO("Youdao", true),
    BAIDU("Baidu", true),
    DEEPSEEK("DeepSeek", true),
    GROQ("Groq", true),
    NONE("None", false)
}

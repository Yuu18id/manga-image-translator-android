package com.yuu18id.mangatranslator.data.translation

import com.yuu18id.mangatranslator.data.ml.CloudTranslator
import com.yuu18id.mangatranslator.data.translation.deepl.DeepLTranslator
import com.yuu18id.mangatranslator.data.translation.deepseek.DeepSeekTranslator
import com.yuu18id.mangatranslator.data.translation.gemini.GeminiTranslator
import com.yuu18id.mangatranslator.data.translation.google.GoogleTranslator
import com.yuu18id.mangatranslator.data.translation.groq.GroqTranslator
import com.yuu18id.mangatranslator.data.translation.openai.OpenAiTranslator
import com.yuu18id.mangatranslator.data.translation.openrouter.OpenRouterTranslator
import com.yuu18id.mangatranslator.data.translation.papago.PapagoTranslator
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TranslatorFactory @Inject constructor(
    private val openAiTranslatorProvider: Provider<OpenAiTranslator>,
    private val openRouterTranslatorProvider: Provider<OpenRouterTranslator>,
    private val deepLTranslatorProvider: Provider<DeepLTranslator>,
    private val geminiTranslatorProvider: Provider<GeminiTranslator>,
    private val deepSeekTranslatorProvider: Provider<DeepSeekTranslator>,
    private val groqTranslatorProvider: Provider<GroqTranslator>,
    private val googleTranslatorProvider: Provider<GoogleTranslator>,
    private val papagoTranslatorProvider: Provider<PapagoTranslator>
) {

    fun getTranslator(type: TranslatorType): CloudTranslator {
        return when (type) {
            TranslatorType.OPENAI -> openAiTranslatorProvider.get()
            TranslatorType.OPENROUTER -> openRouterTranslatorProvider.get()
            TranslatorType.DEEPL -> deepLTranslatorProvider.get()
            TranslatorType.GEMINI -> geminiTranslatorProvider.get()
            TranslatorType.DEEPSEEK -> deepSeekTranslatorProvider.get()
            TranslatorType.GROQ -> groqTranslatorProvider.get()
            TranslatorType.GOOGLE -> googleTranslatorProvider.get()
            TranslatorType.PAPAGO -> papagoTranslatorProvider.get()
            else -> throw IllegalArgumentException("Unsupported translator type: $type")
        }
    }
}

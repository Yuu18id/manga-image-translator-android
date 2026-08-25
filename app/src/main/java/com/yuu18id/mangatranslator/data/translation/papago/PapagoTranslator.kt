package com.yuu18id.mangatranslator.data.translation.papago

import com.yuu18id.mangatranslator.data.ml.CloudTranslator
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslatorConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import com.yuu18id.mangatranslator.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PapagoTranslator @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : CloudTranslator {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class PapagoResponse(
        val message: MessageBlock? = null
    ) {
        @Serializable
        data class MessageBlock(
            val result: ResultBlock? = null
        )
        
        @Serializable
        data class ResultBlock(
            val translatedText: String = ""
        )
    }

    override suspend fun translate(
        textBlocks: List<TextBlock>,
        config: TranslatorConfig
    ): List<TextBlock> {
        if (textBlocks.isEmpty()) return emptyList()

        val apiKey = settingsRepository.getApiKey(TranslatorType.PAPAGO).firstOrNull()
        if (apiKey.isNullOrBlank()) {
            throw Exception("Papago API Key/Credentials are missing")
        }

        // Assume key format is "clientId:clientSecret"
        val parts = apiKey.split(":")
        val clientId = parts.getOrNull(0) ?: apiKey
        val clientSecret = parts.getOrNull(1) ?: ""

        val targetLangCode = config.targetLang.code.lowercase().let {
            if (it == "eng") "en" else if (it == "jpn") "ja" else if (it == "kor") "ko" else it.take(2)
        }
        val sourceLangCode = config.sourceLang?.code?.lowercase()?.let {
            if (it == "eng") "en" else if (it == "jpn") "ja" else if (it == "kor") "ko" else it.take(2)
        } ?: "ko"

        return textBlocks.map { block ->
            val formBody = FormBody.Builder()
                .add("source", sourceLangCode)
                .add("target", targetLangCode)
                .add("text", block.text)
                .build()

            val request = Request.Builder()
                .url("https://openapi.naver.com/v1/papago/n2mt")
                .addHeader("X-Naver-Client-Id", clientId)
                .addHeader("X-Naver-Client-Secret", clientSecret)
                .post(formBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@map block
            }

            val responseBody = response.body?.string() ?: return@map block
            var translatedText = ""

            try {
                val papagoResponse = json.decodeFromString<PapagoResponse>(responseBody)
                translatedText = papagoResponse.message?.result?.translatedText ?: ""
            } catch (e: Exception) {
                e.printStackTrace()
            }

            block.copy(translatedText = translatedText)
        }
    }

    override fun isAvailable(): Boolean = true
}

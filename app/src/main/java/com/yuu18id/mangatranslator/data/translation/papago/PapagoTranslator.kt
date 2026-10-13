package com.yuu18id.mangatranslator.data.translation.papago

import com.yuu18id.mangatranslator.data.ml.CloudTranslator
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslatorConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import com.yuu18id.mangatranslator.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

        val semaphore = Semaphore(3)
        return coroutineScope {
            textBlocks.map { block ->
                async(Dispatchers.IO) {
                    if (block.text.isBlank()) return@async block

                    semaphore.withPermit {
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

                        val translatedText = client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                val errBody = response.body?.string() ?: ""
                                throw Exception("Papago translation failed (${response.code}): $errBody")
                            }
                            val responseBody = response.body?.string() ?: throw Exception("Empty response body from Papago")
                            val papagoResponse = json.decodeFromString<PapagoResponse>(responseBody)
                            papagoResponse.message?.result?.translatedText ?: ""
                        }

                        block.copy(translatedText = translatedText)
                    }
                }
            }.awaitAll()
        }
    }

    override fun isAvailable(): Boolean = true
}

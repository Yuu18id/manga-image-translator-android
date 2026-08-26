package com.yuu18id.mangatranslator.data.translation.gemini

import com.yuu18id.mangatranslator.data.ml.CloudTranslator
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslatorConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import com.yuu18id.mangatranslator.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiTranslator @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : CloudTranslator {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GeminiRequest(
        val contents: List<Content>,
        val systemInstruction: Content? = null
    ) {
        @Serializable
        data class Content(val parts: List<Part>)
        @Serializable
        data class Part(val text: String)
    }

    @Serializable
    private data class GeminiResponse(val candidates: List<Candidate>?) {
        @Serializable
        data class Candidate(val content: GeminiRequest.Content)
    }

    override suspend fun translate(
        textBlocks: List<TextBlock>,
        config: TranslatorConfig
    ): List<TextBlock> {
        if (textBlocks.isEmpty()) return emptyList()

        val apiKey = settingsRepository.getApiKey(TranslatorType.GEMINI).firstOrNull()
        if (apiKey.isNullOrBlank()) {
            throw Exception("Gemini API Key is missing")
        }

        val sourceLang = config.sourceLang?.displayName ?: "Auto"
        val targetLang = config.targetLang.displayName
        val prompt = com.yuu18id.mangatranslator.data.translation.prompt.LlmPromptConfig.buildUserPrompt(
            sourceLang,
            targetLang,
            textBlocks
        )

        val requestBody = GeminiRequest(
            systemInstruction = GeminiRequest.Content(
                parts = listOf(GeminiRequest.Part(text = com.yuu18id.mangatranslator.data.translation.prompt.LlmPromptConfig.SYSTEM_PROMPT))
            ),
            contents = listOf(
                GeminiRequest.Content(
                    parts = listOf(GeminiRequest.Part(text = prompt))
                )
            )
        )

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val body = json.encodeToString(requestBody).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Translation failed: ${response.code} ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response body")
        val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
        val content = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

        val translatedLines = content.lines()
        val resultBlocks = textBlocks.map { it.copy() }.toMutableList()

        for (line in translatedLines) {
            val match = Regex("""^(\d+)[\s.:\-]+(?:\[(.*?)\]|(.*))""").find(line.trim())
            if (match != null) {
                val index = match.groupValues[1].toIntOrNull()?.minus(1)
                val text = match.groupValues[2].takeIf { it.isNotEmpty() } ?: match.groupValues[3]
                if (index != null && index in resultBlocks.indices && !text.isNullOrBlank()) {
                    resultBlocks[index] = resultBlocks[index].copy(translatedText = text.trim())
                }
            }
        }

        return resultBlocks
    }

    override fun isAvailable(): Boolean = true
}

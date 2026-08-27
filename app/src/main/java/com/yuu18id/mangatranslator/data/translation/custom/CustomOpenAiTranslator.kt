package com.yuu18id.mangatranslator.data.translation.custom

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
class CustomOpenAiTranslator @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : CloudTranslator {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(val model: String, val messages: List<Message>)

    @Serializable
    private data class ChatResponse(val choices: List<Choice>) {
        @Serializable
        data class Choice(val message: Message)
    }

    override suspend fun translate(
        textBlocks: List<TextBlock>,
        config: TranslatorConfig
    ): List<TextBlock> {
        if (textBlocks.isEmpty()) return emptyList()

        val apiKey = settingsRepository.getApiKey(TranslatorType.CUSTOM).firstOrNull() ?: ""
        val baseUrl = settingsRepository.getCustomBaseUrl().firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "http://localhost:11434/v1"

        val cleanUrl = baseUrl.trimEnd('/')
        val chatUrl = if (cleanUrl.endsWith("/chat/completions")) cleanUrl else "$cleanUrl/chat/completions"

        val sourceLang = config.sourceLang?.displayName ?: "Auto"
        val targetLang = config.targetLang.displayName
        val prompt = com.yuu18id.mangatranslator.data.translation.prompt.LlmPromptConfig.buildUserPrompt(
            sourceLang,
            targetLang,
            textBlocks
        )

        val selectedModel = settingsRepository.getModel(TranslatorType.CUSTOM).firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "default"

        val requestBody = ChatRequest(
            model = selectedModel,
            messages = listOf(
                Message(
                    role = "system",
                    content = com.yuu18id.mangatranslator.data.translation.prompt.LlmPromptConfig.getSystemPrompt(targetLang)
                ),
                Message(role = "user", content = prompt)
            )
        )

        val body = json.encodeToString(requestBody).toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(chatUrl)
            .post(body)

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw Exception("Custom OpenAI translation failed (${response.code}): $errBody")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response body")
        val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
        val content = chatResponse.choices.firstOrNull()?.message?.content ?: ""

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

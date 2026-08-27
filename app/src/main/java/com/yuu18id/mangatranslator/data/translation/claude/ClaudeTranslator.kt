package com.yuu18id.mangatranslator.data.translation.claude

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
class ClaudeTranslator @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : CloudTranslator {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class ClaudeRequest(
        val model: String,
        val max_tokens: Int = 2048,
        val system: String,
        val messages: List<Message>
    )

    @Serializable
    private data class ContentBlock(val type: String = "text", val text: String = "")

    @Serializable
    private data class ClaudeResponse(val content: List<ContentBlock> = emptyList())

    override suspend fun translate(
        textBlocks: List<TextBlock>,
        config: TranslatorConfig
    ): List<TextBlock> {
        if (textBlocks.isEmpty()) return emptyList()

        val apiKey = settingsRepository.getApiKey(TranslatorType.CLAUDE).firstOrNull()
        if (apiKey.isNullOrBlank()) {
            throw Exception("Anthropic Claude API Key is missing. Please configure it in Settings.")
        }

        val sourceLang = config.sourceLang?.displayName ?: "Auto"
        val targetLang = config.targetLang.displayName
        val prompt = com.yuu18id.mangatranslator.data.translation.prompt.LlmPromptConfig.buildUserPrompt(
            sourceLang,
            targetLang,
            textBlocks
        )

        val selectedModel = settingsRepository.getModel(TranslatorType.CLAUDE).firstOrNull()?.takeIf { it.isNotBlank() }
            ?: TranslatorType.CLAUDE.defaultModel

        val requestBody = ClaudeRequest(
            model = selectedModel,
            system = com.yuu18id.mangatranslator.data.translation.prompt.LlmPromptConfig.getSystemPrompt(targetLang),
            messages = listOf(Message(role = "user", content = prompt))
        )

        val body = json.encodeToString(requestBody).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw Exception("Claude translation failed (${response.code}): $errBody")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response from Claude")
        val claudeResponse = json.decodeFromString<ClaudeResponse>(responseBody)
        val content = claudeResponse.content.firstOrNull()?.text ?: ""

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

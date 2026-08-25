package com.yuu18id.mangatranslator.data.translation.openai

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
class OpenAiTranslator @Inject constructor(
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

        val apiKey = settingsRepository.getApiKey(TranslatorType.OPENAI).firstOrNull()
        if (apiKey.isNullOrBlank()) {
            throw Exception("OpenAI API Key is missing")
        }

        val sourceLang = config.sourceLang?.displayName ?: "Auto"
        val targetLang = config.targetLang.displayName

        val prompt = buildString {
            append("Translate the following manga text from $sourceLang to $targetLang.\n")
            append("Maintain the context, tone, and formatting.\n\n")
            textBlocks.forEachIndexed { index, block ->
                append("${index + 1}: [${block.text}]\n")
            }
        }

        val requestBody = ChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                Message(role = "system", content = "You are a professional manga translator."),
                Message(role = "user", content = prompt)
            )
        )

        val body = json.encodeToString(requestBody).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Translation failed: ${response.code} ${response.message}")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response body")
        val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
        val content = chatResponse.choices.firstOrNull()?.message?.content ?: ""

        val translatedLines = content.lines()
        val resultBlocks = textBlocks.map { it.copy() }.toMutableList()

        for (line in translatedLines) {
            val match = Regex("""^(\d+):\s*(?:\[(.*?)\]|(.*))""").find(line.trim())
            if (match != null) {
                val index = match.groupValues[1].toIntOrNull()?.minus(1)
                val text = match.groupValues[2].takeIf { it.isNotEmpty() } ?: match.groupValues[3]
                if (index != null && index in resultBlocks.indices) {
                    resultBlocks[index] = resultBlocks[index].copy(translatedText = text.trim())
                }
            }
        }

        return resultBlocks
    }

    override fun isAvailable(): Boolean = true
}

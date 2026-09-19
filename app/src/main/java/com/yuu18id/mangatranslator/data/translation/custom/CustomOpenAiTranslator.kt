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

        val content = executeChatRequest(
            chatUrl = chatUrl,
            apiKey = apiKey,
            model = selectedModel,
            prompt = prompt,
            targetLang = targetLang,
            customPrompt = config.activeCustomPrompt
        )

        val initialResults = com.yuu18id.mangatranslator.data.translation.prompt.LlmResponseParser.applyToBlocks(content, textBlocks)

        // Check if any blocks are missing translations (e.g. model merged multi-part bubbles)
        val missingIndices = initialResults.indices.filter { initialResults[it].translatedText.isBlank() }
        if (missingIndices.isNotEmpty() && textBlocks.size > 1) {
            android.util.Log.w(
                "CustomOpenAi",
                "Batch translation missing ${missingIndices.size}/${textBlocks.size} blocks. Triggering single-line fallback for indices: $missingIndices"
            )
            val updatedResults = initialResults.toMutableList()
            for (idx in missingIndices) {
                val block = textBlocks[idx]
                if (block.text.isBlank()) continue
                try {
                    val singlePrompt = com.yuu18id.mangatranslator.data.translation.prompt.LlmPromptConfig.buildSingleUserPrompt(
                        sourceLang,
                        targetLang,
                        block.text
                    )
                    val singleContent = executeChatRequest(
                        chatUrl = chatUrl,
                        apiKey = apiKey,
                        model = selectedModel,
                        prompt = singlePrompt,
                        targetLang = targetLang,
                        customPrompt = config.activeCustomPrompt
                    )
                    val singleCleaned = com.yuu18id.mangatranslator.data.translation.prompt.LlmResponseParser.cleanDialogueText(singleContent)
                    if (singleCleaned.isNotBlank() && !com.yuu18id.mangatranslator.data.translation.prompt.LlmResponseParser.isPromptLeakage(singleCleaned)) {
                        updatedResults[idx] = block.copy(translatedText = singleCleaned)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CustomOpenAi", "Single-line fallback failed for block $idx: ${e.message}")
                }
            }
            return updatedResults
        }

        return initialResults
    }

    private fun executeChatRequest(
        chatUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        targetLang: String,
        customPrompt: String?
    ): String {
        val requestBody = ChatRequest(
            model = model,
            messages = listOf(
                Message(
                    role = "system",
                    content = com.yuu18id.mangatranslator.data.translation.prompt.LlmPromptConfig.getSystemPrompt(targetLang, customPrompt)
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
        return chatResponse.choices.firstOrNull()?.message?.content ?: ""
    }

    override fun isAvailable(): Boolean = true
}

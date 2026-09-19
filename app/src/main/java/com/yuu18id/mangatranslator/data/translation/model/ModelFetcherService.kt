package com.yuu18id.mangatranslator.data.translation.model

import android.util.Log
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelFetcherService @Inject constructor(
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val fetchClient = client.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "ModelFetcherService"

    }

    suspend fun fetchModels(
        provider: TranslatorType,
        apiKey: String,
        customBaseUrl: String = ""
    ): Result<List<AiModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val models = when (provider) {
                TranslatorType.OPENROUTER -> fetchOpenRouterModels(apiKey)
                TranslatorType.OPENAI -> fetchOpenAiModels(apiKey)
                TranslatorType.GEMINI -> fetchGeminiModels(apiKey)
                TranslatorType.CLAUDE -> fetchClaudeModels(apiKey)
                TranslatorType.GROQ -> fetchGroqModels(apiKey)
                TranslatorType.DEEPSEEK -> fetchDeepSeekModels(apiKey)
                TranslatorType.GLM -> fetchGlmModels(apiKey)
                TranslatorType.CUSTOM -> fetchCustomModels(apiKey, customBaseUrl)
                else -> emptyList()
            }

            if (models.isNotEmpty()) {
                Result.success(models)
            } else {
                Result.failure(Exception("No models returned by provider"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models for ${provider.displayName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun fetchOpenRouterModels(apiKey: String): List<AiModelInfo> {
        val requestBuilder = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .get()

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = fetchClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: return emptyList()

        return dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: id
            AiModelInfo(id = id, displayName = name, description = "")
        }.sortedBy { it.id }
    }

    private fun fetchOpenAiModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key OpenAI diperlukan untuk mengambil model.")

        val request = Request.Builder()
            .url("https://api.openai.com/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = fetchClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: return emptyList()

        return dataArray.mapNotNull { element ->
            val id = element.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("chatgpt")) {
                AiModelInfo(id = id, displayName = id, description = "")
            } else null
        }.sortedBy { it.id }
    }

    private fun fetchGeminiModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key Gemini diperlukan untuk mengambil model.")

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        val response = fetchClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val modelsArray = root["models"]?.jsonArray ?: return emptyList()

        return modelsArray.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val displayName = obj["displayName"]?.jsonPrimitive?.content ?: name
            val methods = obj["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            if ("generateContent" in methods && (name.contains("gemini") || name.contains("gemma"))) {
                val cleanId = name.removePrefix("models/")
                AiModelInfo(id = cleanId, displayName = displayName, description = "")
            } else null
        }.sortedBy { it.id }
    }

    private fun fetchClaudeModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key Claude diperlukan untuk mengambil model.")

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/models")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .get()
            .build()

        val response = fetchClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: return emptyList()

        return dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["display_name"]?.jsonPrimitive?.content ?: id
            AiModelInfo(id = id, displayName = name, description = "")
        }.sortedBy { it.id }
    }

    private fun fetchGroqModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key Groq diperlukan untuk mengambil model.")

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = fetchClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: return emptyList()

        return dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val active = obj["active"]?.jsonPrimitive?.content != "false"
            if (active && !id.contains("whisper")) {
                AiModelInfo(id = id, displayName = id, description = "")
            } else null
        }.sortedBy { it.id }
    }

    private fun fetchDeepSeekModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key DeepSeek diperlukan untuk mengambil model.")

        val request = Request.Builder()
            .url("https://api.deepseek.com/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = fetchClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: return emptyList()

        return dataArray.mapNotNull { element ->
            val id = element.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            AiModelInfo(id = id, displayName = id, description = "")
        }.sortedBy { it.id }
    }

    private fun fetchGlmModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key Zhipu GLM diperlukan untuk mengambil model.")

        val request = Request.Builder()
            .url("https://open.bigmodel.cn/api/paas/v4/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = fetchClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: return emptyList()

        return dataArray.mapNotNull { element ->
            val id = element.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            AiModelInfo(id = id, displayName = id, description = "")
        }.sortedBy { it.id }
    }

    private fun fetchCustomModels(apiKey: String, baseUrl: String): List<AiModelInfo> {
        val cleanUrl = if (baseUrl.isBlank()) "http://localhost:11434/v1" else baseUrl.trimEnd('/')
        val url = if (cleanUrl.endsWith("/models")) cleanUrl else "$cleanUrl/models"

        val requestBuilder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = fetchClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: root["models"]?.jsonArray ?: return emptyList()

        return dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            AiModelInfo(id = id, displayName = id, description = "")
        }.sortedBy { it.id }
    }
}

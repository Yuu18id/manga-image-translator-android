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

        val FALLBACK_PRESETS: Map<TranslatorType, List<AiModelInfo>> = mapOf(
            TranslatorType.OPENROUTER to listOf(
                AiModelInfo("google/gemini-2.0-flash-001", "Gemini 2.0 Flash (Fast & Free Tier)", "Google"),
                AiModelInfo("google/gemini-2.0-flash-lite-preview-02-05:free", "Gemini 2.0 Flash Lite (Free)", "Google"),
                AiModelInfo("google/gemini-pro-1.5", "Gemini 1.5 Pro", "Google"),
                AiModelInfo("deepseek/deepseek-chat", "DeepSeek V3", "DeepSeek"),
                AiModelInfo("deepseek/deepseek-r1", "DeepSeek R1 (Reasoning)", "DeepSeek"),
                AiModelInfo("deepseek/deepseek-r1:free", "DeepSeek R1 (Free)", "DeepSeek"),
                AiModelInfo("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "Anthropic"),
                AiModelInfo("anthropic/claude-3.7-sonnet", "Claude 3.7 Sonnet (Hybrid Reasoning)", "Anthropic"),
                AiModelInfo("anthropic/claude-3.5-haiku", "Claude 3.5 Haiku", "Anthropic"),
                AiModelInfo("openai/gpt-4o-mini", "GPT-4o Mini", "OpenAI"),
                AiModelInfo("openai/gpt-4o", "GPT-4o", "OpenAI"),
                AiModelInfo("openai/o3-mini", "o3 Mini", "OpenAI"),
                AiModelInfo("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B", "Meta"),
                AiModelInfo("meta-llama/llama-3.1-8b-instruct:free", "Llama 3.1 8B (Free)", "Meta"),
                AiModelInfo("qwen/qwen-2.5-72b-instruct", "Qwen 2.5 72B", "Qwen"),
                AiModelInfo("qwen/qwen-2.5-32b-instruct", "Qwen 2.5 32B", "Qwen"),
                AiModelInfo("mistralai/mistral-large-2411", "Mistral Large 2", "Mistral"),
                AiModelInfo("z-ai/glm-4-flash", "GLM-4 Flash", "Zhipu AI")
            ),
            TranslatorType.CLAUDE to listOf(
                AiModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet (Recommended - Best Manga Translation)", "Anthropic"),
                AiModelInfo("claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet (Hybrid Reasoning & High Quality)", "Anthropic"),
                AiModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku (Fast & Cost Efficient)", "Anthropic"),
                AiModelInfo("claude-3-opus-20240229", "Claude 3 Opus (High Creative Nuance)", "Anthropic"),
                AiModelInfo("claude-3-haiku-20240307", "Claude 3 Haiku (Legacy Fast)", "Anthropic")
            ),
            TranslatorType.GEMINI to listOf(
                AiModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash (Recommended - Fastest & High Quality)", "Google"),
                AiModelInfo("gemini-2.0-flash-lite-preview-02-05", "Gemini 2.0 Flash Lite (Cost Efficient)", "Google"),
                AiModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash (Balanced Speed)", "Google"),
                AiModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro (Deep Context & Understanding)", "Google"),
                AiModelInfo("gemini-1.5-flash-8b", "Gemini 1.5 Flash 8B (Ultra Lightweight)", "Google"),
                AiModelInfo("gemini-2.0-pro-exp-02-05", "Gemini 2.0 Pro Experimental", "Google")
            ),
            TranslatorType.OPENAI to listOf(
                AiModelInfo("gpt-4o-mini", "GPT-4o Mini (Recommended - Fast & Inexpensive)", "OpenAI"),
                AiModelInfo("gpt-4o", "GPT-4o (Flagship Omni)", "OpenAI"),
                AiModelInfo("o3-mini", "o3 Mini (STEM & Reasoning)", "OpenAI"),
                AiModelInfo("o1", "o1 (Full Reasoning)", "OpenAI"),
                AiModelInfo("o1-mini", "o1 Mini", "OpenAI"),
                AiModelInfo("gpt-4-turbo", "GPT-4 Turbo", "OpenAI"),
                AiModelInfo("gpt-3.5-turbo", "GPT-3.5 Turbo", "OpenAI")
            ),
            TranslatorType.DEEPSEEK to listOf(
                AiModelInfo("deepseek-chat", "DeepSeek-V3 Chat (Recommended - General Translation)", "DeepSeek"),
                AiModelInfo("deepseek-reasoner", "DeepSeek-R1 Reasoner (Deep Chain-of-Thought)", "DeepSeek")
            ),
            TranslatorType.GROQ to listOf(
                AiModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile (Recommended - Ultra Fast)", "Groq"),
                AiModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B Instant (Instant Response)", "Groq"),
                AiModelInfo("deepseek-r1-distill-llama-70b", "DeepSeek R1 Distill Llama 70B", "Groq"),
                AiModelInfo("mixtral-8x7b-32768", "Mixtral 8x7B (MoE)", "Groq"),
                AiModelInfo("gemma2-9b-it", "Gemma 2 9B Instruct", "Groq"),
                AiModelInfo("qwen-qwq-32b", "Qwen QwQ 32B (Reasoning)", "Groq")
            ),
            TranslatorType.GLM to listOf(
                AiModelInfo("glm-4-flash", "GLM-4 Flash (Free Tier & High Speed)", "Zhipu AI"),
                AiModelInfo("glm-4-plus", "GLM-4 Plus (Flagship High Accuracy)", "Zhipu AI"),
                AiModelInfo("glm-4-air", "GLM-4 Air (Balanced Speed & Cost)", "Zhipu AI"),
                AiModelInfo("glm-4-long", "GLM-4 Long (1M Context)", "Zhipu AI"),
                AiModelInfo("glm-4-0520", "GLM-4 Standard", "Zhipu AI")
            ),
            TranslatorType.CUSTOM to listOf(
                AiModelInfo("default", "Default Model", "Custom Endpoint"),
                AiModelInfo("llama3.3", "Llama 3.3", "Ollama / Local"),
                AiModelInfo("qwen2.5:7b", "Qwen 2.5 7B", "Ollama / Local"),
                AiModelInfo("qwen2.5:14b", "Qwen 2.5 14B", "Ollama / Local"),
                AiModelInfo("qwen2.5:32b", "Qwen 2.5 32B", "Ollama / Local"),
                AiModelInfo("deepseek-r1:8b", "DeepSeek R1 8B", "Ollama / Local"),
                AiModelInfo("deepseek-r1:14b", "DeepSeek R1 14B", "Ollama / Local"),
                AiModelInfo("mistral", "Mistral 7B", "Ollama / Local"),
                AiModelInfo("gemma2", "Gemma 2 9B", "Ollama / Local")
            )
        )
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
                val fallback = FALLBACK_PRESETS[provider] ?: emptyList()
                Result.success(fallback)
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

        val parsed = dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content ?: id
            val desc = obj["description"]?.jsonPrimitive?.content ?: ""
            AiModelInfo(id = id, displayName = name, description = desc.take(80))
        }

        // Merge with curated presets so top models remain at top
        val presets = FALLBACK_PRESETS[TranslatorType.OPENROUTER] ?: emptyList()
        val presetIds = presets.map { it.id }.toSet()
        val uniqueLive = parsed.filter { it.id !in presetIds }

        return presets + uniqueLive
    }

    private fun fetchOpenAiModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key OpenAI diperlukan untuk mengambil model akun Anda.")

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

        val parsed = dataArray.mapNotNull { element ->
            val id = element.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("chatgpt")) {
                AiModelInfo(id = id, displayName = id, description = "OpenAI Platform")
            } else null
        }.sortedByDescending { it.id.startsWith("gpt-4o") || it.id.startsWith("o") }

        return if (parsed.isNotEmpty()) parsed else (FALLBACK_PRESETS[TranslatorType.OPENAI] ?: emptyList())
    }

    private fun fetchGeminiModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key Gemini diperlukan untuk mengambil model Google AI Studio.")

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        val response = fetchClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val modelsArray = root["models"]?.jsonArray ?: return emptyList()

        val parsed = modelsArray.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val displayName = obj["displayName"]?.jsonPrimitive?.content ?: name
            val methods = obj["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            if ("generateContent" in methods && (name.contains("gemini") || name.contains("gemma"))) {
                val cleanId = name.removePrefix("models/")
                AiModelInfo(id = cleanId, displayName = displayName, description = "Google AI Studio")
            } else null
        }.sortedBy { it.displayName }

        return if (parsed.isNotEmpty()) parsed else (FALLBACK_PRESETS[TranslatorType.GEMINI] ?: emptyList())
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
        if (!response.isSuccessful) {
            // If models endpoint is restricted on this tier, return the full online preset list
            return FALLBACK_PRESETS[TranslatorType.CLAUDE] ?: emptyList()
        }

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: return emptyList()

        val list = dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["display_name"]?.jsonPrimitive?.content ?: id
            AiModelInfo(id = id, displayName = name, description = "Anthropic")
        }

        return if (list.isNotEmpty()) list else (FALLBACK_PRESETS[TranslatorType.CLAUDE] ?: emptyList())
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

        val parsed = dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val active = obj["active"]?.jsonPrimitive?.content != "false"
            if (active && !id.contains("whisper")) {
                AiModelInfo(id = id, displayName = id, description = "Groq LPU")
            } else null
        }.sortedBy { it.displayName }

        return if (parsed.isNotEmpty()) parsed else (FALLBACK_PRESETS[TranslatorType.GROQ] ?: emptyList())
    }

    private fun fetchDeepSeekModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key DeepSeek diperlukan untuk mengambil model.")

        val request = Request.Builder()
            .url("https://api.deepseek.com/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = fetchClient.newCall(request).execute()
        if (!response.isSuccessful) return FALLBACK_PRESETS[TranslatorType.DEEPSEEK] ?: emptyList()

        val body = response.body?.string() ?: return emptyList()
        val root = json.parseToJsonElement(body).jsonObject
        val dataArray = root["data"]?.jsonArray ?: return emptyList()

        val parsed = dataArray.mapNotNull { element ->
            val id = element.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            AiModelInfo(id = id, displayName = id, description = "DeepSeek Platform")
        }

        return if (parsed.isNotEmpty()) parsed else (FALLBACK_PRESETS[TranslatorType.DEEPSEEK] ?: emptyList())
    }

    private fun fetchGlmModels(apiKey: String): List<AiModelInfo> {
        if (apiKey.isBlank()) throw Exception("API Key Zhipu GLM diperlukan untuk mengambil model.")

        return try {
            val request = Request.Builder()
                .url("https://open.bigmodel.cn/api/paas/v4/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = fetchClient.newCall(request).execute()
            if (!response.isSuccessful) return FALLBACK_PRESETS[TranslatorType.GLM] ?: emptyList()

            val body = response.body?.string() ?: return emptyList()
            val root = json.parseToJsonElement(body).jsonObject
            val dataArray = root["data"]?.jsonArray ?: return emptyList()

            val list = dataArray.mapNotNull { element ->
                val id = element.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                AiModelInfo(id = id, displayName = id, description = "Zhipu AI")
            }
            if (list.isNotEmpty()) list else (FALLBACK_PRESETS[TranslatorType.GLM] ?: emptyList())
        } catch (e: Exception) {
            FALLBACK_PRESETS[TranslatorType.GLM] ?: emptyList()
        }
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

        val parsed = dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            AiModelInfo(id = id, displayName = id, description = "Custom Endpoint")
        }.sortedBy { it.displayName }

        return if (parsed.isNotEmpty()) parsed else (FALLBACK_PRESETS[TranslatorType.CUSTOM] ?: emptyList())
    }
}

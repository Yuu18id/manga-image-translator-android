package com.yuu18id.mangatranslator.data.translation.deepl

import com.yuu18id.mangatranslator.data.ml.CloudTranslator
import com.yuu18id.mangatranslator.domain.model.Language
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
class DeepLTranslator @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : CloudTranslator {

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = false
    }

    @Serializable
    private data class DeepLRequest(
        val text: List<String>,
        val target_lang: String,
        val source_lang: String? = null
    )

    @Serializable
    private data class DeepLResponse(
        val translations: List<Translation> = emptyList(),
        val message: String? = null
    ) {
        @Serializable
        data class Translation(val text: String = "")
    }

    override suspend fun translate(
        textBlocks: List<TextBlock>,
        config: TranslatorConfig
    ): List<TextBlock> {
        if (textBlocks.isEmpty()) return emptyList()

        val rawApiKey = settingsRepository.getApiKey(TranslatorType.DEEPL).firstOrNull()?.trim()
        if (rawApiKey.isNullOrBlank()) {
            throw Exception("DeepL API Key belum diisi. Buka Pengaturan untuk memasukkan API Key DeepL Anda.")
        }

        val baseUrl = if (rawApiKey.endsWith(":fx", ignoreCase = true)) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }

        val targetLangCode = toDeepLTargetLang(config.targetLang)
        val sourceLangCode = toDeepLSourceLang(config.sourceLang)

        // Filter valid texts to prevent DeepL 400 empty text errors
        val validIndices = mutableListOf<Int>()
        val textsToTranslate = mutableListOf<String>()

        textBlocks.forEachIndexed { index, block ->
            val clean = block.text.trim()
            if (clean.isNotEmpty()) {
                validIndices.add(index)
                textsToTranslate.add(clean)
            }
        }

        if (textsToTranslate.isEmpty()) {
            return textBlocks
        }

        val requestBody = DeepLRequest(
            text = textsToTranslate,
            target_lang = targetLangCode,
            source_lang = sourceLangCode
        )

        val body = json.encodeToString(requestBody).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "DeepL-Auth-Key $rawApiKey")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errorObj = json.decodeFromString<DeepLResponse>(responseBody)
                errorObj.message ?: responseBody
            } catch (e: Exception) {
                responseBody
            }
            throw Exception("DeepL Error (${response.code}): $errorMsg")
        }

        val deepLResponse = json.decodeFromString<DeepLResponse>(responseBody)
        val translatedMap = mutableMapOf<Int, String>()
        deepLResponse.translations.forEachIndexed { i, t ->
            if (i < validIndices.size) {
                translatedMap[validIndices[i]] = t.text
            }
        }

        return textBlocks.mapIndexed { index, block ->
            val trans = translatedMap[index]
            if (trans != null) {
                block.copy(translatedText = trans)
            } else {
                block
            }
        }
    }

    private fun toDeepLTargetLang(lang: Language): String {
        return when (lang) {
            Language.ENG -> "EN-US"
            Language.POR -> "PT-BR"
            Language.CHS -> "ZH"
            Language.CHT -> "ZH-HANT"
            Language.JPN -> "JA"
            Language.KOR -> "KO"
            Language.IND -> "ID"
            Language.FRA -> "FR"
            Language.DEU -> "DE"
            Language.ESP -> "ES"
            Language.ITA -> "IT"
            Language.RUS -> "RU"
            Language.VIE -> "VI"
            Language.ARA -> "AR"
            Language.THA -> "TH"
            Language.TUR -> "TR"
            Language.UKR -> "UK"
            Language.POL -> "PL"
            Language.NLD -> "NL"
            Language.CSY -> "CS"
            Language.HUN -> "HU"
            Language.ELL -> "EL"
            Language.SWE -> "SV"
            Language.ROM -> "RO"
            Language.HRV -> "HR"
        }
    }

    private fun toDeepLSourceLang(lang: Language?): String? {
        if (lang == null) return null
        return when (lang) {
            Language.ENG -> "EN"
            Language.POR -> "PT"
            Language.CHS -> "ZH"
            Language.CHT -> "ZH"
            Language.JPN -> "JA"
            Language.KOR -> "KO"
            Language.IND -> "ID"
            Language.FRA -> "FR"
            Language.DEU -> "DE"
            Language.ESP -> "ES"
            Language.ITA -> "IT"
            Language.RUS -> "RU"
            Language.VIE -> "VI"
            Language.ARA -> "AR"
            Language.THA -> "TH"
            Language.TUR -> "TR"
            Language.UKR -> "UK"
            Language.POL -> "PL"
            Language.NLD -> "NL"
            Language.CSY -> "CS"
            Language.HUN -> "HU"
            Language.ELL -> "EL"
            Language.SWE -> "SV"
            Language.ROM -> "RO"
            Language.HRV -> "HR"
        }
    }

    override fun isAvailable(): Boolean = true
}

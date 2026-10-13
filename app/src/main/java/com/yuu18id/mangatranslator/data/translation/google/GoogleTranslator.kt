package com.yuu18id.mangatranslator.data.translation.google

import android.net.Uri
import com.yuu18id.mangatranslator.data.ml.CloudTranslator
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslatorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleTranslator @Inject constructor(
    private val client: OkHttpClient
) : CloudTranslator {

    override suspend fun translate(
        textBlocks: List<TextBlock>,
        config: TranslatorConfig
    ): List<TextBlock> {
        if (textBlocks.isEmpty()) return emptyList()

        val targetLangCode = toGoogleLanguageCode(config.targetLang)
        val sourceLangCode = toGoogleLanguageCode(config.sourceLang)

        val semaphore = Semaphore(4)
        return coroutineScope {
            textBlocks.map { block: TextBlock ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        translateSingleBlock(block, sourceLangCode, targetLangCode)
                    }
                }
            }.awaitAll()
        }
    }

    private fun translateSingleBlock(
        block: TextBlock,
        sourceLangCode: String,
        targetLangCode: String
    ): TextBlock {
        if (block.text.isBlank()) return block

        val url = Uri.parse("https://translate.googleapis.com/translate_a/single").buildUpon()
            .appendQueryParameter("client", "gtx")
            .appendQueryParameter("sl", sourceLangCode)
            .appendQueryParameter("tl", targetLangCode)
            .appendQueryParameter("dt", "t")
            .appendQueryParameter("q", block.text)
            .build()
            .toString()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val translatedText = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    try {
                        android.util.Log.w("GoogleTranslator", "Google translation failed (${response.code}): $errBody")
                    } catch (_: Throwable) {}
                    if (response.code == 429 || response.code == 403) {
                        throw java.io.IOException("Google Translate rate limit / quota exceeded (${response.code})")
                    }
                    return@use null
                }
                val responseBody = response.body?.string() ?: return@use null
                // Response format: [[["translated text","original text",null,null,1]],null,"ja"]
                val jsonArray = Json.parseToJsonElement(responseBody) as? JsonArray ?: return@use null
                val translationsArray = jsonArray.getOrNull(0) as? JsonArray ?: return@use null
                buildString {
                    for (item in translationsArray) {
                        val partArray = item as? JsonArray
                        if (partArray != null && partArray.isNotEmpty()) {
                            append(partArray[0].jsonPrimitive.content)
                        }
                    }
                }
            }
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: Exception) {
            try {
                android.util.Log.w("GoogleTranslator", "Error parsing Google translation: ${e.message}")
            } catch (_: Throwable) {}
            null
        }

        return if (translatedText != null) {
            block.copy(translatedText = translatedText)
        } else {
            block
        }
    }

    private fun toGoogleLanguageCode(lang: com.yuu18id.mangatranslator.domain.model.Language?): String {
        if (lang == null) return "auto"
        return when (lang) {
            com.yuu18id.mangatranslator.domain.model.Language.ENG -> "en"
            com.yuu18id.mangatranslator.domain.model.Language.JPN -> "ja"
            com.yuu18id.mangatranslator.domain.model.Language.CHS -> "zh-CN"
            com.yuu18id.mangatranslator.domain.model.Language.CHT -> "zh-TW"
            com.yuu18id.mangatranslator.domain.model.Language.KOR -> "ko"
            com.yuu18id.mangatranslator.domain.model.Language.FRA -> "fr"
            com.yuu18id.mangatranslator.domain.model.Language.DEU -> "de"
            com.yuu18id.mangatranslator.domain.model.Language.ESP -> "es"
            com.yuu18id.mangatranslator.domain.model.Language.IND -> "id"
            com.yuu18id.mangatranslator.domain.model.Language.ITA -> "it"
            com.yuu18id.mangatranslator.domain.model.Language.POR -> "pt"
            com.yuu18id.mangatranslator.domain.model.Language.RUS -> "ru"
            com.yuu18id.mangatranslator.domain.model.Language.VIE -> "vi"
            com.yuu18id.mangatranslator.domain.model.Language.ARA -> "ar"
            com.yuu18id.mangatranslator.domain.model.Language.THA -> "th"
            com.yuu18id.mangatranslator.domain.model.Language.TUR -> "tr"
            com.yuu18id.mangatranslator.domain.model.Language.UKR -> "uk"
            com.yuu18id.mangatranslator.domain.model.Language.POL -> "pl"
            com.yuu18id.mangatranslator.domain.model.Language.NLD -> "nl"
            com.yuu18id.mangatranslator.domain.model.Language.CSY -> "cs"
            com.yuu18id.mangatranslator.domain.model.Language.HUN -> "hu"
            com.yuu18id.mangatranslator.domain.model.Language.ELL -> "el"
            com.yuu18id.mangatranslator.domain.model.Language.SWE -> "sv"
            com.yuu18id.mangatranslator.domain.model.Language.ROM -> "ro"
            com.yuu18id.mangatranslator.domain.model.Language.HRV -> "hr"
        }
    }

    override fun isAvailable(): Boolean = true
}

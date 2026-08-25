package com.yuu18id.mangatranslator.data.translation.google

import android.net.Uri
import com.yuu18id.mangatranslator.data.ml.CloudTranslator
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslatorConfig
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

        val targetLangCode = config.targetLang.code.lowercase().let {
            if (it == "eng") "en" else if (it == "jpn") "ja" else it.take(2)
        }
        val sourceLangCode = config.sourceLang?.code?.lowercase()?.let {
            if (it == "eng") "en" else if (it == "jpn") "ja" else it.take(2)
        } ?: "auto"

        return textBlocks.map { block ->
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

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@map block
            }

            val responseBody = response.body?.string() ?: return@map block
            var translatedText = ""

            try {
                // Response format: [[["translated text","original text",null,null,1]],null,"ja"]
                val jsonArray = Json.parseToJsonElement(responseBody) as JsonArray
                val translationsArray = jsonArray[0] as JsonArray
                
                translatedText = buildString {
                    for (item in translationsArray) {
                        val partArray = item as? JsonArray
                        if (partArray != null && partArray.size > 0) {
                            append(partArray[0].jsonPrimitive.content)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            block.copy(translatedText = translatedText)
        }
    }

    override fun isAvailable(): Boolean = true
}

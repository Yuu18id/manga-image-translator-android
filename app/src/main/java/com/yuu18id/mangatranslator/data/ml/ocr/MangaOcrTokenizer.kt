package com.yuu18id.mangatranslator.data.ml.ocr

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaOcrTokenizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MangaTranslator"
        const val PAD_TOKEN_ID = 0L
        const val UNK_TOKEN_ID = 1L
        const val CLS_TOKEN_ID = 2L // BOS
        const val SEP_TOKEN_ID = 3L // EOS
        const val MASK_TOKEN_ID = 4L
    }

    private var vocabList: List<String> = emptyList()
    private val idToToken = mutableMapOf<Int, String>()
    private val tokenToId = mutableMapOf<String, Int>()
    private var isInitialized = false

    @Synchronized
    fun ensureLoaded() {
        if (isInitialized) return
        try {
            val inputStream = try {
                context.assets.open("models/manga_ocr_vocab.txt")
            } catch (e: Exception) {
                val file = java.io.File(context.filesDir, "models/manga_ocr_vocab.txt")
                if (file.exists()) file.inputStream() else null
            }

            if (inputStream != null) {
                val list = mutableListOf<String>()
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val token = line.trim()
                        list.add(token)
                        idToToken[index] = token
                        tokenToId[token] = index
                    }
                }
                vocabList = list
                isInitialized = true
                Log.i(TAG, "✓ MangaOcrTokenizer loaded ${vocabList.size} tokens from vocab.txt")
            } else {
                Log.w(TAG, "⚠ manga_ocr_vocab.txt not found in assets or filesDir")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load manga_ocr_vocab.txt: ${e.message}", e)
        }
    }

    fun decode(tokenIds: List<Long>): String {
        ensureLoaded()
        if (vocabList.isEmpty()) return ""

        val sb = StringBuilder()
        for (id in tokenIds) {
            // Skip special tokens
            if (id == PAD_TOKEN_ID || id == CLS_TOKEN_ID || id == SEP_TOKEN_ID || id == MASK_TOKEN_ID) {
                continue
            }
            val token = idToToken[id.toInt()] ?: continue
            if (token.startsWith("<unused") || token == "[UNK]") {
                continue
            }

            if (token.startsWith("##")) {
                sb.append(token.substring(2))
            } else {
                sb.append(token)
            }
        }
        return sb.toString().trim()
    }
}
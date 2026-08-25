package com.yuu18id.mangatranslator.data.ml.ocr

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlphabetDecoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MangaTranslator"
    }

    private var dictionary: List<String> = emptyList()
    private val blankIndex = 0

    init {
        loadAlphabet()
    }

    fun loadAlphabet(file: File? = null) {
        dictionary = try {
            if (file != null && file.exists()) {
                val lines = file.readLines().map { it.trimEnd('\n', '\r') }
                Log.i(TAG, "Loaded alphabet from file: ${lines.size} tokens")
                lines
            } else {
                val lines = context.assets.open("alphabet-all-v5.txt").bufferedReader().readLines().map { it.trimEnd('\n', '\r') }
                Log.i(TAG, "Loaded alphabet from assets: ${lines.size} tokens")
                lines
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load alphabet: ${e.message}", e)
            emptyList()
        }
    }

    data class DecodeResult(
        val text: String,
        val prob: Float,
        val validTimesteps: List<Int>,
        val chars: List<String>
    )

    fun decodeGreedy(logits: FloatArray, timeSteps: Int, vocabSize: Int): DecodeResult {
        val validTimesteps = mutableListOf<Int>()
        val chars = mutableListOf<String>()
        var lastCh = blankIndex
        val predictedIndices = mutableListOf<Int>()
        var totalLogProb = 0.0
        var charCount = 0

        for (t in 0 until timeSteps) {
            val offset = t * vocabSize
            var maxLogit = -Float.MAX_VALUE
            var bestIndex = 0

            for (v in 0 until vocabSize) {
                val logit = logits[offset + v]
                if (logit > maxLogit) {
                    maxLogit = logit
                    bestIndex = v
                }
            }

            if (bestIndex != 0) {
                predictedIndices.add(bestIndex)
            }

            if (bestIndex != lastCh && bestIndex != blankIndex) {
                var ch = dictionary.getOrElse(bestIndex) { "" }
                if (ch == "<SP>") ch = " "
                if (ch.isNotEmpty() && !ch.startsWith("<UNK>") && !ch.startsWith("<PAD>") && !ch.startsWith("<UNUSED")) {
                    chars.add(ch)
                    validTimesteps.add(t)

                    // Compute log-softmax for this token using log-sum-exp
                    var sumExp = 0.0
                    for (v in 0 until vocabSize) {
                        sumExp += kotlin.math.exp((logits[offset + v] - maxLogit).toDouble())
                    }
                    val logProb = -kotlin.math.ln(sumExp)
                    totalLogProb += logProb
                    charCount++
                }
            }
            lastCh = bestIndex
        }

        val fullText = chars.joinToString("")
        val prob = if (charCount > 0) {
            kotlin.math.exp(totalLogProb / charCount).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        if (predictedIndices.isNotEmpty()) {
            Log.d(TAG, "      [CTC Timesteps: $timeSteps] Non-blank tokens: $predictedIndices => \"$fullText\" (prob=${"%.3f".format(prob)})")
        }
        return DecodeResult(fullText, prob, validTimesteps, chars)
    }
}

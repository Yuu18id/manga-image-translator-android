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

    private data class BeamState(
        val pBlank: Double,
        val pNonBlank: Double,
        val timesteps: List<Int>
    ) {
        val totalProb: Double get() = pBlank + pNonBlank
    }

    /**
     * CTC Prefix Beam Search decoder with multi-path alignment merging.
     * Evaluates total prefix probability across all collapsed CTC alignments,
     * significantly reducing single-frame noise spikes and typo rates.
     */
    fun decodePrefixBeamSearch(
        logits: FloatArray,
        timeSteps: Int,
        vocabSize: Int,
        beamWidth: Int = 8,
        topKPerStep: Int = 25
    ): DecodeResult {
        if (timeSteps <= 0 || vocabSize <= 0 || logits.isEmpty()) {
            return DecodeResult("", 0f, emptyList(), emptyList())
        }

        var currentBeams: Map<List<Int>, BeamState> = mapOf(
            emptyList<Int>() to BeamState(pBlank = 1.0, pNonBlank = 0.0, timesteps = emptyList())
        )

        for (t in 0 until timeSteps) {
            val offset = t * vocabSize
            
            // 1. Find max logit for stable softmax
            var maxLogit = -Float.MAX_VALUE
            for (v in 0 until vocabSize) {
                val l = logits[offset + v]
                if (l > maxLogit) maxLogit = l
            }

            // 2. Compute sum of exp for softmax
            var sumExp = 0.0
            for (v in 0 until vocabSize) {
                sumExp += kotlin.math.exp((logits[offset + v] - maxLogit).toDouble())
            }
            val logSumExp = kotlin.math.ln(sumExp)

            // 3. Extract Top-K tokens + blank (0)
            // Use a priority/selection approach to collect topKPerStep tokens
            val candidateScores = mutableListOf<Pair<Int, Double>>()
            val pBlank = kotlin.math.exp((logits[offset] - maxLogit).toDouble() - logSumExp)
            candidateScores.add(0 to pBlank)

            // Top candidates queue
            val topCandidates = java.util.PriorityQueue<Pair<Int, Float>>(
                topKPerStep + 1,
                compareBy { it.second }
            )
            for (v in 1 until vocabSize) {
                val l = logits[offset + v]
                if (topCandidates.size < topKPerStep) {
                    topCandidates.offer(v to l)
                } else if (l > topCandidates.peek()!!.second) {
                    topCandidates.poll()
                    topCandidates.offer(v to l)
                }
            }

            while (topCandidates.isNotEmpty()) {
                val (v, l) = topCandidates.poll()!!
                val p = kotlin.math.exp((l - maxLogit).toDouble() - logSumExp)
                if (p > 1e-6) {
                    candidateScores.add(v to p)
                }
            }

            // 4. Update beams
            val nextBeams = mutableMapOf<List<Int>, BeamState>()

            for ((prefix, state) in currentBeams) {
                val pTotal = state.totalProb
                if (pTotal <= 1e-12) continue

                for ((c, p_c) in candidateScores) {
                    if (c == blankIndex) {
                        val existing = nextBeams[prefix]
                        val newBlank = (existing?.pBlank ?: 0.0) + pTotal * p_c
                        val newNonBlank = existing?.pNonBlank ?: 0.0
                        val ts = existing?.timesteps ?: state.timesteps
                        nextBeams[prefix] = BeamState(newBlank, newNonBlank, ts)
                    } else {
                        val lastChar = prefix.lastOrNull()
                        if (lastChar == c) {
                            // Same character without intervening blank -> collapses into same prefix
                            val existingSame = nextBeams[prefix]
                            val newNonBlankSame = (existingSame?.pNonBlank ?: 0.0) + state.pNonBlank * p_c
                            val newBlankSame = existingSame?.pBlank ?: 0.0
                            val tsSame = existingSame?.timesteps ?: state.timesteps
                            nextBeams[prefix] = BeamState(newBlankSame, newNonBlankSame, tsSame)

                            // Same character with intervening blank -> extends prefix
                            val newPrefix = prefix + c
                            val existingExt = nextBeams[newPrefix]
                            val newNonBlankExt = (existingExt?.pNonBlank ?: 0.0) + state.pBlank * p_c
                            val newBlankExt = existingExt?.pBlank ?: 0.0
                            val tsExt = existingExt?.timesteps ?: (state.timesteps + t)
                            nextBeams[newPrefix] = BeamState(newBlankExt, newNonBlankExt, tsExt)
                        } else {
                            // Different character -> extends prefix
                            val newPrefix = prefix + c
                            val existingExt = nextBeams[newPrefix]
                            val newNonBlankExt = (existingExt?.pNonBlank ?: 0.0) + pTotal * p_c
                            val newBlankExt = existingExt?.pBlank ?: 0.0
                            val tsExt = existingExt?.timesteps ?: (state.timesteps + t)
                            nextBeams[newPrefix] = BeamState(newBlankExt, newNonBlankExt, tsExt)
                        }
                    }
                }
            }

            // 5. Prune next beams to beamWidth
            currentBeams = nextBeams.entries
                .sortedByDescending { it.value.totalProb }
                .take(beamWidth)
                .associate { it.key to it.value }
        }

        val bestEntry = currentBeams.maxByOrNull { it.value.totalProb }
        val winningPrefix = bestEntry?.key ?: emptyList()
        val winningState = bestEntry?.value

        if (winningPrefix.isEmpty()) {
            return decodeGreedy(logits, timeSteps, vocabSize)
        }

        val chars = mutableListOf<String>()
        val validTimesteps = mutableListOf<Int>()
        for (i in winningPrefix.indices) {
            val tokenIdx = winningPrefix[i]
            var ch = dictionary.getOrElse(tokenIdx) { "" }
            if (ch == "<SP>") ch = " "
            if (ch.isNotEmpty() && !ch.startsWith("<UNK>") && !ch.startsWith("<PAD>") && !ch.startsWith("<UNUSED")) {
                chars.add(ch)
                if (winningState != null && i < winningState.timesteps.size) {
                    validTimesteps.add(winningState.timesteps[i])
                } else if (i < timeSteps) {
                    validTimesteps.add(i)
                }
            }
        }

        val fullText = chars.joinToString("")
        val confidence = winningState?.let {
            val avgLogProb = kotlin.math.ln(it.totalProb.coerceIn(1e-12, 1.0)) / (chars.size.coerceAtLeast(1))
            kotlin.math.exp(avgLogProb).toFloat().coerceIn(0f, 1f)
        } ?: 0.5f

        Log.d(TAG, "      [BeamSearch (width=$beamWidth)] tokens=$winningPrefix => \"$fullText\" (conf=${"%.3f".format(confidence)})")
        return DecodeResult(fullText, confidence, validTimesteps, chars)
    }

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

package com.yuu18id.mangatranslator.data.ml.ocr

import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap
import android.util.Log
import com.yuu18id.mangatranslator.data.ml.OcrEngine
import com.yuu18id.mangatranslator.data.ml.OnnxModelManager
import com.yuu18id.mangatranslator.domain.model.OcrConfig
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import com.yuu18id.mangatranslator.domain.model.TextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import javax.inject.Inject

class CtcOcrEngine @Inject constructor(
    private val modelManager: OnnxModelManager,
    private val preProcessor: OcrPreProcessor,
    private val decoder: AlphabetDecoder,
    private val colorExtractor: ColorExtractor
) : OcrEngine {

    companion object {
        private const val TAG = "MangaTranslator"
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun recognize(
        image: Bitmap,
        textRegions: List<Quadrilateral>,
        config: OcrConfig
    ): List<Quadrilateral> = withContext(Dispatchers.Default) {
        if (textRegions.isEmpty()) return@withContext emptyList()

        val session = modelManager.createSession(OnnxModelManager.ModelType.OCR_CTC_48PX, useNnapi = false)
        val env = modelManager.ortEnvironment

        // Majority voting on text direction within spatial clusters (like Python CommonOCR)
        val n = textRegions.size
        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            var r = i
            while (r != parent[r]) { parent[r] = parent[parent[r]]; r = parent[r] }
            return r
        }
        fun union(i: Int, j: Int) {
            val ri = find(i); val rj = find(j)
            if (ri != rj) parent[ri] = rj
        }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val q1 = textRegions[i]
                val q2 = textRegions[j]
                val r1 = q1.boundingRect(); val r2 = q2.boundingRect()
                val fs = kotlin.math.min(kotlin.math.min(r1.width(), r1.height()), kotlin.math.min(r2.width(), r2.height()))
                val xDist = if (r1.right < r2.left) r2.left - r1.right else if (r2.right < r1.left) r1.left - r2.right else 0f
                val yDist = if (r1.bottom < r2.top) r2.top - r1.bottom else if (r2.bottom < r1.top) r1.top - r2.bottom else 0f
                if (kotlin.math.hypot(xDist, yDist) < fs * 2.5f) {
                    union(i, j)
                }
            }
        }
        val clusterMap = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            clusterMap.getOrPut(find(i)) { mutableListOf() }.add(i)
        }
        val effectiveVertical = BooleanArray(n)
        for ((_, indices) in clusterMap) {
            val vCount = indices.count { textRegions[it].isVertical }
            val majorityVertical = vCount >= (indices.size - vCount)
            for (idx in indices) {
                effectiveVertical[idx] = majorityVertical
            }
        }

        val allCrops = textRegions.mapIndexed { idx, region ->
            preProcessor.cropTextRegion(image, region, forceVertical = effectiveVertical[idx])
        }

        val outRegions = mutableListOf<Quadrilateral>()
        val maxChunkSize = 16

        for (chunkStart in 0 until n step maxChunkSize) {
            val chunkEnd = kotlin.math.min(chunkStart + maxChunkSize, n)
            val chunkIndices = chunkStart until chunkEnd
            val chunk = chunkIndices.map { textRegions[it] }
            val crops = chunkIndices.map { allCrops[it] }
            
            val (tensorData, widths) = preProcessor.batchCrops(crops)
            val batchSize = chunk.size
            val paddedWidth = tensorData.size / (batchSize * 3 * 48)

            val byteBuffer = java.nio.ByteBuffer.allocateDirect(tensorData.size * 4)
                .order(java.nio.ByteOrder.nativeOrder())
            val floatBuffer = byteBuffer.asFloatBuffer()
            floatBuffer.put(tensorData)
            floatBuffer.rewind()

            var inputTensor: OnnxTensor? = null
            var result: ai.onnxruntime.OrtSession.Result? = null

            try {
                inputTensor = OnnxTensor.createTensor(
                    env,
                    floatBuffer,
                    longArrayOf(batchSize.toLong(), 3L, 48L, paddedWidth.toLong())
                )

                val inputName = session.inputNames.iterator().next()
                Log.d(TAG, "   [ONNX] inputName=$inputName, running inference...")
                result = session.run(mapOf(inputName to inputTensor))
                
                Log.d(TAG, "   [ONNX] result size=${result.size()}")
                
                // Use FloatBuffer approach instead of getValue() cast for reliability
                val outputTensor = result.get(0) as OnnxTensor
                val outputInfo = outputTensor.info
                val outputShape = outputInfo.shape
                Log.d(TAG, "   [ONNX] output[0] shape=${outputShape.toList()}, type=${outputInfo.type}")
                
                val outputBuffer = outputTensor.floatBuffer
                val totalOutputFloats = outputShape.fold(1L) { acc, v -> acc * v }.toInt()
                val outputFlat = FloatArray(totalOutputFloats)
                outputBuffer.get(outputFlat)
                
                // Interpret as [batchSize, seqLen, vocabSize]
                val seqLen = outputShape[1].toInt()
                val vocabSize = outputShape[2].toInt()
                Log.d(TAG, "   [ONNX] seqLen=$seqLen, vocabSize=$vocabSize")
                
                // Debug: log first few logits values for batch item 0
                if (seqLen > 0 && vocabSize > 0) {
                    val t0offset = 0 // batch=0, seq=0
                    val topK = 5
                    val indices = (0 until vocabSize).sortedByDescending { outputFlat[t0offset + it] }.take(topK)
                    val vals = indices.map { outputFlat[t0offset + it] }
                    Log.d(TAG, "   [ONNX] t=0 top-$topK: indices=$indices vals=$vals")
                }

                val colorOutputTensor = if (result.size() > 1) result.get(1) as? OnnxTensor else null

                for (i in chunk.indices) {
                    val region = chunk[i]
                    
                    // Extract logits for batch item i from flat array
                    val batchOffset = i * seqLen * vocabSize
                    val flatLogits = FloatArray(seqLen * vocabSize)
                    System.arraycopy(outputFlat, batchOffset, flatLogits, 0, seqLen * vocabSize)

                    val decodeResult = decoder.decodeGreedy(flatLogits, seqLen, vocabSize)

                    val colors = if (colorOutputTensor != null) {
                        val colorShape = colorOutputTensor.info.shape
                        val colorSeqLen = colorShape[1].toInt()
                        val colorDim = colorShape[2].toInt()
                        val colorBuffer = colorOutputTensor.floatBuffer
                        val colorFlat = FloatArray(colorShape.fold(1L) { acc, v -> acc * v }.toInt())
                        colorBuffer.rewind()
                        colorBuffer.get(colorFlat)
                        val colorBatchOffset = i * colorSeqLen * colorDim
                        val batchColorFlat = FloatArray(colorSeqLen * colorDim)
                        System.arraycopy(colorFlat, colorBatchOffset, batchColorFlat, 0, colorSeqLen * colorDim)
                        colorExtractor.extractColors(batchColorFlat, decodeResult.validTimesteps, decodeResult.chars)
                    } else {
                        TextColor(intArrayOf(0, 0, 0), intArrayOf(255, 255, 255))
                    }

                    val updatedRegion = region.copy(
                        text = decodeResult.text,
                        prob = decodeResult.prob,
                        fgColor = colors.fg,
                        bgColor = colors.bg
                    )
                    outRegions.add(updatedRegion)
                    Log.d(TAG, "   [OCR Crop $i] cropSize=${crops[i].width}x${crops[i].height} => text=\"${decodeResult.text}\" prob=${decodeResult.prob}")
                }
            } finally {
                inputTensor?.close()
                result?.close()
            }
        }

        outRegions
    }
}

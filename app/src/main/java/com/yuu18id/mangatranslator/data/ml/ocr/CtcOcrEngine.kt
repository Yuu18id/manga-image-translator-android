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

        // Bucket sorting by crop width to group similar aspect-ratio crops together, minimizing zero-padding FLOPs
        val sortedIndices = (0 until n).sortedBy { allCrops[it].width }
        val outRegionsMap = arrayOfNulls<Quadrilateral>(n)
        val maxChunkSize = 4 // Mobile-safe chunk size to prevent large tensor bloat

        try {
            for (chunkStart in 0 until n step maxChunkSize) {
                val chunkEnd = kotlin.math.min(chunkStart + maxChunkSize, n)
                val chunkOriginalIndices = (chunkStart until chunkEnd).map { sortedIndices[it] }
                val chunk = chunkOriginalIndices.map { textRegions[it] }
                val crops = chunkOriginalIndices.map { allCrops[it] }
                
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
                    Log.d(TAG, "   [ONNX] inputName=$inputName, running inference (batch=$batchSize, width=$paddedWidth)...")
                    result = session.run(mapOf(inputName to inputTensor))
                    
                    Log.d(TAG, "   [ONNX] result size=${result.size()}")
                    
                    val outputTensor = result.get(0) as OnnxTensor
                    val outputInfo = outputTensor.info
                    val outputShape = outputInfo.shape
                    val seqLen = outputShape[1].toInt()
                    val vocabSize = outputShape[2].toInt()
                    Log.d(TAG, "   [ONNX] output[0] shape=${outputShape.toList()}, seqLen=$seqLen, vocabSize=$vocabSize")

                    val outputBuffer = outputTensor.floatBuffer

                    val colorOutputTensor = if (result.size() > 1) result.get(1) as? OnnxTensor else null
                    val colorBuffer = colorOutputTensor?.floatBuffer
                    val colorShape = colorOutputTensor?.info?.shape
                    val colorSeqLen = colorShape?.getOrNull(1)?.toInt() ?: 0
                    val colorDim = colorShape?.getOrNull(2)?.toInt() ?: 0

                    for (i in chunk.indices) {
                        val origIdx = chunkOriginalIndices[i]
                        val region = chunk[i]
                        
                        // Extract logits ONLY for batch item i directly from native FloatBuffer slice (~2 MB instead of 260 MB)
                        val itemFloats = seqLen * vocabSize
                        val flatLogits = FloatArray(itemFloats)
                        outputBuffer.position(i * itemFloats)
                        outputBuffer.get(flatLogits)

                        val decodeResult = decoder.decodePrefixBeamSearch(flatLogits, seqLen, vocabSize, beamWidth = 8)
                        val colors = colorExtractor.extractColorsFromBitmap(crops[i])

                        val updatedRegion = region.copy(
                            text = decodeResult.text,
                            prob = decodeResult.prob,
                            fgColor = colors.fg,
                            bgColor = colors.bg
                        )
                        outRegionsMap[origIdx] = updatedRegion
                        Log.d(TAG, "   [OCR Crop $origIdx] cropSize=${crops[i].width}x${crops[i].height} => text=\"${decodeResult.text}\" fg=(${colors.fg.joinToString()}) bg=(${colors.bg.joinToString()})")
                    }
                } finally {
                    inputTensor?.close()
                    result?.close()
                }
            }
        } finally {
            allCrops.forEach { crop ->
                if (!crop.isRecycled) {
                    crop.recycle()
                }
            }
        }

        outRegionsMap.filterNotNull()
    }
}

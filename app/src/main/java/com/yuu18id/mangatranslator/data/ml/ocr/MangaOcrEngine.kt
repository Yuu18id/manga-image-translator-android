package com.yuu18id.mangatranslator.data.ml.ocr

import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap
import android.util.Log
import com.yuu18id.mangatranslator.data.ml.OcrEngine
import com.yuu18id.mangatranslator.data.ml.OnnxModelManager
import com.yuu18id.mangatranslator.domain.model.OcrConfig
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaOcrEngine @Inject constructor(
    private val modelManager: OnnxModelManager,
    private val preProcessor: OcrPreProcessor,
    private val tokenizer: MangaOcrTokenizer,
    private val ctcOcrEngine: CtcOcrEngine,
    private val colorExtractor: ColorExtractor
) : OcrEngine {

    companion object {
        private const val TAG = "MangaTranslator"
        private const val TARGET_IMAGE_SIZE = 224
        private const val MAX_GENERATION_LENGTH = 35
        private val mutex = Mutex()
    }

    override suspend fun recognize(
        image: Bitmap,
        textRegions: List<Quadrilateral>,
        config: OcrConfig
    ): List<Quadrilateral> = withContext(Dispatchers.Default) {
        if (textRegions.isEmpty()) return@withContext emptyList()

        // Check if full FP32 Manga-OCR models exist
        val encoderExists = modelManager.isModelDownloaded(OnnxModelManager.ModelType.MANGA_OCR_ENCODER)
        val decoderExists = modelManager.isModelDownloaded(OnnxModelManager.ModelType.MANGA_OCR_DECODER)

        if (!encoderExists || !decoderExists) {
            Log.w(TAG, "⚠ Manga-OCR full FP32 models not found on disk. Falling back to default CTC OCR.")
            return@withContext ctcOcrEngine.recognize(image, textRegions, config)
        }

        tokenizer.ensureLoaded()

        mutex.withLock {
            val env = modelManager.ortEnvironment
            val encoderSession = modelManager.createSession(OnnxModelManager.ModelType.MANGA_OCR_ENCODER, useNnapi = false)
            val decoderSession = modelManager.createSession(OnnxModelManager.ModelType.MANGA_OCR_DECODER, useNnapi = false)

            val outRegions = mutableListOf<Quadrilateral>()

            val vitInputArray = Array(1) { Array(3) { Array(TARGET_IMAGE_SIZE) { FloatArray(TARGET_IMAGE_SIZE) } } }
            val hiddenArray3D = Array(1) { Array(197) { FloatArray(768) } }

            for (region in textRegions) {
                val crop = preProcessor.cropForMangaOcr(image, region)
                if (crop.width < 8 || crop.height < 8) {
                    outRegions.add(region.copy(text = "", prob = 0f))
                    if (!crop.isRecycled) crop.recycle()
                    continue
                }

                var encoderInputTensor: OnnxTensor? = null
                var encoderResult: ai.onnxruntime.OrtSession.Result? = null

                try {
                    // 1. Prepare 224x224 normalized input for ViT encoder in 4D primitive float array
                    prepareViTInput(crop, vitInputArray)
                    encoderInputTensor = OnnxTensor.createTensor(
                        env,
                        vitInputArray
                    )

                    val encoderInputName = encoderSession.inputNames.iterator().next()
                    encoderResult = encoderSession.run(mapOf(encoderInputName to encoderInputTensor))

                    // Extract encoder output [1, 197, 768] into 3D primitive float array
                    val encoderOutput = encoderResult.get(0) as OnnxTensor
                    val hiddenFloats = FloatArray(encoderOutput.floatBuffer.remaining())
                    encoderOutput.floatBuffer.get(hiddenFloats)

                    // Close encoder tensor and result immediately to free encoder session memory
                    encoderInputTensor.close()
                    encoderInputTensor = null
                    encoderResult.close()
                    encoderResult = null

                    var offset = 0
                    for (i in 0 until 197) {
                        val row = hiddenArray3D[0][i]
                        for (j in 0 until 768) {
                            row[j] = hiddenFloats[offset++]
                        }
                    }

                    // 2. Autoregressive greedy decoding loop
                    val generatedTokenIds = mutableListOf<Long>()
                    val inputIdsList = mutableListOf<Long>(MangaOcrTokenizer.CLS_TOKEN_ID) // BOS = 2L

                    for (step in 0 until MAX_GENERATION_LENGTH) {
                        val seqLen = inputIdsList.size
                        val inputIdsArray = arrayOf(inputIdsList.toLongArray())
                        val inputIdsTensor = OnnxTensor.createTensor(
                            env,
                            inputIdsArray
                        )
                        val hiddenStateTensor = OnnxTensor.createTensor(
                            env,
                            hiddenArray3D
                        )

                        val decoderInputs = mapOf(
                            "input_ids" to inputIdsTensor,
                            "encoder_hidden_states" to hiddenStateTensor
                        )

                        var decoderResult: ai.onnxruntime.OrtSession.Result? = null
                        try {
                            decoderResult = decoderSession.run(decoderInputs)
                            val logitsTensor = decoderResult.get(0) as OnnxTensor
                            val logitsBuffer = logitsTensor.floatBuffer
                            val vocabSize = logitsTensor.info.shape[2].toInt()

                            // Extract argmax for the last timestep (seqLen - 1)
                            logitsBuffer.position((seqLen - 1) * vocabSize)
                            var maxLogit = Float.NEGATIVE_INFINITY
                            var bestTokenId = 0L
                            for (v in 0 until vocabSize) {
                                val logit = logitsBuffer.get()
                                if (logit > maxLogit) {
                                    maxLogit = logit
                                    bestTokenId = v.toLong()
                                }
                            }

                            if (bestTokenId == MangaOcrTokenizer.SEP_TOKEN_ID) {
                                break // EOS reached
                            }

                            // Avoid infinite single-token loops
                            if (generatedTokenIds.size >= 2 &&
                                generatedTokenIds.takeLast(2).all { it == bestTokenId }) {
                                break
                            }

                            generatedTokenIds.add(bestTokenId)
                            inputIdsList.add(bestTokenId)
                        } finally {
                            inputIdsTensor.close()
                            hiddenStateTensor.close()
                            decoderResult?.close()
                        }
                    }

                    val recognizedText = tokenizer.decode(generatedTokenIds)
                    val colors = colorExtractor.extractColorsFromBitmap(crop)

                    val updatedRegion = region.copy(
                        text = recognizedText,
                        prob = 0.95f,
                        fgColor = colors.fg,
                        bgColor = colors.bg
                    )
                    outRegions.add(updatedRegion)
                    Log.d(TAG, "   [Manga-OCR] cropSize=${crop.width}x${crop.height} => \"$recognizedText\" fg=(${colors.fg.joinToString()}) bg=(${colors.bg.joinToString()})")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Manga-OCR failed on crop: ${e.message}", e)
                    outRegions.add(region.copy(text = "", prob = 0f))
                } finally {
                    encoderInputTensor?.close()
                    encoderResult?.close()
                    if (!crop.isRecycled) {
                        crop.recycle()
                    }
                }
            }

            outRegions
        }
    }

    private fun prepareViTInput(
        crop: Bitmap,
        outArray: Array<Array<Array<FloatArray>>>
    ) {
        val resized = if (crop.width == TARGET_IMAGE_SIZE && crop.height == TARGET_IMAGE_SIZE) {
            crop
        } else {
            Bitmap.createScaledBitmap(crop, TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE, true)
        }

        val totalPixels = TARGET_IMAGE_SIZE * TARGET_IMAGE_SIZE
        val pixels = IntArray(totalPixels)
        resized.getPixels(pixels, 0, TARGET_IMAGE_SIZE, 0, 0, TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE)

        val inv127 = 1.0f / 127.5f // Normalize [0, 255] to [-1.0, 1.0]
        var idx = 0
        val rPlane = outArray[0][0]
        val gPlane = outArray[0][1]
        val bPlane = outArray[0][2]

        for (y in 0 until TARGET_IMAGE_SIZE) {
            val rRow = rPlane[y]
            val gRow = gPlane[y]
            val bRow = bPlane[y]
            for (x in 0 until TARGET_IMAGE_SIZE) {
                val p = pixels[idx++]
                rRow[x] = (((p shr 16) and 0xFF) * inv127) - 1.0f
                gRow[x] = (((p shr 8) and 0xFF) * inv127) - 1.0f
                bRow[x] = ((p and 0xFF) * inv127) - 1.0f
            }
        }

        if (resized !== crop && !resized.isRecycled) {
            resized.recycle()
        }
    }
}
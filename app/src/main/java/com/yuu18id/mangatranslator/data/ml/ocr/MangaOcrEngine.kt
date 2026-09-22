package com.yuu18id.mangatranslator.data.ml.ocr

import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap
import android.util.Log
import com.yuu18id.mangatranslator.data.ml.OcrEngine
import com.yuu18id.mangatranslator.data.ml.OnnxModelManager
import com.yuu18id.mangatranslator.domain.model.OcrConfig
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

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
        private const val MAX_GENERATION_LENGTH = 100
        private val mutex = Mutex()

        /**
         * Determines whether a block is a dense multi-column narrative box requiring line-by-line decoding.
         * - Blocks with 1 line are always processed as single crops.
         * - Standard dialogue bubbles with 2 or 3 short lines are processed as single bubble crops.
         * - Tall narrative boxes (>= 4 lines, 3 lines >= 280px, or 2 lines >= 320px tall / >= 26 chars)
         *   are processed line-by-line to prevent vertical ViT resolution compression.
         */
        internal fun isDenseMultiLineBlock(block: TextBlock): Boolean {
            if (block.lines.size >= 4) return true
            if (block.lines.size <= 1) return false

            val maxLineLength = block.lines.maxOfOrNull { line ->
                val r = line.boundingRect()
                if (block.isVertical) r.height() else r.width()
            } ?: 0f

            val estChars = block.lines.sumOf { line ->
                val r = line.boundingRect()
                val majorAxis = if (block.isVertical) r.height() else r.width()
                val minorAxis = max(1f, if (block.isVertical) r.width() else r.height())
                (majorAxis / minorAxis).toDouble()
            }

            // 3-line blocks: dense if line length >= 280px or total estimated chars >= 30
            if (block.lines.size == 3) {
                return estChars >= 30.0 || maxLineLength >= 280f
            }

            // 2-line blocks: dense if exceptionally tall narrative columns (>= 320px tall, ~15+ kanji)
            // or high character count (>= 26 chars)
            if (block.lines.size == 2) {
                return maxLineLength >= 320f || estChars >= 26.0
            }

            return false
        }

        /**
         * Merges sequentially decoded line texts, trimming overlapping substrings or repeated prefixes/suffixes
         * caused by neighboring column bleeding.
         */
        internal fun mergeOverlappingLines(lines: List<String>, isVertical: Boolean): String {
            if (lines.isEmpty()) return ""
            val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
            if (cleaned.isEmpty()) return ""
            if (cleaned.size == 1) return cleaned[0]

            val result = StringBuilder(cleaned[0])

            for (i in 1 until cleaned.size) {
                val next = cleaned[i]
                val currentStr = result.toString()

                // Case 1: Next line is completely contained inside current string
                if (currentStr.contains(next)) {
                    continue
                }

                // Case 2: What we already have is completely contained at the start of next line
                if (next.startsWith(currentStr)) {
                    result.clear()
                    result.append(next)
                    continue
                }

                // Case 3: Suffix of current matches prefix of next (min overlap >= 2 chars)
                var maxOverlap = 0
                val checkLimit = min(currentStr.length, next.length)
                for (len in checkLimit downTo 2) {
                    val suffix = currentStr.takeLast(len)
                    val prefix = next.take(len)
                    if (suffix == prefix) {
                        maxOverlap = len
                        break
                    }
                }

                if (maxOverlap > 0) {
                    result.append(next.substring(maxOverlap))
                } else {
                    if (!isVertical) {
                        result.append(" ")
                    }
                    result.append(next)
                }
            }

            return result.toString()
        }
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

                try {
                    val (recognizedText, colors) = decodeCrop(
                        crop = crop,
                        env = env,
                        encoderSession = encoderSession,
                        decoderSession = decoderSession,
                        vitInputArray = vitInputArray,
                        hiddenArray3D = hiddenArray3D
                    )

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
                    if (!crop.isRecycled) {
                        crop.recycle()
                    }
                }
            }

            outRegions
        }
    }

    override suspend fun recognizeBlocks(
        image: Bitmap,
        blocks: List<TextBlock>,
        config: OcrConfig
    ): List<TextBlock> = withContext(Dispatchers.Default) {
        if (blocks.isEmpty()) return@withContext emptyList()

        val encoderExists = modelManager.isModelDownloaded(OnnxModelManager.ModelType.MANGA_OCR_ENCODER)
        val decoderExists = modelManager.isModelDownloaded(OnnxModelManager.ModelType.MANGA_OCR_DECODER)

        if (!encoderExists || !decoderExists) {
            Log.w(TAG, "⚠ Manga-OCR full FP32 models not found on disk. Falling back to default CTC OCR.")
            return@withContext ctcOcrEngine.recognizeBlocks(image, blocks, config)
        }

        tokenizer.ensureLoaded()

        mutex.withLock {
            val env = modelManager.ortEnvironment
            val encoderSession = modelManager.createSession(OnnxModelManager.ModelType.MANGA_OCR_ENCODER, useNnapi = false)
            val decoderSession = modelManager.createSession(OnnxModelManager.ModelType.MANGA_OCR_DECODER, useNnapi = false)

            val outBlocks = mutableListOf<TextBlock>()

            val vitInputArray = Array(1) { Array(3) { Array(TARGET_IMAGE_SIZE) { FloatArray(TARGET_IMAGE_SIZE) } } }
            val hiddenArray3D = Array(1) { Array(197) { FloatArray(768) } }

            for (block in blocks) {
                // Adaptive OCR Routing:
                // - Normal speech bubbles (<= 2 lines or 3 short lines): process as single bubble crop (fast + accurate with generous padding).
                // - Dense multi-column narrative boxes (>= 4 lines or 3 long/dense lines): process line-by-line to prevent ViT patch resolution collapse and hallucination.
                val isDenseMultiColumn = isDenseMultiLineBlock(block)

                if (!isDenseMultiColumn) {
                    val crop = preProcessor.cropBlockForMangaOcr(image, block)
                    if (crop.width < 8 || crop.height < 8) {
                        outBlocks.add(block.copy(text = ""))
                        if (!crop.isRecycled) crop.recycle()
                        continue
                    }

                    try {
                        val (recognizedText, colors) = decodeCrop(
                            crop = crop,
                            env = env,
                            encoderSession = encoderSession,
                            decoderSession = decoderSession,
                            vitInputArray = vitInputArray,
                            hiddenArray3D = hiddenArray3D
                        )

                        val updatedLines = block.lines.map { line ->
                            line.copy(fgColor = colors.fg, bgColor = colors.bg)
                        }

                        val updatedBlock = block.copy(
                            text = recognizedText,
                            fgColor = colors.fg,
                            bgColor = colors.bg,
                            lines = updatedLines
                        )
                        outBlocks.add(updatedBlock)
                        Log.i(TAG, "   [Manga-OCR Bubble] size=${crop.width}x${crop.height}, lines=${block.lines.size} => \"$recognizedText\"")

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Manga-OCR failed on bubble crop: ${e.message}", e)
                        outBlocks.add(block.copy(text = ""))
                    } finally {
                        if (!crop.isRecycled) {
                            crop.recycle()
                        }
                    }
                } else {
                    Log.i(TAG, "   [Manga-OCR Adaptive] Block has ${block.lines.size} lines -> processing line-by-line to prevent hallucination")
                    val updatedLines = mutableListOf<Quadrilateral>()
                    val lineTexts = mutableListOf<String>()
                    var blockColors: TextColor? = null

                    for (line in block.lines) {
                        val crop = preProcessor.cropForMangaOcr(image, line)
                        if (crop.width < 8 || crop.height < 8) {
                            updatedLines.add(line.copy(text = "", prob = 0f))
                            if (!crop.isRecycled) crop.recycle()
                            continue
                        }

                        try {
                            val (lineText, colors) = decodeCrop(
                                crop = crop,
                                env = env,
                                encoderSession = encoderSession,
                                decoderSession = decoderSession,
                                vitInputArray = vitInputArray,
                                hiddenArray3D = hiddenArray3D
                            )
                            if (blockColors == null) blockColors = colors
                            updatedLines.add(line.copy(
                                text = lineText,
                                prob = 0.95f,
                                fgColor = colors.fg,
                                bgColor = colors.bg
                            ))
                            if (lineText.isNotBlank()) {
                                lineTexts.add(lineText.trim())
                            }
                            Log.i(TAG, "      [Manga-OCR Line] size=${crop.width}x${crop.height} => \"$lineText\"")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Manga-OCR failed on line crop: ${e.message}", e)
                            updatedLines.add(line.copy(text = "", prob = 0f))
                        } finally {
                            if (!crop.isRecycled) {
                                crop.recycle()
                            }
                        }
                    }

                    val joinedText = mergeOverlappingLines(lineTexts, block.isVertical)
                    val finalColors = blockColors ?: TextColor(intArrayOf(0, 0, 0), intArrayOf(255, 255, 255))
                    val updatedBlock = block.copy(
                        text = joinedText,
                        fgColor = finalColors.fg,
                        bgColor = finalColors.bg,
                        lines = updatedLines
                    )
                    outBlocks.add(updatedBlock)
                    Log.i(TAG, "   [Manga-OCR Dense Block Result] lines=${block.lines.size} => \"$joinedText\"")
                }
            }

            outBlocks
        }
    }

    private fun decodeCrop(
        crop: Bitmap,
        env: ai.onnxruntime.OrtEnvironment,
        encoderSession: ai.onnxruntime.OrtSession,
        decoderSession: ai.onnxruntime.OrtSession,
        vitInputArray: Array<Array<Array<FloatArray>>>,
        hiddenArray3D: Array<Array<FloatArray>>
    ): Pair<String, TextColor> {
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

            val hiddenStateTensor = OnnxTensor.createTensor(
                env,
                hiddenArray3D
            )
            try {
                for (step in 0 until MAX_GENERATION_LENGTH) {
                    val seqLen = inputIdsList.size
                    val inputIdsArray = arrayOf(inputIdsList.toLongArray())
                    val inputIdsTensor = OnnxTensor.createTensor(
                        env,
                        inputIdsArray
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

                        // Avoid infinite single-token loops (break only if 5 identical tokens in a row)
                        if (generatedTokenIds.size >= 5 &&
                            generatedTokenIds.takeLast(5).all { it == bestTokenId }) {
                            break
                        }

                        generatedTokenIds.add(bestTokenId)
                        inputIdsList.add(bestTokenId)
                    } finally {
                        inputIdsTensor.close()
                        decoderResult?.close()
                    }
                }
            } finally {
                hiddenStateTensor.close()
            }

            val recognizedText = tokenizer.decode(generatedTokenIds)
            val colors = colorExtractor.extractColorsFromBitmap(crop)
            return Pair(recognizedText, colors)
        } finally {
            encoderInputTensor?.close()
            encoderResult?.close()
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
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                // Color manga support: Manga-OCR was trained on grayscale Manga109 (R = G = B).
                // Colored inks (magenta, pink, cyan, blue) on light speech bubbles have high reflectance
                // in some channels (e.g. pink has R ~ 224, making it invisible in red channel).
                // min(r, min(g, b)) extracts the true ink stroke density across all colors.
                // Copying it to R=G=B produces sharp black ink on clean paper without channel conflict.
                val inkVal = min(r, min(g, b))
                val normVal = (inkVal * inv127) - 1.0f

                rRow[x] = normVal
                gRow[x] = normVal
                bRow[x] = normVal
            }
        }

        if (resized !== crop && !resized.isRecycled) {
            resized.recycle()
        }
    }
}
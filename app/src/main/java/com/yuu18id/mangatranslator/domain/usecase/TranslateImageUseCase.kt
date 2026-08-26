package com.yuu18id.mangatranslator.domain.usecase

import android.graphics.Bitmap
import android.util.Log
import com.yuu18id.mangatranslator.data.mask.MaskRefinement
import com.yuu18id.mangatranslator.data.ml.Inpainter
import com.yuu18id.mangatranslator.data.ml.OcrEngine
import com.yuu18id.mangatranslator.data.ml.TextDetector
import com.yuu18id.mangatranslator.data.ml.TextRenderer
import com.yuu18id.mangatranslator.data.textline.BracketBalancer
import com.yuu18id.mangatranslator.data.textline.DictionaryFilter
import com.yuu18id.mangatranslator.data.textline.PostTranslationVerifier
import com.yuu18id.mangatranslator.data.textline.ReadingOrderSorter
import com.yuu18id.mangatranslator.data.textline.TextlineMerger
import com.yuu18id.mangatranslator.data.translation.TranslatorFactory
import com.yuu18id.mangatranslator.domain.model.PipelineStage
import com.yuu18id.mangatranslator.domain.model.PipelineState
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import com.yuu18id.mangatranslator.domain.model.TranslationConfig
import com.yuu18id.mangatranslator.domain.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslateImageUseCase @Inject constructor(
    private val textDetector: TextDetector,
    private val ocrEngine: OcrEngine,
    private val textlineMerger: TextlineMerger,
    private val readingOrderSorter: ReadingOrderSorter,
    private val bracketBalancer: BracketBalancer,
    private val dictionaryFilter: DictionaryFilter,
    private val postTranslationVerifier: PostTranslationVerifier,
    private val translatorFactory: TranslatorFactory,
    private val maskRefinement: MaskRefinement,
    private val inpainter: Inpainter,
    private val textRenderer: TextRenderer
) {
    companion object {
        private const val TAG = "MangaTranslator"
    }

    suspend fun detectOnly(image: Bitmap, config: com.yuu18id.mangatranslator.domain.model.DetectorConfig): TextDetector.DetectionResult {
        return textDetector.detect(image, config)
    }

    operator fun invoke(image: Bitmap, config: TranslationConfig): Flow<PipelineState> = flow {
        var currentStage = PipelineStage.DETECTION
        val startTime = System.currentTimeMillis()

        Log.i(TAG, "==================================================")
        Log.i(TAG, "▶ START TRANSLATION PIPELINE (Direct Auto Mode)")
        Log.i(TAG, "Image Size: ${image.width}x${image.height}")
        Log.i(TAG, "Translator: ${config.translator.translatorType}, Target: ${config.translator.targetLang}, Source: ${config.translator.sourceLang ?: "AUTO"}")
        Log.i(TAG, "==================================================")

        try {
            currentStage = PipelineStage.DETECTION
            emit(PipelineState.Progress(currentStage, 0.1f, "Detecting text bubbles..."))
            val detStart = System.currentTimeMillis()
            val detections = textDetector.detect(image, config.detector)
            Log.i(TAG, "✓ [1/7 DETECTION] Found ${detections.textlines.size} textlines in ${System.currentTimeMillis() - detStart}ms")

            if (detections.textlines.isEmpty()) {
                Log.w(TAG, "⚠ No textlines detected in image. Skipping remaining stages.")
                val endTime = System.currentTimeMillis()
                emit(
                    PipelineState.Completed(
                        TranslationResult(
                            originalImage = image,
                            translatedImage = image,
                            textBlocks = emptyList(),
                            config = config,
                            timestamp = startTime,
                            processingTimeMs = endTime - startTime
                        )
                    )
                )
                return@flow
            }

            executePipelineInternal(image, detections.textlines, detections.mask, config, startTime).collect {
                emit(it)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "❌ PIPELINE ERROR at stage $currentStage: ${exception.message}", exception)
            emit(PipelineState.Error(currentStage, exception.message ?: "Unknown error", exception))
        }
    }.flowOn(Dispatchers.Default)

    fun executeFromDetections(
        image: Bitmap,
        customTextlines: List<Quadrilateral>,
        config: TranslationConfig,
        rawMask: Bitmap? = null
    ): Flow<PipelineState> = flow {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "==================================================")
        Log.i(TAG, "▶ START TRANSLATION PIPELINE (From Curated Boxes: ${customTextlines.size} items)")
        Log.i(TAG, "==================================================")

        if (customTextlines.isEmpty()) {
            val endTime = System.currentTimeMillis()
            emit(
                PipelineState.Completed(
                    TranslationResult(
                        originalImage = image,
                        translatedImage = image,
                        textBlocks = emptyList(),
                        config = config,
                        timestamp = startTime,
                        processingTimeMs = endTime - startTime
                    )
                )
            )
            return@flow
        }

        try {
            executePipelineInternal(image, customTextlines, rawMask, config, startTime).collect {
                emit(it)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "❌ PIPELINE ERROR from custom detections: ${exception.message}", exception)
            emit(PipelineState.Error(PipelineStage.OCR, exception.message ?: "Unknown error", exception))
        }
    }.flowOn(Dispatchers.Default)

    private fun executePipelineInternal(
        image: Bitmap,
        textlines: List<Quadrilateral>,
        rawMask: Bitmap?,
        config: TranslationConfig,
        startTime: Long
    ): Flow<PipelineState> = flow {
        var currentStage = PipelineStage.OCR

        // 2. OCR
        currentStage = PipelineStage.OCR
        emit(PipelineState.Progress(currentStage, 0.3f, "Reading text (OCR)..."))
        val ocrStart = System.currentTimeMillis()
        val ocrResults = ocrEngine.recognize(image, textlines, config.ocr)
        Log.i(TAG, "✓ [2/7 OCR] Recognized ${ocrResults.size} textlines in ${System.currentTimeMillis() - ocrStart}ms")
        ocrResults.forEachIndexed { i, q ->
            Log.i(TAG, "   OCR Line $i: \"${q.text}\" (prob=${q.prob})")
        }

        // 3. Textline Merge & Order
        currentStage = PipelineStage.TEXTLINE_MERGE
        emit(PipelineState.Progress(currentStage, 0.45f, "Merging and ordering text..."))
        var mergedBlocks = textlineMerger.merge(ocrResults)
        mergedBlocks = readingOrderSorter.sort(mergedBlocks, isRtl = true)
        val balancedBlocks = mergedBlocks.map { block ->
            val balancedText = bracketBalancer.balance(block.text)
            block.copy(text = balancedText)
        }
        Log.i(TAG, "✓ [3/7 MERGE] Grouped into ${balancedBlocks.size} text blocks:")
        balancedBlocks.forEachIndexed { i, b ->
            Log.i(TAG, "   Block $i [${b.lines.size} lines]: \"${b.text}\" bounds=${b.mergedBoundingBox()}")
        }

        // 4. Translation
        currentStage = PipelineStage.TRANSLATION
        emit(PipelineState.Progress(currentStage, 0.6f, "Translating text via ${config.translator.translatorType.displayName}..."))
        val preFilteredBlocks = balancedBlocks.map { block ->
            block.copy(text = dictionaryFilter.applyRules(block.text, emptyMap()))
        }
        val transStart = System.currentTimeMillis()
        val translator = translatorFactory.getTranslator(config.translator.translatorType)
        Log.i(TAG, "   Sending ${preFilteredBlocks.size} blocks to ${config.translator.translatorType}...")
        var translatedBlocks = translator.translate(preFilteredBlocks, config.translator)
        Log.i(TAG, "✓ [4/7 TRANSLATION] API finished in ${System.currentTimeMillis() - transStart}ms")

        translatedBlocks = translatedBlocks.mapIndexed { index, block ->
            val targetText = if (block.translatedText.isNotBlank()) block.translatedText else block.text
            val verification = postTranslationVerifier.verify(block.text, targetText, config.translator.targetLang)
            val finalTranslatedText = if (verification.isValid) {
                dictionaryFilter.applyRules(targetText, emptyMap())
            } else {
                targetText
            }
            Log.i(TAG, "   Block $index Result:")
            Log.i(TAG, "      Original:   \"${block.text}\"")
            Log.i(TAG, "      Translated: \"$finalTranslatedText\"")
            Log.i(TAG, "      Valid:      ${verification.isValid} (reason=${verification.reason})")
            
            block.copy(
                translatedText = finalTranslatedText,
                language = config.translator.targetLang
            )
        }

        // 5. Mask Refinement
        currentStage = PipelineStage.MASK_REFINEMENT
        emit(PipelineState.Progress(currentStage, 0.75f, "Refining text mask..."))
        val maskStart = System.currentTimeMillis()
        val refinedMask = maskRefinement.refine(rawMask, textlines, config.inpainter, image.width, image.height)
        if (rawMask != null && !rawMask.isRecycled) {
            try { rawMask.recycle() } catch (_: Throwable) {}
        }
        Log.i(TAG, "✓ [5/7 MASK REFINEMENT] Completed in ${System.currentTimeMillis() - maskStart}ms")

        // 6. Inpainting
        currentStage = PipelineStage.INPAINTING
        emit(PipelineState.Progress(currentStage, 0.85f, "Cleaning original text (Inpainting)..."))
        val inpaintStart = System.currentTimeMillis()
        val inpaintedImage = inpainter.inpaint(image, refinedMask, config.inpainter)
        refinedMask.recycle()
        Log.i(TAG, "✓ [6/7 INPAINTING] Completed in ${System.currentTimeMillis() - inpaintStart}ms")

        // 7. Rendering
        currentStage = PipelineStage.RENDERING
        emit(PipelineState.Progress(currentStage, 0.95f, "Rendering translated text..."))
        val renderStart = System.currentTimeMillis()
        val (finalImage, updatedBlocks) = textRenderer.renderWithUpdatedBlocks(inpaintedImage, translatedBlocks, config.render)
        Log.i(TAG, "✓ [7/7 RENDERING] Completed in ${System.currentTimeMillis() - renderStart}ms")

        // 8. Completed
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        Log.i(TAG, "==================================================")
        Log.i(TAG, "★ TRANSLATION COMPLETED SUCCESSFULLY in ${totalTime}ms")
        Log.i(TAG, "==================================================")

        emit(
            PipelineState.Completed(
                TranslationResult(
                    originalImage = image,
                    translatedImage = finalImage,
                    textBlocks = updatedBlocks,
                    config = config,
                    timestamp = startTime,
                    processingTimeMs = totalTime,
                    inpaintedImage = inpaintedImage
                )
            )
        )
    }.flowOn(Dispatchers.Default)
}

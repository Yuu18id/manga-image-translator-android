package com.yuu18id.mangatranslator.data.ml.ocr

import android.graphics.Bitmap
import android.util.Log
import com.yuu18id.mangatranslator.data.ml.OcrEngine
import com.yuu18id.mangatranslator.domain.model.OcrConfig
import com.yuu18id.mangatranslator.domain.model.OcrType
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositeOcrEngine @Inject constructor(
    private val ctcOcrEngine: CtcOcrEngine,
    private val mangaOcrEngine: MangaOcrEngine
) : OcrEngine {

    companion object {
        private const val TAG = "MangaTranslator"
    }

    override suspend fun recognize(
        image: Bitmap,
        textRegions: List<Quadrilateral>,
        config: OcrConfig
    ): List<Quadrilateral> {
        return when (config.ocrType) {
            OcrType.MANGA_OCR -> {
                Log.i(TAG, "▶ Running Manga-OCR (Full FP32 Vision Transformer)...")
                mangaOcrEngine.recognize(image, textRegions, config)
            }
            else -> {
                Log.i(TAG, "▶ Running CTC OCR (48px ConvNeXt)...")
                ctcOcrEngine.recognize(image, textRegions, config)
            }
        }
    }
}

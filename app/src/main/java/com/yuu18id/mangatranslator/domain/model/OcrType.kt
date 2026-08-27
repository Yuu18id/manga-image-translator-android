package com.yuu18id.mangatranslator.domain.model

import androidx.annotation.StringRes
import com.yuu18id.mangatranslator.R

enum class OcrType(@StringRes val titleResId: Int, val displayName: String) {
    OCR_48PX_CTC(R.string.ocr_engine_ctc, "48px CTC OCR"),
    MANGA_OCR(R.string.ocr_engine_manga_ocr, "Manga-OCR (ViT + RoBERTa)");

    companion object {
        val OCR_48PX = OCR_48PX_CTC
        val OCR_32PX = OCR_48PX_CTC
    }
}

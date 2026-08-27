package com.yuu18id.mangatranslator.domain.model

enum class OcrType(val displayName: String) {
    OCR_48PX_CTC("CTC OCR (Default - Cepat & Multilingual)"),
    MANGA_OCR("Manga-OCR (Full FP32 ViT - Akurasi Maksimal Jepang)");

    companion object {
        val OCR_48PX = OCR_48PX_CTC
        val OCR_32PX = OCR_48PX_CTC
    }
}

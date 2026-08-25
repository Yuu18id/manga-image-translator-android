package com.yuu18id.mangatranslator.domain.model

data class TranslationConfig(
    val detector: DetectorConfig = DetectorConfig(),
    val ocr: OcrConfig = OcrConfig(),
    val translator: TranslatorConfig = TranslatorConfig(
        translatorType = TranslatorType.NONE,
        targetLang = Language.ENG,
        sourceLang = null
    ),
    val inpainter: InpaintConfig = InpaintConfig(),
    val render: RenderConfig = RenderConfig()
)

data class DetectorConfig(
    val detectorType: DetectorType = DetectorType.CTD,
    val detectionSize: Int = 1024,
    val textThreshold: Float = 0.30f,
    val boxThreshold: Float = 0.60f,
    val unclipRatio: Float = 1.50f
)

data class OcrConfig(
    val ocrType: OcrType = OcrType.OCR_48PX_CTC,
    val minTextLength: Int = 0
)

data class TranslatorConfig(
    val translatorType: TranslatorType = TranslatorType.NONE,
    val targetLang: Language = Language.ENG,
    val sourceLang: Language? = null
)

data class InpaintConfig(
    val inpainterType: InpainterType = InpainterType.AOT,
    val inpaintingSize: Int = 512,
    val maskDilationOffset: Int = 20
)

data class RenderConfig(
    val alignment: TextAlignment = TextAlignment.AUTO,
    val direction: TextDirection = TextDirection.AUTO,
    val fontSizeOffset: Int = 0,
    val fontSizeMinimum: Int = -1,
    val disableFontBorder: Boolean = false
)

package com.yuu18id.mangatranslator.domain.model

enum class PipelineStage(val displayName: String, val displayNameId: String) {
    DETECTION("Detection", "stage_detection"),
    OCR("OCR", "stage_ocr"),
    TEXTLINE_MERGE("Textline Merge", "stage_textline_merge"),
    TRANSLATION("Translation", "stage_translation"),
    MASK_REFINEMENT("Mask Refinement", "stage_mask_refinement"),
    INPAINTING("Inpainting", "stage_inpainting"),
    RENDERING("Rendering", "stage_rendering")
}

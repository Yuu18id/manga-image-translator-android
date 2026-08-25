package com.yuu18id.mangatranslator.data.ml

import android.graphics.Bitmap
import com.yuu18id.mangatranslator.domain.model.DetectorConfig
import com.yuu18id.mangatranslator.domain.model.Quadrilateral

interface TextDetector {
    suspend fun detect(bitmap: Bitmap, config: DetectorConfig): DetectionResult

    data class DetectionResult(
        val textlines: List<Quadrilateral>,
        val mask: Bitmap
    )
}

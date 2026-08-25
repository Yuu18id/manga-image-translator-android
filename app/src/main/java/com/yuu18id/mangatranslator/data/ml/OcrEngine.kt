package com.yuu18id.mangatranslator.data.ml

import android.graphics.Bitmap
import com.yuu18id.mangatranslator.domain.model.OcrConfig
import com.yuu18id.mangatranslator.domain.model.Quadrilateral

interface OcrEngine {
    suspend fun recognize(image: Bitmap, textRegions: List<Quadrilateral>, config: OcrConfig): List<Quadrilateral>
}

package com.yuu18id.mangatranslator.data.ml

import android.graphics.Bitmap
import com.yuu18id.mangatranslator.domain.model.OcrConfig
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import com.yuu18id.mangatranslator.domain.model.TextBlock

interface OcrEngine {
    suspend fun recognize(image: Bitmap, textRegions: List<Quadrilateral>, config: OcrConfig): List<Quadrilateral>
    suspend fun recognizeBlocks(image: Bitmap, blocks: List<TextBlock>, config: OcrConfig): List<TextBlock>
}


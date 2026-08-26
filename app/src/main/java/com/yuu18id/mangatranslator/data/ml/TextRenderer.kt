package com.yuu18id.mangatranslator.data.ml

import android.graphics.Bitmap
import com.yuu18id.mangatranslator.domain.model.RenderConfig
import com.yuu18id.mangatranslator.domain.model.TextBlock

interface TextRenderer {
    suspend fun render(inpaintedImage: Bitmap, textBlocks: List<TextBlock>, config: RenderConfig): Bitmap
    suspend fun renderWithUpdatedBlocks(inpaintedImage: Bitmap, textBlocks: List<TextBlock>, config: RenderConfig): Pair<Bitmap, List<TextBlock>>
}

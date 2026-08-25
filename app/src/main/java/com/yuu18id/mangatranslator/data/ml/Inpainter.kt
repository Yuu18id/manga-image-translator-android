package com.yuu18id.mangatranslator.data.ml

import android.graphics.Bitmap
import com.yuu18id.mangatranslator.domain.model.InpaintConfig

interface Inpainter {
    suspend fun inpaint(image: Bitmap, mask: Bitmap, config: InpaintConfig): Bitmap
}

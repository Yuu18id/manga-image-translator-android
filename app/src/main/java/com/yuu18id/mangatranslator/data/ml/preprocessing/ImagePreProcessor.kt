package com.yuu18id.mangatranslator.data.ml.preprocessing

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.nio.FloatBuffer
import javax.inject.Inject

data class PreProcessMetadata(
    val originalWidth: Int,
    val originalHeight: Int,
    val scaleX: Float,
    val scaleY: Float,
    val padLeft: Int,
    val padTop: Int
)

data class PreProcessResult(
    val tensor: OnnxTensor,
    val metadata: PreProcessMetadata
)

class ImagePreProcessor @Inject constructor(
    private val ortEnvironment: OrtEnvironment
) {
    fun prepareForDetection(bitmap: Bitmap, targetSize: Int): PreProcessResult {
        val (padded, paddingInfo) = TensorUtils.resizeWithPadding(bitmap, targetSize)
        val tensor = TensorUtils.bitmapToFloatTensor(
            ortEnvironment,
            padded,
            TensorUtils.NormMode.ZERO_ONE
        )
        val metadata = PreProcessMetadata(
            originalWidth = paddingInfo.originalWidth,
            originalHeight = paddingInfo.originalHeight,
            scaleX = paddingInfo.scale,
            scaleY = paddingInfo.scale,
            padLeft = paddingInfo.padLeft,
            padTop = paddingInfo.padTop
        )
        return PreProcessResult(tensor, metadata)
    }

    fun prepareForOcr(bitmap: Bitmap, regions: List<FloatArray>, targetHeight: Int = 48): List<PreProcessResult> {
        return regions.map { region ->
            val cropped = TensorUtils.cropAndWarpQuadrilateral(bitmap, region, targetHeight)
            val tensor = TensorUtils.bitmapToFloatTensor(
                ortEnvironment,
                cropped,
                TensorUtils.NormMode.NEG_ONE_ONE
            )
            val metadata = PreProcessMetadata(
                originalWidth = cropped.width,
                originalHeight = cropped.height,
                scaleX = 1f,
                scaleY = 1f,
                padLeft = 0,
                padTop = 0
            )
            PreProcessResult(tensor, metadata)
        }
    }

    fun prepareForInpainting(image: Bitmap, mask: Bitmap, targetSize: Int): PreProcessResult {
        val (paddedImage, paddingInfo) = TensorUtils.resizeWithPadding(image, targetSize)
        val (paddedMask, _) = TensorUtils.resizeWithPadding(mask, targetSize)
        
        val imagePixels = IntArray(targetSize * targetSize)
        val maskPixels = IntArray(targetSize * targetSize)
        
        paddedImage.getPixels(imagePixels, 0, targetSize, 0, 0, targetSize, targetSize)
        paddedMask.getPixels(maskPixels, 0, targetSize, 0, 0, targetSize, targetSize)

        val buffer = FloatBuffer.allocate(1 * 4 * targetSize * targetSize)
        val mOffset = 0
        val rOffset = targetSize * targetSize
        val gOffset = 2 * targetSize * targetSize
        val bOffset = 3 * targetSize * targetSize

        for (i in imagePixels.indices) {
            val imgPixel = imagePixels[i]
            val maskPixel = maskPixels[i]

            val r = (((imgPixel shr 16) and 0xFF) / 127.5f) - 1.0f
            val g = (((imgPixel shr 8) and 0xFF) / 127.5f) - 1.0f
            val b = ((imgPixel and 0xFF) / 127.5f) - 1.0f
            
            val m = ((maskPixel and 0xFF) / 255.0f)

            buffer.put(mOffset + i, m)
            buffer.put(rOffset + i, r)
            buffer.put(gOffset + i, g)
            buffer.put(bOffset + i, b)
        }
        
        buffer.rewind()
        val tensor = OnnxTensor.createTensor(ortEnvironment, buffer, longArrayOf(1, 4, targetSize.toLong(), targetSize.toLong()))
        
        val metadata = PreProcessMetadata(
            originalWidth = paddingInfo.originalWidth,
            originalHeight = paddingInfo.originalHeight,
            scaleX = paddingInfo.scale,
            scaleY = paddingInfo.scale,
            padLeft = paddingInfo.padLeft,
            padTop = paddingInfo.padTop
        )
        return PreProcessResult(tensor, metadata)
    }
}

package com.yuu18id.mangatranslator.data.ml.inpainting

import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import com.yuu18id.mangatranslator.data.ml.Inpainter
import com.yuu18id.mangatranslator.data.ml.OnnxModelManager
import com.yuu18id.mangatranslator.domain.model.InpaintConfig
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AotInpainter @Inject constructor(
    private val onnxModelManager: OnnxModelManager
) : Inpainter {

    @Suppress("UNCHECKED_CAST")
    override suspend fun inpaint(image: Bitmap, mask: Bitmap, config: InpaintConfig): Bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val env = onnxModelManager.ortEnvironment
        val session = onnxModelManager.createSession(OnnxModelManager.ModelType.AOT_INPAINTER, useNnapi = false)

        val inputTensorInfo = session.inputInfo.values.firstOrNull()?.info as? ai.onnxruntime.TensorInfo
        val shape = inputTensorInfo?.shape
        val modelTargetH = shape?.getOrNull(2)?.takeIf { it > 0 }?.toInt() ?: 512
        val targetSize = if (modelTargetH > 0) modelTargetH else 512
        val targetH = targetSize
        val targetW = targetSize

        val resizedImage = Bitmap.createScaledBitmap(image, targetW, targetH, true)
        val resizedMask = Bitmap.createScaledBitmap(mask, targetW, targetH, true)

        var inputTensor: OnnxTensor? = null
        var result: ai.onnxruntime.OrtSession.Result? = null

        try {
            inputTensor = prepareInputTensor(resizedImage, resizedMask, env)
            val inputName = session.inputNames.iterator().next()
            result = session.run(mapOf(inputName to inputTensor))

            val outputTensor = result.get(0) as OnnxTensor
            val floatBuffer = outputTensor.floatBuffer
            val outputFloatArray = FloatArray(floatBuffer.remaining())
            floatBuffer.get(outputFloatArray)

            val inpaintedResized = denormalizeOutput(outputFloatArray, targetW, targetH)
            val inpaintedOriginalSize = Bitmap.createScaledBitmap(inpaintedResized, image.width, image.height, true)
            val finalResult = blendBitmaps(image, inpaintedOriginalSize, mask)

            if (inpaintedResized != inpaintedOriginalSize) inpaintedResized.recycle()
            if (inpaintedOriginalSize != finalResult) inpaintedOriginalSize.recycle()

            return@withContext finalResult
        } finally {
            if (resizedImage != image) resizedImage.recycle()
            if (resizedMask != mask) resizedMask.recycle()
            inputTensor?.close()
            result?.close()
        }
    }

    private fun prepareInputTensor(image: Bitmap, mask: Bitmap, env: OrtEnvironment): OnnxTensor {
        val w = image.width
        val h = image.height
        val byteBuffer = java.nio.ByteBuffer.allocateDirect(1 * 4 * h * w * 4)
            .order(java.nio.ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()

        val imagePixels = IntArray(w * h)
        image.getPixels(imagePixels, 0, w, 0, 0, w, h)
        
        val maskPixels = IntArray(w * h)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val isMaskedArray = BooleanArray(w * h)

        // Channel 0: Text Mask (1.0 for text to erase, 0.0 for background)
        for (i in 0 until h * w) {
            val maskPixel = maskPixels[i]
            val r = Color.red(maskPixel)
            val isMasked = r >= 128
            isMaskedArray[i] = isMasked
            floatBuffer.put(if (isMasked) 1.0f else 0.0f)
        }

        // Channels 1..3: Normalized RGB [-1.0, 1.0], multiplied by (1 - mask)
        for (c in 0..2) {
            for (i in 0 until h * w) {
                if (isMaskedArray[i]) {
                    // Black out masked area: img_torch *= (1 - mask_torch)
                    floatBuffer.put(0.0f)
                } else {
                    val pixel = imagePixels[i]
                    val value = when (c) {
                        0 -> Color.red(pixel)
                        1 -> Color.green(pixel)
                        2 -> Color.blue(pixel)
                        else -> 0
                    }
                    val normValue = (value / 127.5f) - 1.0f
                    floatBuffer.put(normValue)
                }
            }
        }
        
        floatBuffer.rewind()
        return OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 4, h.toLong(), w.toLong()))
    }

    private fun denormalizeOutput(floatArray: FloatArray, w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        
        val channelSize = w * h
        for (i in 0 until channelSize) {
            val r = ((floatArray[i] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((floatArray[channelSize + i] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((floatArray[2 * channelSize + i] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
            
            pixels[i] = Color.rgb(r, g, b)
        }
        
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun blendBitmaps(original: Bitmap, inpainted: Bitmap, mask: Bitmap): Bitmap {
        val w = original.width
        val h = original.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        
        val origPixels = IntArray(w * h)
        val inpaintPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        val resultPixels = IntArray(w * h)
        
        original.getPixels(origPixels, 0, w, 0, 0, w, h)
        inpainted.getPixels(inpaintPixels, 0, w, 0, 0, w, h)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)
        
        for (i in 0 until w * h) {
            val maskVal = Color.red(maskPixels[i])
            if (maskVal > 128) {
                // Inpainted pixel in text bubble
                resultPixels[i] = inpaintPixels[i]
            } else if (maskVal > 0) {
                // Smooth anti-aliased edge blending
                val m = maskVal / 255.0f
                val oR = Color.red(origPixels[i])
                val oG = Color.green(origPixels[i])
                val oB = Color.blue(origPixels[i])
                
                val iR = Color.red(inpaintPixels[i])
                val iG = Color.green(inpaintPixels[i])
                val iB = Color.blue(inpaintPixels[i])
                
                val rR = (oR * (1f - m) + iR * m).toInt().coerceIn(0, 255)
                val rG = (oG * (1f - m) + iG * m).toInt().coerceIn(0, 255)
                val rB = (oB * (1f - m) + iB * m).toInt().coerceIn(0, 255)
                resultPixels[i] = Color.rgb(rR, rG, rB)
            } else {
                // Untouched original pixel
                resultPixels[i] = origPixels[i]
            }
        }
        
        result.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return result
    }
}

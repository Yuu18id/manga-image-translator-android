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
        val totalPixels = w * h
        val byteBuffer = java.nio.ByteBuffer.allocateDirect(1 * 4 * h * w * 4)
            .order(java.nio.ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()

        val imagePixels = IntArray(totalPixels)
        image.getPixels(imagePixels, 0, w, 0, 0, w, h)
        
        val maskPixels = IntArray(totalPixels)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val inv127 = 1.0f / 127.5f

        // Channel 0: Text Mask (1.0 for text to erase, 0.0 for background)
        for (i in 0 until totalPixels) {
            val isMasked = (maskPixels[i] and 0xFF) >= 128
            floatBuffer.put(i, if (isMasked) 1.0f else 0.0f)
        }

        // Channels 1..3: Normalized RGB [-1.0, 1.0], multiplied by (1 - mask)
        val planeR = totalPixels
        val planeG = 2 * totalPixels
        val planeB = 3 * totalPixels

        for (i in 0 until totalPixels) {
            val isMasked = (maskPixels[i] and 0xFF) >= 128
            if (isMasked) {
                floatBuffer.put(planeR + i, 0.0f)
                floatBuffer.put(planeG + i, 0.0f)
                floatBuffer.put(planeB + i, 0.0f)
            } else {
                val pixel = imagePixels[i]
                val rNorm = (((pixel shr 16) and 0xFF) * inv127) - 1.0f
                val gNorm = (((pixel shr 8) and 0xFF) * inv127) - 1.0f
                val bNorm = ((pixel and 0xFF) * inv127) - 1.0f
                floatBuffer.put(planeR + i, rNorm)
                floatBuffer.put(planeG + i, gNorm)
                floatBuffer.put(planeB + i, bNorm)
            }
        }
        
        floatBuffer.position(0)
        return OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 4, h.toLong(), w.toLong()))
    }

    private fun denormalizeOutput(floatArray: FloatArray, w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val totalPixels = w * h
        val pixels = IntArray(totalPixels)
        
        val planeG = totalPixels
        val planeB = 2 * totalPixels
        for (i in 0 until totalPixels) {
            val r = ((floatArray[i] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((floatArray[planeG + i] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((floatArray[planeB + i] + 1.0f) * 127.5f).toInt().coerceIn(0, 255)
            
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun blendBitmaps(original: Bitmap, inpainted: Bitmap, mask: Bitmap): Bitmap {
        val origMat = org.opencv.core.Mat()
        val inpaintMat = org.opencv.core.Mat()
        val maskMat = org.opencv.core.Mat()
        val maskGray = org.opencv.core.Mat()
        val resultMat = org.opencv.core.Mat()

        try {
            org.opencv.android.Utils.bitmapToMat(original, origMat)
            org.opencv.android.Utils.bitmapToMat(inpainted, inpaintMat)
            org.opencv.android.Utils.bitmapToMat(mask, maskMat)

            if (maskMat.channels() > 1) {
                org.opencv.imgproc.Imgproc.cvtColor(maskMat, maskGray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)
            } else {
                maskMat.copyTo(maskGray)
            }

            origMat.copyTo(resultMat)
            inpaintMat.copyTo(resultMat, maskGray)

            val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(resultMat, result)
            return result
        } finally {
            origMat.release()
            inpaintMat.release()
            maskMat.release()
            maskGray.release()
            resultMat.release()
        }
    }
}

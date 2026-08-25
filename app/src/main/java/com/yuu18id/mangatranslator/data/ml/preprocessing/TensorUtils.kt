package com.yuu18id.mangatranslator.data.ml.preprocessing

import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.nio.FloatBuffer
import kotlin.math.max

object TensorUtils {
    enum class NormMode { ZERO_ONE, NEG_ONE_ONE, IMAGENET }

    fun bitmapToFloatTensor(
        env: OrtEnvironment,
        bitmap: Bitmap,
        normMode: NormMode = NormMode.ZERO_ONE
    ): OnnxTensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val buffer = FloatBuffer.allocate(1 * 3 * height * width)
        val rOffset = 0
        val gOffset = height * width
        val bOffset = 2 * height * width

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            val (nr, ng, nb) = when (normMode) {
                NormMode.ZERO_ONE -> Triple(r, g, b)
                NormMode.NEG_ONE_ONE -> Triple(
                    r * 2.0f - 1.0f,
                    g * 2.0f - 1.0f,
                    b * 2.0f - 1.0f
                )
                NormMode.IMAGENET -> Triple(
                    (r - 0.485f) / 0.229f,
                    (g - 0.456f) / 0.224f,
                    (b - 0.406f) / 0.225f
                )
            }
            buffer.put(rOffset + i, nr)
            buffer.put(gOffset + i, ng)
            buffer.put(bOffset + i, nb)
        }

        buffer.rewind()
        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    fun floatTensorToBitmap(
        tensorArray: FloatArray,
        width: Int,
        height: Int,
        normMode: NormMode = NormMode.ZERO_ONE
    ): Bitmap {
        val pixels = IntArray(width * height)
        val channelSize = width * height

        for (i in 0 until channelSize) {
            val rVal = tensorArray[i]
            val gVal = tensorArray[i + channelSize]
            val bVal = tensorArray[i + 2 * channelSize]

            val (dr, dg, db) = when (normMode) {
                NormMode.ZERO_ONE -> Triple(rVal, gVal, bVal)
                NormMode.NEG_ONE_ONE -> Triple(
                    (rVal + 1.0f) / 2.0f,
                    (gVal + 1.0f) / 2.0f,
                    (bVal + 1.0f) / 2.0f
                )
                NormMode.IMAGENET -> Triple(
                    rVal * 0.229f + 0.485f,
                    gVal * 0.224f + 0.456f,
                    bVal * 0.225f + 0.406f
                )
            }

            val r = (dr * 255).toInt().coerceIn(0, 255)
            val g = (dg * 255).toInt().coerceIn(0, 255)
            val b = (db * 255).toInt().coerceIn(0, 255)

            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun cropAndWarpQuadrilateral(bitmap: Bitmap, points: FloatArray, targetHeight: Int): Bitmap {
        val w1 = Math.hypot((points[2] - points[0]).toDouble(), (points[3] - points[1]).toDouble())
        val w2 = Math.hypot((points[6] - points[4]).toDouble(), (points[7] - points[5]).toDouble())
        val maxWidth = max(w1, w2).toInt()

        val h1 = Math.hypot((points[4] - points[2]).toDouble(), (points[5] - points[3]).toDouble())
        val h2 = Math.hypot((points[6] - points[0]).toDouble(), (points[7] - points[1]).toDouble())
        val maxHeight = max(h1, h2).toInt()

        if (maxWidth <= 0 || maxHeight <= 0) {
             return Bitmap.createBitmap(1, targetHeight, Bitmap.Config.ARGB_8888)
        }

        val aspect = maxWidth.toFloat() / maxHeight.toFloat()
        val targetWidth = (targetHeight * aspect).toInt().coerceAtLeast(1)

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        val srcPixels = IntArray(srcWidth * srcHeight)
        bitmap.getPixels(srcPixels, 0, srcWidth, 0, 0, srcWidth, srcHeight)

        val destPixels = IntArray(targetWidth * targetHeight)

        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val u = x.toFloat() / targetWidth
                val v = y.toFloat() / targetHeight
                
                val px1 = points[0] + (points[6] - points[0]) * v
                val py1 = points[1] + (points[7] - points[1]) * v
                
                val px2 = points[2] + (points[4] - points[2]) * v
                val py2 = points[3] + (points[5] - points[3]) * v

                val mapX = (px1 + (px2 - px1) * u).toInt().coerceIn(0, srcWidth - 1)
                val mapY = (py1 + (py2 - py1) * u).toInt().coerceIn(0, srcHeight - 1)
                
                destPixels[y * targetWidth + x] = srcPixels[mapY * srcWidth + mapX]
            }
        }

        output.setPixels(destPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        return output
    }

    data class PaddingInfo(val originalWidth: Int, val originalHeight: Int, val padLeft: Int, val padTop: Int, val scale: Float)

    fun resizeWithPadding(bitmap: Bitmap, targetSize: Int): Pair<Bitmap, PaddingInfo> {
        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        var newWidth: Int
        var newHeight: Int
        val scale: Float

        if (aspect > 1f) {
            newWidth = targetSize
            newHeight = (targetSize / aspect).toInt()
            scale = targetSize.toFloat() / bitmap.width.toFloat()
        } else {
            newHeight = targetSize
            newWidth = (targetSize * aspect).toInt()
            scale = targetSize.toFloat() / bitmap.height.toFloat()
        }

        val resized = Bitmap.createScaledBitmap(bitmap, newWidth.coerceAtLeast(1), newHeight.coerceAtLeast(1), true)
        val padded = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        
        val padLeft = (targetSize - newWidth) / 2
        val padTop = (targetSize - newHeight) / 2
        
        val canvas = android.graphics.Canvas(padded)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
        canvas.drawRect(0f, 0f, targetSize.toFloat(), targetSize.toFloat(), paint)
        canvas.drawBitmap(resized, padLeft.toFloat(), padTop.toFloat(), null)

        return Pair(padded, PaddingInfo(bitmap.width, bitmap.height, padLeft, padTop, scale))
    }

    fun concatTensors(
        env: OrtEnvironment,
        tensors: List<FloatArray>,
        shapes: List<LongArray>
    ): OnnxTensor {
        val c = shapes[0][1].toInt()
        val h = shapes[0][2].toInt()
        val maxW = shapes.maxOf { it[3] }.toInt()
        val batchSize = tensors.size

        val batchedBuffer = FloatBuffer.allocate(batchSize * c * h * maxW)
        
        for (b in 0 until batchSize) {
            val tensor = tensors[b]
            val w = shapes[b][3].toInt()
            
            for (ch in 0 until c) {
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val srcIdx = ch * h * w + y * w + x
                        val destIdx = b * c * h * maxW + ch * h * maxW + y * maxW + x
                        batchedBuffer.put(destIdx, tensor[srcIdx])
                    }
                }
            }
        }
        
        batchedBuffer.rewind()
        return OnnxTensor.createTensor(env, batchedBuffer, longArrayOf(batchSize.toLong(), c.toLong(), h.toLong(), maxW.toLong()))
    }
}

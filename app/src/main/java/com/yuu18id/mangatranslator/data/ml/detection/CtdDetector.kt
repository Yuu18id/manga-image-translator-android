package com.yuu18id.mangatranslator.data.ml.detection

import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap
import com.yuu18id.mangatranslator.data.ml.OnnxModelManager
import com.yuu18id.mangatranslator.data.ml.TextDetector
import com.yuu18id.mangatranslator.domain.model.DetectorConfig
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class CtdDetector @Inject constructor(
    private val modelManager: OnnxModelManager
) : TextDetector {

    companion object {
        private const val TARGET_SIZE = 1024
    }

    override suspend fun detect(
        image: Bitmap,
        config: DetectorConfig
    ): TextDetector.DetectionResult = withContext(Dispatchers.Default) {
        val session = modelManager.createSession(OnnxModelManager.ModelType.CTD_DETECTOR, useNnapi = false)
        val env = modelManager.ortEnvironment

        val originalW = image.width
        val originalH = image.height

        val mat = Mat()
        Utils.bitmapToMat(image, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)

        // Letterbox resizing (1024x1024)
        val targetSize = config.detectionSize.coerceAtLeast(TARGET_SIZE)
        val scale = min(targetSize.toFloat() / originalW, targetSize.toFloat() / originalH)
        val newW = (originalW * scale).toInt()
        val newH = (originalH * scale).toInt()

        val resizedMat = Mat()
        Imgproc.resize(mat, resizedMat, Size(newW.toDouble(), newH.toDouble()))

        val dw = targetSize - newW
        val dh = targetSize - newH
        val paddedMat = Mat()
        Core.copyMakeBorder(resizedMat, paddedMat, 0, dh, 0, dw, Core.BORDER_CONSTANT, org.opencv.core.Scalar(127.5, 127.5, 127.5))

        // Convert to FloatArray CHW format, normalized to [0, 1]
        val floatArray = FloatArray(3 * targetSize * targetSize)
        val bytes = ByteArray(targetSize * targetSize * 3)
        paddedMat.get(0, 0, bytes)

        val planeSize = targetSize * targetSize
        for (h in 0 until targetSize) {
            for (w in 0 until targetSize) {
                val baseIdx = (h * targetSize + w) * 3
                val r = (bytes[baseIdx].toInt() and 0xFF) / 255.0f
                val g = (bytes[baseIdx + 1].toInt() and 0xFF) / 255.0f
                val b = (bytes[baseIdx + 2].toInt() and 0xFF) / 255.0f
                
                val pixelOffset = h * targetSize + w
                floatArray[pixelOffset] = r
                floatArray[planeSize + pixelOffset] = g
                floatArray[2 * planeSize + pixelOffset] = b
            }
        }

        val floatBuffer = FloatBuffer.wrap(floatArray)
        val inputTensor = OnnxTensor.createTensor(
            env,
            floatBuffer,
            longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong())
        )

        val inputName = session.inputNames.iterator().next()
        val result = session.run(mapOf(inputName to inputTensor))

        // Outputs: [0] blk [1, 64512, 7], [1] seg (mask [1, 1, 1024, 1024]), [2] det (lines [1, 2, 1024, 1024])
        val maskOnnxTensor = result.get(1) as OnnxTensor
        val linesOnnxTensor = result.get(2) as OnnxTensor

        val maskFlat = FloatArray(targetSize * targetSize)
        val linesFlat = FloatArray(targetSize * targetSize)

        maskOnnxTensor.floatBuffer.get(maskFlat)
        // det has 2 channels; channel 0 contains the textline probability heatmap
        linesOnnxTensor.floatBuffer.get(linesFlat)

        val maskMat = Mat(targetSize, targetSize, CvType.CV_32FC1)
        val linesMat = Mat(targetSize, targetSize, CvType.CV_32FC1)
        maskMat.put(0, 0, maskFlat)
        linesMat.put(0, 0, linesFlat)

        // Unpad from bottom/right: submat(0, newH, 0, newW)
        val maskCropped = maskMat.submat(0, newH, 0, newW)
        val linesCropped = linesMat.submat(0, newH, 0, newW)

        val maskResized = Mat()
        Imgproc.resize(maskCropped, maskResized, Size(originalW.toDouble(), originalH.toDouble()))
        val linesResized = Mat()
        Imgproc.resize(linesCropped, linesResized, Size(originalW.toDouble(), originalH.toDouble()))

        // Binarize using textThreshold, then evaluate contours with boxThreshold
        val binaryLines = DetectionPostProcessor.binarize(linesResized, config.textThreshold)
        val contours = ArrayList<org.opencv.core.MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binaryLines, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val textlines = mutableListOf<Pair<List<Point>, Float>>()
        for (contour in contours) {
            if (contour.rows() < 4) {
                contour.release()
                continue
            }
            val (box, shortSide) = DetectionPostProcessor.getMiniBoxes(contour)
            if (shortSide < 3) {
                contour.release()
                continue
            }
            
            // Score against the actual text contour strokes (matching Python CTD SegDetectorRepresenter)
            val contourPoints = contour.toList()
            val score = DetectionPostProcessor.boxScoreFast(linesResized, contourPoints)
            if (score < config.boxThreshold) {
                contour.release()
                continue
            }
            
            val unclippedBox = DetectionPostProcessor.unclipContour(contour, config.unclipRatio)
            textlines.add(Pair(unclippedBox, score))
            contour.release()
        }

        val nmsBoxes = DetectionPostProcessor.nonMaxSuppression(textlines, 0.3f)
        val quadrilaterals = nmsBoxes.map { (pts, score) ->
            val pointFs = pts.map { android.graphics.PointF(it.x.toFloat(), it.y.toFloat()) }
            Quadrilateral.fromPoints(pointFs, text = "", prob = score)
        }

        // Convert mask to 8-bit bitmap [0, 255]
        val mask8u = Mat()
        maskResized.convertTo(mask8u, CvType.CV_8UC1, 255.0)
        val maskBitmap = Bitmap.createBitmap(originalW, originalH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mask8u, maskBitmap)

        // Native memory cleanup
        inputTensor.close()
        result.close()
        mat.release()
        resizedMat.release()
        paddedMat.release()
        maskMat.release()
        linesMat.release()
        maskCropped.release()
        linesCropped.release()
        maskResized.release()
        linesResized.release()
        binaryLines.release()
        hierarchy.release()
        mask8u.release()

        TextDetector.DetectionResult(
            textlines = quadrilaterals,
            mask = maskBitmap
        )
    }
}

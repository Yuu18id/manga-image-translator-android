package com.yuu18id.mangatranslator.data.ml.detection

import ai.onnxruntime.OnnxTensor
import android.graphics.Bitmap
import android.graphics.PointF
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
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import javax.inject.Inject

class DbNetDetector @Inject constructor(
    private val onnxModelManager: OnnxModelManager
) : TextDetector {

    override suspend fun detect(
        bitmap: Bitmap,
        config: DetectorConfig
    ): TextDetector.DetectionResult = withContext(Dispatchers.Default) {
        val session = onnxModelManager.createSession(OnnxModelManager.ModelType.CTD_DETECTOR) // Replace when DBNET is available
        val env = onnxModelManager.ortEnvironment

        val targetSize = config.detectionSize
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)

        val originalW = mat.cols()
        val originalH = mat.rows()

        val resizedMat = Mat()
        Imgproc.resize(mat, resizedMat, Size(targetSize.toDouble(), targetSize.toDouble()))

        val flatArray = FloatArray(3 * targetSize * targetSize)
        val planeSize = targetSize * targetSize
        for (y in 0 until targetSize) {
            for (x in 0 until targetSize) {
                val pixel = resizedMat.get(y, x)
                // Normalize [-1, 1]
                flatArray[0 * planeSize + y * targetSize + x] = ((pixel[2] / 127.5) - 1.0).toFloat() // R
                flatArray[1 * planeSize + y * targetSize + x] = ((pixel[1] / 127.5) - 1.0).toFloat() // G
                flatArray[2 * planeSize + y * targetSize + x] = ((pixel[0] / 127.5) - 1.0).toFloat() // B
            }
        }
        val floatBuffer = FloatBuffer.wrap(flatArray)

        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 3, targetSize.toLong(), targetSize.toLong()))
        val result = session.run(mapOf(session.inputNames.iterator().next() to inputTensor))

        // We assume output [0] is probability map, [1] is threshold map or segmentation mask
        val probOutput = result[0].value as Array<Array<Array<FloatArray>>>
        
        val probMat = Mat(targetSize, targetSize, CvType.CV_32FC1)
        for (y in 0 until targetSize) {
            for (x in 0 until targetSize) {
                probMat.put(y, x, probOutput[0][0][y][x].toDouble())
            }
        }

        val probResized = Mat()
        Imgproc.resize(probMat, probResized, Size(originalW.toDouble(), originalH.toDouble()))

        val binaryProb = DetectionPostProcessor.binarize(probResized, config.textThreshold)
        val contours = ArrayList<org.opencv.core.MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binaryProb, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

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
            val contourPoints = contour.toList()
            val score = DetectionPostProcessor.boxScoreFast(probResized, contourPoints)
            if (score < config.boxThreshold) {
                contour.release()
                continue
            }
            
            val unclippedBox = DetectionPostProcessor.unclipContour(contour, config.unclipRatio)
            textlines.add(Pair(unclippedBox, score))
            contour.release()
        }

        val finalLines = textlines.map { pair ->
            val clampedPts = DetectionPostProcessor.expandQuadrilateral(
                points = pair.first,
                imageWidth = originalW,
                imageHeight = originalH
            )
            Quadrilateral(
                pts = listOf(
                    PointF(clampedPts[0].x.toFloat(), clampedPts[0].y.toFloat()),
                    PointF(clampedPts[1].x.toFloat(), clampedPts[1].y.toFloat()),
                    PointF(clampedPts[2].x.toFloat(), clampedPts[2].y.toFloat()),
                    PointF(clampedPts[3].x.toFloat(), clampedPts[3].y.toFloat())
                ),
                prob = pair.second
            )
        }

        val finalMaskBitmap = Bitmap.createBitmap(originalW, originalH, Bitmap.Config.ARGB_8888)
        val maskDisplayMat = Mat()
        probResized.convertTo(maskDisplayMat, CvType.CV_8UC1, 255.0)
        Utils.matToBitmap(maskDisplayMat, finalMaskBitmap)

        mat.release()
        resizedMat.release()
        inputTensor.close()
        result.close()
        probMat.release()
        probResized.release()
        binaryProb.release()
        hierarchy.release()
        maskDisplayMat.release()

        TextDetector.DetectionResult(finalLines, finalMaskBitmap)
    }
}

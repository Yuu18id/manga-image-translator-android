package com.yuu18id.mangatranslator.data.mask

import android.graphics.Bitmap
import com.yuu18id.mangatranslator.domain.model.InpaintConfig
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class MaskRefinement @Inject constructor() {

    suspend fun refine(rawMask: Bitmap?, textlines: List<Quadrilateral>, config: InpaintConfig, fallbackWidth: Int = 1024, fallbackHeight: Int = 1024): Bitmap {
        val imgW = rawMask?.width ?: fallbackWidth
        val imgH = rawMask?.height ?: fallbackHeight

        if (textlines.isEmpty()) {
            return Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
        }

        val rawGray = Mat()
        if (rawMask != null && !rawMask.isRecycled) {
            val rawMat = Mat()
            Utils.bitmapToMat(rawMask, rawMat)
            if (rawMat.channels() > 1) {
                Imgproc.cvtColor(rawMat, rawGray, Imgproc.COLOR_RGBA2GRAY)
            } else {
                rawMat.copyTo(rawGray)
            }
            rawMat.release()
        } else {
            Mat.zeros(imgH, imgW, CvType.CV_8UC1).copyTo(rawGray)
        }

        val refinedMat = Mat.zeros(imgH, imgW, CvType.CV_8UC1)

        // Only refine and keep masks INSIDE the detected/curated text lines
        for (line in textlines) {
            val bounds = line.boundingRect()
            
            // Expand window by 15% to ensure full stroke coverage
            val padX = max(6, (bounds.width() * 0.15f).toInt())
            val padY = max(6, (bounds.height() * 0.15f).toInt())
            
            val x1 = max(0, (bounds.left - padX).toInt())
            val y1 = max(0, (bounds.top - padY).toInt())
            val x2 = min(imgW, (bounds.right + padX).toInt())
            val y2 = min(imgH, (bounds.bottom + padY).toInt())
            
            val w = x2 - x1
            val h = y2 - y1
            if (w <= 0 || h <= 0) continue

            val roiRect = Rect(x1, y1, w, h)
            val rawRoi = rawGray.submat(roiRect)
            val refinedRoi = refinedMat.submat(roiRect)

            val nonZero = Core.countNonZero(rawRoi)
            if (nonZero > 15) {
                // Binarize ROI mask from detector
                val threshRoi = Mat()
                Imgproc.threshold(rawRoi, threshRoi, 30.0, 255.0, Imgproc.THRESH_BINARY)

                // Dilation to cover font edges
                val kSize = (config.maskDilationOffset.coerceIn(3, 9)).toDouble()
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(kSize, kSize))
                val dilatedRoi = Mat()
                Imgproc.dilate(threshRoi, dilatedRoi, kernel)

                // Merge into refined output
                Core.bitwise_or(refinedRoi, dilatedRoi, refinedRoi)

                threshRoi.release()
                kernel.release()
                dilatedRoi.release()
            } else {
                // User-added box or unsegmented text: fill polygon with white
                val roiPts = line.pts.map { org.opencv.core.Point((it.x - x1).toDouble(), (it.y - y1).toDouble()) }
                val matOfPoint = org.opencv.core.MatOfPoint(*roiPts.toTypedArray())
                Imgproc.fillConvexPoly(refinedRoi, matOfPoint, org.opencv.core.Scalar(255.0))
                matOfPoint.release()
            }

            rawRoi.release()
            refinedRoi.release()
        }

        val refinedMaskBitmap = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(refinedMat, refinedMaskBitmap)

        rawGray.release()
        refinedMat.release()

        return refinedMaskBitmap
    }
}

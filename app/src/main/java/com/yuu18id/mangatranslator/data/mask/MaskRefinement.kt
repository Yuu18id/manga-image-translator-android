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

    suspend fun refine(rawMask: Bitmap, textlines: List<Quadrilateral>, config: InpaintConfig): Bitmap {
        if (textlines.isEmpty()) {
            return Bitmap.createBitmap(rawMask.width, rawMask.height, Bitmap.Config.ARGB_8888)
        }

        val rawMat = Mat()
        Utils.bitmapToMat(rawMask, rawMat)
        
        val rawGray = Mat()
        if (rawMat.channels() > 1) {
            Imgproc.cvtColor(rawMat, rawGray, Imgproc.COLOR_RGBA2GRAY)
        } else {
            rawMat.copyTo(rawGray)
        }

        val refinedMat = Mat.zeros(rawMask.height, rawMask.width, CvType.CV_8UC1)
        val imgW = rawMask.width
        val imgH = rawMask.height

        // Only refine and keep masks INSIDE the detected text lines
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

            // Binarize ROI mask
            val threshRoi = Mat()
            Imgproc.threshold(rawRoi, threshRoi, 40.0, 255.0, Imgproc.THRESH_BINARY)

            // Dilation to cover font edges
            val kSize = (config.maskDilationOffset.coerceIn(3, 9)).toDouble()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(kSize, kSize))
            val dilatedRoi = Mat()
            Imgproc.dilate(threshRoi, dilatedRoi, kernel)

            // Merge into refined output
            Core.bitwise_or(refinedRoi, dilatedRoi, refinedRoi)

            rawRoi.release()
            refinedRoi.release()
            threshRoi.release()
            kernel.release()
            dilatedRoi.release()
        }

        val refinedMaskBitmap = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(refinedMat, refinedMaskBitmap)

        rawMat.release()
        rawGray.release()
        refinedMat.release()

        return refinedMaskBitmap
    }
}

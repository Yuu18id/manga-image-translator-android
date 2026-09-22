package com.yuu18id.mangatranslator.data.ml.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import com.yuu18id.mangatranslator.domain.model.TextBlock
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class OcrPreProcessor @Inject constructor() {

    fun cropBlockForMangaOcr(image: Bitmap, block: TextBlock): Bitmap {
        val rect = block.mergedBoundingBox()
        val estFontSize = if (block.lines.isNotEmpty()) {
            block.lines.map { min(it.boundingRect().width(), it.boundingRect().height()) }.average().toFloat()
        } else {
            min(rect.width(), rect.height()) / max(1, block.lines.size)
        }

        // Bounded font-aware padding:
        // - For standard/wide bubbles: generous padding (12-18px or ~35% font size) to prevent edge clipping.
        // - For narrow/small bubbles: bound padding to at most 30% of dimension so text doesn't shrink into a hairline in 224x224.
        val targetPadX = max(6f, max(estFontSize * 0.35f, rect.width() * 0.12f))
        val targetPadY = max(8f, max(estFontSize * 0.35f, rect.height() * 0.10f))
        val padX = min(targetPadX, max(4f, rect.width() * 0.30f)).toInt()
        val padY = min(targetPadY, max(6f, rect.height() * 0.25f)).toInt()

        val minX = max(0, rect.left.toInt() - padX)
        val minY = max(0, rect.top.toInt() - padY)
        val maxX = min(image.width, rect.right.toInt() + padX)
        val maxY = min(image.height, rect.bottom.toInt() + padY)

        val cropW = maxX - minX
        val cropH = maxY - minY
        if (cropW <= 2 || cropH <= 2) {
            val emptyBmp = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
            Canvas(emptyBmp).drawColor(Color.WHITE)
            return emptyBmp
        }

        return Bitmap.createBitmap(image, minX, minY, cropW, cropH)
    }

    fun cropForMangaOcr(image: Bitmap, quad: Quadrilateral): Bitmap {
        val rect = quad.boundingRect()
        val isVertical = quad.isVertical || (rect.height() > rect.width())
        val fontSize = min(rect.width(), rect.height())

        // Direction-aware padding:
        // - Along text flow (Y for vertical, X for horizontal): generous padding (at least 16px or 35% font size)
        //   so edge characters and punctuation marks are never clipped.
        // - Perpendicular to flow (X for vertical, Y for horizontal): tight padding (2-4px max)
        //   so adjacent columns or lines are NEVER captured into the crop.
        val padX: Int
        val padY: Int
        if (isVertical) {
            padX = max(2, min(4, (fontSize * 0.08f).toInt()))
            padY = max(16, (fontSize * 0.35f).toInt())
        } else {
            padX = max(16, (fontSize * 0.35f).toInt())
            padY = max(2, min(4, (fontSize * 0.08f).toInt()))
        }

        val minX = max(0, rect.left.toInt() - padX)
        val minY = max(0, rect.top.toInt() - padY)
        val maxX = min(image.width, rect.right.toInt() + padX)
        val maxY = min(image.height, rect.bottom.toInt() + padY)

        val cropW = maxX - minX
        val cropH = maxY - minY
        if (cropW <= 2 || cropH <= 2) {
            val emptyBmp = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
            Canvas(emptyBmp).drawColor(Color.WHITE)
            return emptyBmp
        }

        return Bitmap.createBitmap(image, minX, minY, cropW, cropH)
    }

    fun cropTextRegion(image: Bitmap, quad: Quadrilateral, textHeight: Int = 48, forceVertical: Boolean? = null): Bitmap {
        val (pts, naturalIsVertical) = Quadrilateral.sortPnts(quad.pts)
        val isVertical = forceVertical ?: quad.isVertical.takeIf { it } ?: naturalIsVertical

        val p1x = (pts[0].x + pts[1].x) / 2.0
        val p1y = (pts[0].y + pts[1].y) / 2.0
        val p2x = (pts[2].x + pts[3].x) / 2.0
        val p2y = (pts[2].y + pts[3].y) / 2.0
        val p3x = (pts[1].x + pts[2].x) / 2.0
        val p3y = (pts[1].y + pts[2].y) / 2.0
        val p4x = (pts[3].x + pts[0].x) / 2.0
        val p4y = (pts[3].y + pts[0].y) / 2.0

        val vNorm = hypot(p2x - p1x, p2y - p1y)
        val hNorm = hypot(p3x - p4x, p3y - p4y)
        if (vNorm <= 1.0 || hNorm <= 1.0) {
            return Bitmap.createBitmap(textHeight, textHeight, Bitmap.Config.ARGB_8888)
        }

        val ratio = vNorm / hNorm

        val maxAllowedLength = 1024
        val targetW: Int
        val targetH: Int
        if (!isVertical) {
            targetH = max(textHeight, 2)
            targetW = max((textHeight / ratio).roundToInt(), 2).coerceAtMost(maxAllowedLength)
        } else {
            targetW = max(textHeight, 2)
            targetH = max((textHeight * ratio).roundToInt(), 2).coerceAtMost(maxAllowedLength)
        }

        val fullMat = Mat()
        Utils.bitmapToMat(image, fullMat)
        Imgproc.cvtColor(fullMat, fullMat, Imgproc.COLOR_RGBA2RGB)

        val minX = max(0, pts.minOf { it.x }.toInt())
        val minY = max(0, pts.minOf { it.y }.toInt())
        val maxX = min(image.width, pts.maxOf { it.x }.toInt() + 1)
        val maxY = min(image.height, pts.maxOf { it.y }.toInt() + 1)

        val cropW = maxX - minX
        val cropH = maxY - minY
        if (cropW <= 0 || cropH <= 0) {
            fullMat.release()
            return Bitmap.createBitmap(textHeight, textHeight, Bitmap.Config.ARGB_8888)
        }

        val cropRoi = fullMat.submat(Rect(minX, minY, cropW, cropH))

        val srcPoints = arrayOf(
            Point((pts[0].x - minX).toDouble(), (pts[0].y - minY).toDouble()),
            Point((pts[1].x - minX).toDouble(), (pts[1].y - minY).toDouble()),
            Point((pts[2].x - minX).toDouble(), (pts[2].y - minY).toDouble()),
            Point((pts[3].x - minX).toDouble(), (pts[3].y - minY).toDouble())
        )

        val dstPoints = arrayOf(
            Point(0.0, 0.0),
            Point((targetW - 1).toDouble(), 0.0),
            Point((targetW - 1).toDouble(), (targetH - 1).toDouble()),
            Point(0.0, (targetH - 1).toDouble())
        )

        val srcMat = MatOfPoint2f(*srcPoints)
        val dstMat = MatOfPoint2f(*dstPoints)
        val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)

        val isDownsampling = cropW > targetW || cropH > targetH
        val interpolation = if (isDownsampling) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR

        var regionMat = Mat()
        Imgproc.warpPerspective(cropRoi, regionMat, transform, Size(targetW.toDouble(), targetH.toDouble()), interpolation)

        if (isVertical) {
            val rotatedMat = Mat()
            Core.rotate(regionMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
            regionMat.release()
            regionMat = rotatedMat
        }

        // Mild unsharp mask to crispen fine kanji strokes at 48px height
        val blurred = Mat()
        Imgproc.GaussianBlur(regionMat, blurred, Size(0.0, 0.0), 1.0)
        Core.addWeighted(regionMat, 1.2, blurred, -0.2, 0.0, regionMat)
        blurred.release()

        val outBmp = Bitmap.createBitmap(regionMat.cols(), regionMat.rows(), Bitmap.Config.ARGB_8888)
        val rgbaMat = Mat()
        Imgproc.cvtColor(regionMat, rgbaMat, Imgproc.COLOR_RGB2RGBA)
        Utils.matToBitmap(rgbaMat, outBmp)

        fullMat.release()
        cropRoi.release()
        srcMat.release()
        dstMat.release()
        transform.release()
        regionMat.release()
        rgbaMat.release()

        return outBmp
    }
    
    fun batchCrops(crops: List<Bitmap>): Pair<FloatArray, IntArray> {
        if (crops.isEmpty()) return Pair(FloatArray(0), IntArray(0))
        val maxWidth = crops.maxOf { it.width }.coerceAtMost(1024)
        val paddedMaxWidth = (((maxWidth + 3) / 4) * 4).coerceAtLeast(32)
        val batchSize = crops.size
        val tensor = FloatArray(batchSize * 3 * 48 * paddedMaxWidth)
        tensor.fill(-1.0f) // Fill with -1.0f matching Python np.zeros(..., dtype=np.uint8) normalized via (0 - 127.5) / 127.5 = -1.0f
        val widths = IntArray(batchSize)
        
        for (i in 0 until batchSize) {
            val bmp = crops[i]
            val w = bmp.width
            val h = bmp.height
            widths[i] = w
            
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            
            val targetH = minOf(h, 48)
            for (y in 0 until targetH) {
                for (x in 0 until w) {
                    val color = pixels[y * w + x]
                    val r = ((color shr 16) and 0xFF)
                    val g = ((color shr 8) and 0xFF)
                    val b = (color and 0xFF)
                    
                    val rf = (r - 127.5f) / 127.5f
                    val gf = (g - 127.5f) / 127.5f
                    val bf = (b - 127.5f) / 127.5f
                    
                    val baseIdx = i * (3 * 48 * paddedMaxWidth) + y * paddedMaxWidth + x
                    tensor[baseIdx] = rf
                    tensor[baseIdx + 48 * paddedMaxWidth] = gf
                    tensor[baseIdx + 2 * 48 * paddedMaxWidth] = bf
                }
            }
        }
        
        return Pair(tensor, widths)
    }
}

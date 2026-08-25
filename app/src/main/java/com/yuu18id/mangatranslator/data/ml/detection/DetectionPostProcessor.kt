package com.yuu18id.mangatranslator.data.ml.detection

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

object DetectionPostProcessor {

    fun binarize(heatmap: Mat, threshold: Float): Mat {
        val binary = Mat()
        Imgproc.threshold(heatmap, binary, threshold.toDouble(), 255.0, Imgproc.THRESH_BINARY)
        val binary8u = Mat()
        binary.convertTo(binary8u, CvType.CV_8UC1)
        binary.release()
        return binary8u
    }

    fun boxScoreFast(bitmap: Mat, points: List<Point>): Float {
        val xMin = points.minOf { it.x }.toFloat()
        val xMax = points.maxOf { it.x }.toFloat()
        val yMin = points.minOf { it.y }.toFloat()
        val yMax = points.maxOf { it.y }.toFloat()

        val xMinInt = max(0, xMin.toInt())
        val xMaxInt = min(bitmap.cols() - 1, xMax.toInt())
        val yMinInt = max(0, yMin.toInt())
        val yMaxInt = min(bitmap.rows() - 1, yMax.toInt())

        if (xMaxInt <= xMinInt || yMaxInt <= yMinInt) return 0f

        val mask = Mat.zeros(yMaxInt - yMinInt + 1, xMaxInt - xMinInt + 1, CvType.CV_8UC1)
        val shiftedPoints = points.map { Point(it.x - xMinInt, it.y - yMinInt) }
        val matOfPoint = MatOfPoint().apply { fromList(shiftedPoints) }
        
        Imgproc.fillPoly(mask, listOf(matOfPoint), Scalar(255.0))
        
        val submat = bitmap.submat(yMinInt, yMaxInt + 1, xMinInt, xMaxInt + 1)
        val mean = Core.mean(submat, mask)
        
        mask.release()
        submat.release()
        matOfPoint.release()
        
        return mean.`val`[0].toFloat()
    }

    fun unclipContour(contour: MatOfPoint, unclipRatio: Float): List<Point> {
        val matOfPoint2f = MatOfPoint2f(*contour.toArray())
        val area = Imgproc.contourArea(matOfPoint2f)
        val perimeter = Imgproc.arcLength(matOfPoint2f, true)
        
        val rect = Imgproc.minAreaRect(matOfPoint2f)
        matOfPoint2f.release()

        if (perimeter <= 1e-4 || area <= 1.0) {
            val pts = arrayOfNulls<Point>(4)
            rect.points(pts)
            return sortPoints(pts.map { it!! })
        }
        
        // distance = area * unclipRatio / perimeter (exact formula from Python CTD db_utils.py)
        val shortSide = min(rect.size.width, rect.size.height)
        val maxDistance = max(shortSide * 0.5, 4.0)
        val distance = (area * unclipRatio / perimeter).toDouble().coerceIn(1.0, maxDistance)
        
        // Expand the rotated rectangle by distance on both width and height
        rect.size.width += 2.0 * distance
        rect.size.height += 2.0 * distance
        
        val newPoints = arrayOfNulls<Point>(4)
        rect.points(newPoints)
        
        return sortPoints(newPoints.map { it!! })
    }

    fun getMiniBoxes(contour: MatOfPoint): Pair<List<Point>, Float> {
        val matOfPoint2f = MatOfPoint2f(*contour.toArray())
        val rect = Imgproc.minAreaRect(matOfPoint2f)
        val points = arrayOfNulls<Point>(4)
        rect.points(points)
        matOfPoint2f.release()
        
        val sortedPoints = sortPoints(points.map { it!! })
        val shortSide = min(rect.size.width, rect.size.height).toFloat()
        
        return Pair(sortedPoints, shortSide)
    }

    fun sortPoints(points: List<Point>): List<Point> {
        val sortedByX = points.sortedBy { it.x }
        
        val index1 = if (sortedByX[1].y > sortedByX[0].y) 0 else 1
        val index4 = if (sortedByX[1].y > sortedByX[0].y) 1 else 0
        
        val index2 = if (sortedByX[3].y > sortedByX[2].y) 2 else 3
        val index3 = if (sortedByX[3].y > sortedByX[2].y) 3 else 2
        
        return listOf(
            sortedByX[index1],
            sortedByX[index2],
            sortedByX[index3],
            sortedByX[index4]
        )
    }

    fun nonMaxSuppression(boxes: List<Pair<List<Point>, Float>>, nmsThresh: Float): List<Pair<List<Point>, Float>> {
        if (boxes.isEmpty()) return emptyList()

        val sortedBoxes = boxes.sortedByDescending { it.second }.toMutableList()
        val keep = mutableListOf<Pair<List<Point>, Float>>()

        while (sortedBoxes.isNotEmpty()) {
            val current = sortedBoxes.removeAt(0)
            keep.add(current)

            val currentRect = boundingRect(current.first)
            sortedBoxes.removeAll { other ->
                val otherRect = boundingRect(other.first)
                val iou = computeIoU(currentRect, otherRect)
                iou > nmsThresh
            }
        }

        return keep
    }

    private fun boundingRect(points: List<Point>): Rect {
        val xMin = points.minOf { it.x }.toInt()
        val xMax = points.maxOf { it.x }.toInt()
        val yMin = points.minOf { it.y }.toInt()
        val yMax = points.maxOf { it.y }.toInt()
        return Rect(xMin, yMin, max(1, xMax - xMin), max(1, yMax - yMin))
    }

    private fun computeIoU(r1: Rect, r2: Rect): Float {
        val x1 = max(r1.x, r2.x)
        val y1 = max(r1.y, r2.y)
        val x2 = min(r1.x + r1.width, r2.x + r2.width)
        val y2 = min(r1.y + r1.height, r2.y + r2.height)

        val intersectionW = max(0, x2 - x1)
        val intersectionH = max(0, y2 - y1)
        val intersectionArea = (intersectionW * intersectionH).toFloat()

        val area1 = (r1.width * r1.height).toFloat()
        val area2 = (r2.width * r2.height).toFloat()
        val unionArea = area1 + area2 - intersectionArea

        if (unionArea <= 0) return 0f
        return intersectionArea / unionArea
    }
}

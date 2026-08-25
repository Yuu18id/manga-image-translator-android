package com.yuu18id.mangatranslator.domain.model

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class Quadrilateral(
    val pts: List<PointF>,
    val text: String = "",
    val prob: Float = 0f,
    val fgColor: IntArray = intArrayOf(0, 0, 0),
    val bgColor: IntArray = intArrayOf(255, 255, 255),
    val angle: Float = 0f,
    val isVertical: Boolean = false
) {
    init {
        require(pts.size == 4) { "Quadrilateral must have exactly 4 points" }
    }

    companion object {
        fun fromPoints(rawPts: List<PointF>, text: String = "", prob: Float = 0f): Quadrilateral {
            val (sortedPts, isVertical) = sortPnts(rawPts)
            return Quadrilateral(
                pts = sortedPts,
                text = text,
                prob = prob,
                isVertical = isVertical
            )
        }

        fun sortPnts(pts: List<PointF>): Pair<List<PointF>, Boolean> {
            require(pts.size == 4)
            val vectors = mutableListOf<Pair<PointF, Float>>()
            for (i in 0 until 4) {
                for (j in 0 until 4) {
                    val dx = pts[i].x - pts[j].x
                    val dy = pts[i].y - pts[j].y
                    val norm = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    vectors.add(Pair(PointF(dx, dy), norm))
                }
            }
            val sortedByNorm = vectors.sortedBy { it.second }
            var vec1 = sortedByNorm[8].first
            val vec2 = sortedByNorm[10].first
            val dot = vec1.x * vec2.x + vec1.y * vec2.y
            if (dot < 0) {
                vec1 = PointF(-vec1.x, -vec1.y)
            }
            val strucVecX = abs((vec1.x + vec2.x) / 2f)
            val strucVecY = abs((vec1.y + vec2.y) / 2f)
            val isVertical = strucVecX <= strucVecY

            return if (isVertical) {
                val sortedByY = pts.sortedBy { it.y }
                val topTwo = sortedByY.take(2).sortedBy { it.x }
                val bottomTwo = sortedByY.takeLast(2).sortedByDescending { it.x }
                Pair(listOf(topTwo[0], topTwo[1], bottomTwo[0], bottomTwo[1]), true)
            } else {
                val sortedByX = pts.sortedBy { it.x }
                val leftTwo = sortedByX.take(2).sortedBy { it.y }
                val rightTwo = sortedByX.takeLast(2).sortedBy { it.y }
                Pair(listOf(leftTwo[0], rightTwo[0], rightTwo[1], leftTwo[1]), false)
            }
        }
    }

    fun boundingRect(): RectF {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (pt in pts) {
            minX = min(minX, pt.x)
            minY = min(minY, pt.y)
            maxX = max(maxX, pt.x)
            maxY = max(maxY, pt.y)
        }

        return RectF(minX, minY, maxX, maxY)
    }

    fun center(): PointF {
        val rect = boundingRect()
        return PointF(rect.centerX(), rect.centerY())
    }

    fun width(): Float {
        val rect = boundingRect()
        return rect.width()
    }

    fun height(): Float {
        val rect = boundingRect()
        return rect.height()
    }

    fun area(): Float {
        return width() * height()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Quadrilateral

        if (pts != other.pts) return false
        if (text != other.text) return false
        if (prob != other.prob) return false
        if (!fgColor.contentEquals(other.fgColor)) return false
        if (!bgColor.contentEquals(other.bgColor)) return false
        if (angle != other.angle) return false
        if (isVertical != other.isVertical) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pts.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + prob.hashCode()
        result = 31 * result + fgColor.contentHashCode()
        result = 31 * result + bgColor.contentHashCode()
        result = 31 * result + angle.hashCode()
        result = 31 * result + isVertical.hashCode()
        return result
    }
}

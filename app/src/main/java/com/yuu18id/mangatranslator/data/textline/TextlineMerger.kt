package com.yuu18id.mangatranslator.data.textline

import android.graphics.PointF
import android.graphics.RectF
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import com.yuu18id.mangatranslator.domain.model.TextBlock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Singleton
class TextlineMerger @Inject constructor() {

    fun merge(lines: List<Quadrilateral>): List<TextBlock> {
        if (lines.isEmpty()) return emptyList()

        val n = lines.size
        val parent = IntArray(n) { it }

        fun find(i: Int): Int {
            var root = i
            while (root != parent[root]) {
                parent[root] = parent[parent[root]]
                root = parent[root]
            }
            return root
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) {
                parent[rootI] = rootJ
            }
        }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (canMerge(lines[i], lines[j])) {
                    union(i, j)
                }
            }
        }

        val clusters = mutableMapOf<Int, MutableList<Quadrilateral>>()
        for (i in 0 until n) {
            val root = find(i)
            clusters.getOrPut(root) { mutableListOf() }.add(lines[i])
        }

        return clusters.values.map { cluster ->
            mergeCluster(cluster)
        }
    }

    private fun getFontSize(q: Quadrilateral): Float {
        val rect = q.boundingRect()
        return min(rect.width(), rect.height())
    }

    fun canMerge(q1: Quadrilateral, q2: Quadrilateral): Boolean {
        val r1 = q1.boundingRect()
        val r2 = q2.boundingRect()

        val fs1 = getFontSize(q1)
        val fs2 = getFontSize(q2)
        val charSize = min(fs1, fs2)
        if (charSize <= 0f) return false

        // Font size ratio check (reject if fonts are too different)
        if (max(fs1, fs2) / charSize > 1.8f) return false

        // Direction must match
        if (q1.isVertical != q2.isVertical) return false

        val xDist = if (r1.right < r2.left) r2.left - r1.right else if (r2.right < r1.left) r1.left - r2.right else 0f
        val yDist = if (r1.bottom < r2.top) r2.top - r1.bottom else if (r2.bottom < r1.top) r1.top - r2.bottom else 0f
        val rectDist = hypot(xDist, yDist)

        return if (q1.isVertical) {
            // Vertical textlines: gap horizontally between columns is typically <= 2.5 * charSize
            if (xDist > charSize * 2.5f) return false
            if (yDist > charSize * 3.0f) return false
            rectDist < charSize * 3.5f
        } else {
            // Horizontal textlines: gap vertically between rows is typically <= 2.5 * charSize
            if (yDist > charSize * 2.5f) return false
            if (xDist > charSize * 3.0f) return false
            rectDist < charSize * 3.5f
        }
    }

    private fun mergeCluster(cluster: List<Quadrilateral>): TextBlock {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (quad in cluster) {
            val rect = quad.boundingRect()
            minX = min(minX, rect.left)
            minY = min(minY, rect.top)
            maxX = max(maxX, rect.right)
            maxY = max(maxY, rect.bottom)
        }

        val boundingBox = RectF(minX, minY, maxX, maxY)
        // Majority voting for orientation
        val verticalCount = cluster.count { it.isVertical }
        val isVertical = verticalCount >= (cluster.size - verticalCount)

        val sortedCluster = if (isVertical) {
            // Right-to-left for vertical columns in Japanese manga, then top-to-bottom
            cluster.sortedWith(compareByDescending<Quadrilateral> { it.center().x }.thenBy { it.center().y })
        } else {
            // Top-to-bottom for horizontal lines, then left-to-right
            cluster.sortedWith(compareBy<Quadrilateral> { it.center().y }.thenBy { it.center().x })
        }

        val text = sortedCluster.joinToString(if (isVertical) "" else " ") { it.text.trim() }.trim()
        val avgFg = cluster.first().fgColor
        val avgBg = cluster.first().bgColor
        val avgAngle = cluster.map { it.angle }.average().toFloat()

        return TextBlock(
            lines = sortedCluster,
            text = text,
            translatedText = "",
            language = null,
            fgColor = avgFg,
            bgColor = avgBg,
            boundingBox = boundingBox,
            angle = avgAngle,
            isVertical = isVertical
        )
    }
}

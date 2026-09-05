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

        // Pass 1: Build connected components of candidate textlines
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (canMerge(lines[i], lines[j])) {
                    union(i, j)
                }
            }
        }

        val rawClusters = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            val root = find(i)
            rawClusters.getOrPut(root) { mutableListOf() }.add(i)
        }

        // Pass 2: Minimum Spanning Tree (MST) Outlier Splitting
        // Accurately cuts bridge edges between touching/adjacent speech balloons
        val finalClusters = mutableListOf<List<Quadrilateral>>()
        for (clusterIndices in rawClusters.values) {
            val splitGroups = splitTextRegionMst(clusterIndices, lines)
            for (group in splitGroups) {
                finalClusters.add(group.map { lines[it] })
            }
        }

        return finalClusters.map { cluster ->
            mergeCluster(cluster)
        }
    }

    private data class Edge(val dist: Float, val u: Int, val v: Int)

    private fun kruskalMst(nodeIndices: List<Int>, lines: List<Quadrilateral>): List<Edge> {
        val edges = mutableListOf<Edge>()
        for (i in 0 until nodeIndices.size) {
            for (j in i + 1 until nodeIndices.size) {
                val u = nodeIndices[i]
                val v = nodeIndices[j]
                val d = calculateDistance(lines[u], lines[v])
                edges.add(Edge(d, u, v))
            }
        }
        edges.sortBy { it.dist }

        val parent = mutableMapOf<Int, Int>()
        for (n in nodeIndices) parent[n] = n

        fun find(i: Int): Int {
            var root = i
            while (root != (parent[root] ?: root)) {
                parent[root] = parent[parent[root] ?: root] ?: root
                root = parent[root] ?: root
            }
            return root
        }

        val mstEdges = mutableListOf<Edge>()
        for (edge in edges) {
            val rootU = find(edge.u)
            val rootV = find(edge.v)
            if (rootU != rootV) {
                parent[rootU] = rootV
                mstEdges.add(edge)
                if (mstEdges.size == nodeIndices.size - 1) break
            }
        }
        return mstEdges
    }

    private fun splitTextRegionMst(
        nodeIndices: List<Int>,
        lines: List<Quadrilateral>,
        gamma: Float = 0.5f,
        sigma: Float = 1.2f
    ): List<List<Int>> {
        if (nodeIndices.size <= 1) return listOf(nodeIndices)

        if (nodeIndices.size == 2) {
            val u = nodeIndices[0]
            val v = nodeIndices[1]
            val fs = max(getFontSize(lines[u]), getFontSize(lines[v]))
            val d = calculateDistance(lines[u], lines[v])
            return if (canMerge(lines[u], lines[v]) && d <= 2.2f * fs && abs(lines[u].angle - lines[v].angle) <= 25.0f) {
                listOf(nodeIndices)
            } else {
                listOf(listOf(u), listOf(v))
            }
        }

        val mstEdges = kruskalMst(nodeIndices, lines).sortedByDescending { it.dist }
        if (mstEdges.isEmpty()) return listOf(nodeIndices)

        val distances = mstEdges.map { it.dist }
        val meanD = distances.average().toFloat()
        val variance = distances.map { (it - meanD) * (it - meanD) }.average().toFloat()
        val stdD = kotlin.math.sqrt(variance)
        val avgFontSize = nodeIndices.map { getFontSize(lines[it]) }.average().toFloat()
        val stdThreshold = max(0.35f * avgFontSize + 5.0f, 6.0f)

        val maxEdge = mstEdges[0]
        val maxD = maxEdge.dist

        // If the largest edge is significantly larger than internal line spacing or standard deviation is high,
        // it indicates a bridge between two separate speech bubbles!
        val shouldKeepTogether = (maxD <= meanD + stdD * sigma || maxD <= avgFontSize * 1.8f) && (stdD < stdThreshold || maxD <= avgFontSize * 1.5f)

        if (shouldKeepTogether) {
            return listOf(nodeIndices)
        }

        // Cut the largest bridge edge and find the resulting sub-trees
        val remainingEdges = mstEdges.drop(1)
        val adj = mutableMapOf<Int, MutableList<Int>>()
        for (n in nodeIndices) adj[n] = mutableListOf()
        for (edge in remainingEdges) {
            adj[edge.u]?.add(edge.v)
            adj[edge.v]?.add(edge.u)
        }

        val visited = mutableSetOf<Int>()
        val subComponents = mutableListOf<List<Int>>()
        for (n in nodeIndices) {
            if (n !in visited) {
                val comp = mutableListOf<Int>()
                val queue = ArrayDeque<Int>()
                queue.add(n)
                visited.add(n)
                while (queue.isNotEmpty()) {
                    val curr = queue.removeFirst()
                    comp.add(curr)
                    for (neighbor in adj[curr] ?: emptyList()) {
                        if (neighbor !in visited) {
                            visited.add(neighbor)
                            queue.add(neighbor)
                        }
                    }
                }
                subComponents.add(comp)
            }
        }

        val result = mutableListOf<List<Int>>()
        for (comp in subComponents) {
            result.addAll(splitTextRegionMst(comp, lines, gamma, sigma))
        }
        return result
    }

    private fun calculateDistance(q1: Quadrilateral, q2: Quadrilateral): Float {
        val minX1 = q1.boundsMinX()
        val maxX1 = q1.boundsMaxX()
        val minY1 = q1.boundsMinY()
        val maxY1 = q1.boundsMaxY()

        val minX2 = q2.boundsMinX()
        val maxX2 = q2.boundsMaxX()
        val minY2 = q2.boundsMinY()
        val maxY2 = q2.boundsMaxY()

        val c1x = q1.centerX()
        val c1y = q1.centerY()
        val c2x = q2.centerX()
        val c2y = q2.centerY()

        val fs = max(getFontSize(q1), getFontSize(q2))

        val w1 = q1.width()
        val h1 = q1.height()
        val w2 = q2.width()
        val h2 = q2.height()

        val isMb1 = isMultiColumnBlock(q1)
        val isMb2 = isMultiColumnBlock(q2)
        if (isMb1 && isMb2) {
            return abs(c1x - c2x) + abs(c1y - c2y) + 20.0f * fs
        }

        val ar1 = max(w1, h1) / max(min(w1, h1), 1f)
        val ar2 = max(w2, h2) / max(min(w2, h2), 1f)
        val isSingleChar1 = ar1 <= 1.35f && !isMb1
        val isSingleChar2 = ar2 <= 1.35f && !isMb2

        val isVertical = when {
            !isSingleChar1 && !isSingleChar2 -> q1.isVertical
            !isSingleChar1 -> q1.isVertical
            !isSingleChar2 -> q2.isVertical
            else -> q1.isVertical || q2.isVertical
        }

        return if (isVertical) {
            val dx = abs(c1x - c2x)
            val dy = abs(c1y - c2y)
            val vOverlap = max(0f, min(maxY1, maxY2) - max(minY1, minY2))
            val minHeight = min(h1, h2)
            val overlapRatio = if (minHeight > 0f) vOverlap / minHeight else 0f
            val yGap = if (maxY1 < minY2) minY2 - maxY1 else if (maxY2 < minY1) minY1 - maxY2 else 0f

            val topDiff = abs(minY1 - minY2)
            val botDiff = abs(maxY1 - maxY2)
            val minAlignDiff = min(topDiff, botDiff)

            val hasBaselineAlign = minAlignDiff <= fs * 0.85f
            val hasHighOverlap = overlapRatio >= 0.70f && dy <= fs * 1.5f

            if (vOverlap <= 0f || (!hasBaselineAlign && !hasHighOverlap) || overlapRatio < 0.35f) {
                dx + yGap + 10.0f * fs
            } else {
                dx + (1.0f - overlapRatio) * fs * 0.5f
            }
        } else {
            val dx = abs(c1x - c2x)
            val dy = abs(c1y - c2y)
            val hOverlap = max(0f, min(maxX1, maxX2) - max(minX1, minX2))
            val minWidth = min(w1, w2)
            val overlapRatio = if (minWidth > 0f) hOverlap / minWidth else 0f
            val xGap = if (maxX1 < minX2) minX2 - maxX1 else if (maxX2 < minX1) minX1 - maxX2 else 0f

            val leftDiff = abs(minX1 - minX2)
            val rightDiff = abs(maxX1 - maxX2)
            val minAlignDiff = min(leftDiff, rightDiff)

            val hasBaselineAlign = minAlignDiff <= fs * 0.85f
            val hasHighOverlap = overlapRatio >= 0.70f && dx <= fs * 1.5f

            if (hOverlap <= 0f || (!hasBaselineAlign && !hasHighOverlap) || overlapRatio < 0.35f) {
                dy + xGap + 10.0f * fs
            } else {
                dy + (1.0f - overlapRatio) * fs * 0.5f
            }
        }
    }

    fun isMultiColumnBlock(q: Quadrilateral): Boolean {
        val w = q.width()
        val h = q.height()
        return if (q.isVertical) {
            (w >= h * 0.70f && w >= 120.0f)
        } else {
            (h >= w * 0.70f && h >= 120.0f)
        }
    }

    private fun getFontSize(q: Quadrilateral): Float {
        val w = q.width()
        val h = q.height()
        val ar = max(w, h) / max(min(w, h), 1f)
        if (isMultiColumnBlock(q)) {
            val estCols = if (q.isVertical) {
                max(2.0f, kotlin.math.round(w / 70.0f))
            } else {
                max(2.0f, kotlin.math.round(h / 70.0f))
            }
            return if (q.isVertical) (w / estCols) else (h / estCols)
        }
        return if (ar <= 1.35f && min(w, h) <= 100.0f) {
            min(w, h)
        } else if (q.isVertical) {
            w
        } else {
            h
        }
    }

    fun canMerge(q1: Quadrilateral, q2: Quadrilateral): Boolean {
        val isMb1 = isMultiColumnBlock(q1)
        val isMb2 = isMultiColumnBlock(q2)

        // RULE 1: Two multi-column/multi-line blocks must NEVER merge!
        // In comic typography, two distinct blocks represent separate speech bubbles or panels.
        if (isMb1 && isMb2) return false

        val minX1 = q1.boundsMinX()
        val maxX1 = q1.boundsMaxX()
        val minY1 = q1.boundsMinY()
        val maxY1 = q1.boundsMaxY()

        val minX2 = q2.boundsMinX()
        val maxX2 = q2.boundsMaxX()
        val minY2 = q2.boundsMinY()
        val maxY2 = q2.boundsMaxY()

        val c1x = q1.centerX()
        val c1y = q1.centerY()
        val c2x = q2.centerX()
        val c2y = q2.centerY()

        val fs1 = getFontSize(q1)
        val fs2 = getFontSize(q2)
        val charSize = min(fs1, fs2)
        if (charSize <= 0f) return false

        // Font size ratio check (tolerant up to 2.2x for comic emphasis/furigana)
        if (max(fs1, fs2) / charSize > 2.2f) return false

        val w1 = q1.width()
        val h1 = q1.height()
        val w2 = q2.width()
        val h2 = q2.height()

        // Check if either box is a single square character (aspect ratio ~1.0)
        val ar1 = max(w1, h1) / max(min(w1, h1), 1f)
        val ar2 = max(w2, h2) / max(min(w2, h2), 1f)
        val isSingleChar1 = ar1 <= 1.35f && !isMb1
        val isSingleChar2 = ar2 <= 1.35f && !isMb2

        // Orientation direction: if one box is a single square character, its orientation aligns with the multi-char line
        val effectiveVertical = when {
            !isSingleChar1 && !isSingleChar2 -> {
                if (q1.isVertical != q2.isVertical) return false
                q1.isVertical
            }
            !isSingleChar1 -> q1.isVertical
            !isSingleChar2 -> q2.isVertical
            else -> q1.isVertical || q2.isVertical
        }

        // Angle orientation must be reasonably aligned (within 25 degrees)
        if (abs(q1.angle - q2.angle) > 25.0f) return false

        return if (effectiveVertical) {
            val dx = abs(c1x - c2x)
            val dy = abs(c1y - c2y)
            val xGap = if (maxX1 < minX2) minX2 - maxX1 else if (maxX2 < minX1) minX1 - maxX2 else 0f
            val vOverlap = max(0f, min(maxY1, maxY2) - max(minY1, minY2))
            val minHeight = min(h1, h2)
            val overlapRatio = if (minHeight > 0f) vOverlap / minHeight else 0f

            val topDiff = abs(minY1 - minY2)
            val botDiff = abs(maxY1 - maxY2)
            val minAlignDiff = min(topDiff, botDiff)

            val isHorizontallyAdjacent = if (!isMb1 && !isMb2) {
                dx <= charSize * 2.2f || xGap <= charSize * 1.5f
            } else {
                xGap <= charSize * 0.5f
            }

            val hasVerticalOverlap = if (isSingleChar1 || isSingleChar2) {
                vOverlap >= charSize * 0.5f
            } else {
                val hasBaselineAlign = minAlignDiff <= charSize * 0.85f
                val hasHighOverlap = overlapRatio >= 0.70f && dy <= charSize * 1.5f
                val isAlignedWell = hasBaselineAlign || hasHighOverlap
                val hasEnoughOverlap = overlapRatio >= 0.35f

                vOverlap > 0f && isAlignedWell && hasEnoughOverlap
            }

            isHorizontallyAdjacent && hasVerticalOverlap
        } else {
            val dx = abs(c1x - c2x)
            val dy = abs(c1y - c2y)
            val yGap = if (maxY1 < minY2) minY2 - maxY1 else if (maxY2 < minY1) minY1 - maxY2 else 0f
            val hOverlap = max(0f, min(maxX1, maxX2) - max(minX1, minX2))
            val minWidth = min(w1, w2)
            val overlapRatio = if (minWidth > 0f) hOverlap / minWidth else 0f

            val leftDiff = abs(minX1 - minX2)
            val rightDiff = abs(maxX1 - maxX2)
            val minAlignDiff = min(leftDiff, rightDiff)

            val isVerticallyAdjacent = if (!isMb1 && !isMb2) {
                dy <= charSize * 2.2f || yGap <= charSize * 1.5f
            } else {
                yGap <= charSize * 0.5f
            }

            val hasHorizontalOverlap = if (isSingleChar1 || isSingleChar2) {
                hOverlap >= charSize * 0.5f
            } else {
                val hasBaselineAlign = minAlignDiff <= charSize * 0.85f
                val hasHighOverlap = overlapRatio >= 0.70f && dx <= charSize * 1.5f
                val isAlignedWell = hasBaselineAlign || hasHighOverlap
                val hasEnoughOverlap = overlapRatio >= 0.35f

                hOverlap > 0f && isAlignedWell && hasEnoughOverlap
            }

            isVerticallyAdjacent && hasHorizontalOverlap
        }
    }

    private fun mergeCluster(cluster: List<Quadrilateral>): TextBlock {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (quad in cluster) {
            minX = min(minX, quad.boundsMinX())
            minY = min(minY, quad.boundsMinY())
            maxX = max(maxX, quad.boundsMaxX())
            maxY = max(maxY, quad.boundsMaxY())
        }

        val boundingBox = RectF(minX, minY, maxX, maxY)
        var verticalScore = 0
        for (q in cluster) {
            val w = q.width()
            val h = q.height()
            val ar = max(w, h) / max(min(w, h), 1f)
            if (ar > 1.35f) {
                verticalScore += if (q.isVertical) 3 else -3
            } else {
                verticalScore += if (q.isVertical) 1 else -1
            }
        }
        val isVertical = verticalScore >= 0

        val sortedCluster = if (isVertical) {
            // Right-to-left for vertical columns in Japanese manga, then top-to-bottom
            cluster.sortedWith { a, b ->
                val dx = b.centerX().compareTo(a.centerX())
                if (dx != 0) dx else a.centerY().compareTo(b.centerY())
            }
        } else {
            // Top-to-bottom for horizontal lines, then left-to-right
            cluster.sortedWith { a, b ->
                val dy = a.centerY().compareTo(b.centerY())
                if (dy != 0) dy else a.centerX().compareTo(b.centerX())
            }
        }

        val text = sortedCluster.joinToString(if (isVertical) "" else " ") { it.text.trim() }.trim()
        val avgFg = if (cluster.size == 1) {
            cluster.first().fgColor
        } else {
            val validFg = cluster.map { it.fgColor }.filter { it.size == 3 }
            if (validFg.isNotEmpty()) {
                val r = validFg.map { it[0] }.average().toInt()
                val g = validFg.map { it[1] }.average().toInt()
                val b = validFg.map { it[2] }.average().toInt()
                intArrayOf(r, g, b)
            } else {
                intArrayOf(0, 0, 0)
            }
        }

        val avgBg = if (cluster.size == 1) {
            cluster.first().bgColor
        } else {
            val validBg = cluster.map { it.bgColor }.filter { it.size == 3 }
            if (validBg.isNotEmpty()) {
                val r = validBg.map { it[0] }.average().toInt()
                val g = validBg.map { it[1] }.average().toInt()
                val b = validBg.map { it[2] }.average().toInt()
                intArrayOf(r, g, b)
            } else {
                intArrayOf(255, 255, 255)
            }
        }
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

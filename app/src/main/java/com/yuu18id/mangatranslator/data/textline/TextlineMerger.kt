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
        gamma: Float = 0.15f,
        sigma: Float = 1.2f
    ): List<List<Int>> {
        if (nodeIndices.size <= 1) return listOf(nodeIndices)

        if (nodeIndices.size == 2) {
            val u = nodeIndices[0]
            val v = nodeIndices[1]
            val fs = max(getFontSize(lines[u]), getFontSize(lines[v]))
            val d = calculateDistance(lines[u], lines[v])
            return if (canMerge(lines[u], lines[v]) && d <= (0.95f + gamma) * fs && abs(lines[u].angle - lines[v].angle) <= 25.0f) {
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
        val stdThreshold = max(0.20f * avgFontSize + 2.0f, 3.0f)

        val maxEdge = mstEdges[0]
        val maxD = maxEdge.dist

        // If the largest edge is significantly larger than internal line spacing or standard deviation is high,
        // it indicates a bridge between two separate speech bubbles!
        val shouldKeepTogether = (maxD <= meanD + stdD * sigma && maxD <= avgFontSize * (0.95f + gamma)) && (stdD < stdThreshold)

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
        val r1 = q1.boundingRect()
        val r2 = q2.boundingRect()
        val xDist = if (r1.right < r2.left) r2.left - r1.right else if (r2.right < r1.left) r1.left - r2.right else 0f
        val yDist = if (r1.bottom < r2.top) r2.top - r1.bottom else if (r2.bottom < r1.top) r1.top - r2.bottom else 0f
        return hypot(xDist, yDist)
    }

    private fun getFontSize(q: Quadrilateral): Float {
        val rect = q.boundingRect()
        return if (q.isVertical) rect.width() else rect.height()
    }

    fun canMerge(q1: Quadrilateral, q2: Quadrilateral): Boolean {
        val r1 = q1.boundingRect()
        val r2 = q2.boundingRect()

        val fs1 = getFontSize(q1)
        val fs2 = getFontSize(q2)
        val charSize = min(fs1, fs2)
        if (charSize <= 0f) return false

        // Font size ratio check (tolerant up to 1.8x for comic emphasis/furigana)
        if (max(fs1, fs2) / charSize > 1.8f) return false

        // Orientation direction must match
        if (q1.isVertical != q2.isVertical) return false

        // Angle orientation must be reasonably aligned (within 25 degrees)
        if (abs(q1.angle - q2.angle) > 25.0f) return false

        val xDist = if (r1.right < r2.left) r2.left - r1.right else if (r2.right < r1.left) r1.left - r2.right else 0f
        val yDist = if (r1.bottom < r2.top) r2.top - r1.bottom else if (r2.bottom < r1.top) r1.top - r2.bottom else 0f

        return if (q1.isVertical) {
            // Vertical Japanese columns in the same speech bubble
            val verticalOverlap = max(0f, min(r1.bottom, r2.bottom) - max(r1.top, r2.top))
            val minHeight = min(r1.height(), r2.height())
            val hasVerticalOverlap = minHeight > 0f && (verticalOverlap / minHeight) >= 0.40f

            val horizontalOverlap = max(0f, min(r1.right, r2.right) - max(r1.left, r2.left))
            val minWidth = min(r1.width(), r2.width())
            val hasHorizontalOverlap = minWidth > 0f && (horizontalOverlap / minWidth) >= 0.45f

            // 1. Parallel adjacent columns: must have strong vertical overlap and close horizontal column spacing
            val isParallelColumn = hasVerticalOverlap && (xDist <= charSize * 0.95f) && (yDist <= charSize * 0.5f)
            // 2. Collinear stacked segments of the SAME vertical column: must have strong horizontal alignment
            val isStackedSegment = hasHorizontalOverlap && (xDist <= charSize * 0.35f) && (yDist <= charSize * 1.25f)

            isParallelColumn || isStackedSegment
        } else {
            // Horizontal text rows in the same speech bubble
            val horizontalOverlap = max(0f, min(r1.right, r2.right) - max(r1.left, r2.left))
            val minWidth = min(r1.width(), r2.width())
            val hasHorizontalOverlap = minWidth > 0f && (horizontalOverlap / minWidth) >= 0.40f

            val verticalOverlap = max(0f, min(r1.bottom, r2.bottom) - max(r1.top, r2.top))
            val minHeight = min(r1.height(), r2.height())
            val hasVerticalOverlap = minHeight > 0f && (verticalOverlap / minHeight) >= 0.45f

            // 1. Parallel adjacent rows: must have strong horizontal overlap and close vertical row spacing
            val isParallelRow = hasHorizontalOverlap && (yDist <= charSize * 0.95f) && (xDist <= charSize * 0.5f)
            // 2. Collinear inline segments of the SAME horizontal row: must have strong vertical alignment
            val isInlineSegment = hasVerticalOverlap && (yDist <= charSize * 0.35f) && (xDist <= charSize * 1.25f)

            isParallelRow || isInlineSegment
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

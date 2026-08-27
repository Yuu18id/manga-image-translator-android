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
            return if (canMerge(lines[u], lines[v]) && d <= (1.0f + gamma) * fs && abs(lines[u].angle - lines[v].angle) <= 25.0f) {
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
        val shouldKeepTogether = (maxD <= meanD + stdD * sigma || maxD <= avgFontSize * (1.0f + gamma)) && (stdD < stdThreshold)

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
        val c1 = q1.center()
        val c2 = q2.center()
        val fs = max(getFontSize(q1), getFontSize(q2))

        return if (q1.isVertical) {
            // Vertical text in manga
            val dx = abs(c1.x - c2.x)
            val centerDiffY = abs(c1.y - c2.y)
            val topDiffY = abs(r1.top - r2.top)
            val bottomDiffY = abs(r1.bottom - r2.bottom)
            val alignY = minOf(centerDiffY, topDiffY, bottomDiffY)

            // Check if collinear stacked segments in the same column
            val isCollinear = dx <= fs * 0.5f
            if (isCollinear) {
                val yGap = if (r1.bottom < r2.top) r2.top - r1.bottom else if (r2.bottom < r1.top) r1.top - r2.bottom else 0f
                hypot(dx, yGap)
            } else {
                // Side-by-side parallel columns: penalize large vertical misalignment
                hypot(dx, alignY * 1.8f)
            }
        } else {
            // Horizontal text
            val dy = abs(c1.y - c2.y)
            val centerDiffX = abs(c1.x - c2.x)
            val leftDiffX = abs(r1.left - r2.left)
            val rightDiffX = abs(r1.right - r2.right)
            val alignX = minOf(centerDiffX, leftDiffX, rightDiffX)

            val isInline = dy <= fs * 0.5f
            if (isInline) {
                val xGap = if (r1.right < r2.left) r2.left - r1.right else if (r2.right < r1.left) r1.left - r2.right else 0f
                hypot(xGap, dy)
            } else {
                hypot(alignX * 1.8f, dy)
            }
        }
    }

    private fun getFontSize(q: Quadrilateral): Float {
        val rect = q.boundingRect()
        return if (q.isVertical) rect.width() else rect.height()
    }

    fun canMerge(q1: Quadrilateral, q2: Quadrilateral): Boolean {
        val r1 = q1.boundingRect()
        val r2 = q2.boundingRect()
        val c1 = q1.center()
        val c2 = q2.center()

        val fs1 = getFontSize(q1)
        val fs2 = getFontSize(q2)
        val charSize = min(fs1, fs2)
        if (charSize <= 0f) return false

        // Font size ratio check (tolerant up to 2.2x for comic emphasis/furigana)
        if (max(fs1, fs2) / charSize > 2.2f) return false

        // Orientation direction must match
        if (q1.isVertical != q2.isVertical) return false

        // Angle orientation must be reasonably aligned (within 25 degrees)
        if (abs(q1.angle - q2.angle) > 25.0f) return false

        return if (q1.isVertical) {
            val dx = abs(c1.x - c2.x)
            val vOverlap = max(0f, min(r1.bottom, r2.bottom) - max(r1.top, r2.top))
            val maxH = max(r1.height(), r2.height())
            val alignY = minOf(abs(c1.y - c2.y), abs(r1.top - r2.top), abs(r1.bottom - r2.bottom))
            val yGap = if (r1.bottom < r2.top) r2.top - r1.bottom else if (r2.bottom < r1.top) r1.top - r2.bottom else 0f

            // 1. Parallel adjacent columns in the same speech bubble
            val isParallelColumn = (dx <= charSize * 2.2f) && (vOverlap > 0f || alignY <= maxH * 0.75f) && (yGap <= charSize * 1.5f)
            // 2. Collinear stacked segments in the same vertical column
            val isStackedSegment = (dx <= charSize * 0.55f) && (yGap <= charSize * 2.5f)

            isParallelColumn || isStackedSegment
        } else {
            val dy = abs(c1.y - c2.y)
            val hOverlap = max(0f, min(r1.right, r2.right) - max(r1.left, r2.left))
            val maxW = max(r1.width(), r2.width())
            val alignX = minOf(abs(c1.x - c2.x), abs(r1.left - r2.left), abs(r1.right - r2.right))
            val xGap = if (r1.right < r2.left) r2.left - r1.right else if (r2.right < r1.left) r1.left - r2.right else 0f

            // 1. Parallel adjacent rows in the same speech bubble
            val isParallelRow = (dy <= charSize * 2.2f) && (hOverlap > 0f || alignX <= maxW * 0.75f) && (xGap <= charSize * 1.5f)
            // 2. Collinear inline segments in the same horizontal row
            val isInlineSegment = (dy <= charSize * 0.55f) && (xGap <= charSize * 2.5f)

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

package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.PointF
import android.graphics.RectF
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import kotlin.math.max
import kotlin.math.min

data class EditableBox(
    val id: Int,
    val pts: List<PointF>,
    val prob: Float = 1.0f,
    val isVertical: Boolean = false
) {
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
        val r = boundingRect()
        return PointF(r.centerX(), r.centerY())
    }

    fun moveBy(dx: Float, dy: Float, boundW: Float, boundH: Float): EditableBox {
        val r = boundingRect()
        val w = r.width()
        val h = r.height()
        val newLeft = (r.left + dx).coerceIn(0f, boundW - w)
        val newTop = (r.top + dy).coerceIn(0f, boundH - h)
        val actualDx = newLeft - r.left
        val actualDy = newTop - r.top

        val newPts = pts.map { PointF((it.x + actualDx).coerceIn(0f, boundW), (it.y + actualDy).coerceIn(0f, boundH)) }
        return copy(pts = newPts)
    }

    fun resizeCornerAnchor(cornerIndex: Int, newPt: PointF, boundW: Float, boundH: Float): EditableBox {
        val r = boundingRect()
        val minSize = 15f
        var l = r.left
        var t = r.top
        var right = r.right
        var b = r.bottom

        when (cornerIndex) {
            0 -> { // Top-Left (Anchor is Bottom-Right)
                l = newPt.x.coerceIn(0f, right - minSize)
                t = newPt.y.coerceIn(0f, b - minSize)
            }
            1 -> { // Top-Right (Anchor is Bottom-Left)
                right = newPt.x.coerceIn(l + minSize, boundW)
                t = newPt.y.coerceIn(0f, b - minSize)
            }
            2 -> { // Bottom-Right (Anchor is Top-Left)
                right = newPt.x.coerceIn(l + minSize, boundW)
                b = newPt.y.coerceIn(t + minSize, boundH)
            }
            3 -> { // Bottom-Left (Anchor is Top-Right)
                l = newPt.x.coerceIn(0f, right - minSize)
                b = newPt.y.coerceIn(t + minSize, boundH)
            }
        }
        return fromRect(id, RectF(l, t, right, b)).copy(prob = prob, isVertical = isVertical)
    }

    fun updateCorner(cornerIndex: Int, newPt: PointF, boundW: Float, boundH: Float): EditableBox {
        val clampedPt = PointF(newPt.x.coerceIn(0f, boundW), newPt.y.coerceIn(0f, boundH))
        val newPts = pts.toMutableList()
        if (cornerIndex in newPts.indices) {
            newPts[cornerIndex] = clampedPt
        }
        return copy(pts = newPts)
    }

    fun toQuadrilateral(): Quadrilateral {
        return Quadrilateral.fromPoints(pts, prob = prob)
    }

    companion object {
        fun fromQuadrilateral(id: Int, quad: Quadrilateral): EditableBox {
            return EditableBox(
                id = id,
                pts = quad.pts.map { PointF(it.x, it.y) },
                prob = quad.prob,
                isVertical = quad.isVertical
            )
        }

        fun fromRect(id: Int, rect: RectF): EditableBox {
            val pts = listOf(
                PointF(rect.left, rect.top),
                PointF(rect.right, rect.top),
                PointF(rect.right, rect.bottom),
                PointF(rect.left, rect.bottom)
            )
            val isVertical = rect.height() > rect.width() * 1.3f
            return EditableBox(
                id = id,
                pts = pts,
                prob = 1.0f,
                isVertical = isVertical
            )
        }
    }
}

enum class DragMode {
    NONE,
    PAN,
    MOVE_BOX,
    RESIZE_CORNER_0, // Top-Left
    RESIZE_CORNER_1, // Top-Right
    RESIZE_CORNER_2, // Bottom-Right
    RESIZE_CORNER_3, // Bottom-Left
    DRAW_NEW_BOX
}

package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Composable
fun DetectionEditorCanvas(
    imageBitmap: ImageBitmap,
    boxes: List<EditableBox>,
    selectedBoxId: Int?,
    isAddBoxMode: Boolean,
    onSelectBox: (Int?) -> Unit,
    onUpdateBox: (EditableBox) -> Unit,
    onAddNewBox: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var liveDrawingStartPt by remember { mutableStateOf<PointF?>(null) }
    var liveDrawingCurrentPt by remember { mutableStateOf<PointF?>(null) }

    val imageWidth = imageBitmap.width.toFloat()
    val imageHeight = imageBitmap.height.toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF181818))
            .pointerInput(boxes, selectedBoxId, isAddBoxMode, scale, offset) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    val viewWidth = size.width.toFloat()
                    val viewHeight = size.height.toFloat()
                    val baseScale = min(viewWidth / imageWidth, viewHeight / imageHeight)
                    val fitWidth = imageWidth * baseScale
                    val fitHeight = imageHeight * baseScale
                    val centerX = viewWidth / 2f + offset.x
                    val centerY = viewHeight / 2f + offset.y
                    val imgLeft = centerX - (fitWidth * scale) / 2f
                    val imgTop = centerY - (fitHeight * scale) / 2f
                    val currentDisplayScale = baseScale * scale

                    fun screenToImage(pos: Offset): PointF {
                        val ix = (pos.x - imgLeft) / currentDisplayScale
                        val iy = (pos.y - imgTop) / currentDisplayScale
                        return PointF(ix.coerceIn(0f, imageWidth), iy.coerceIn(0f, imageHeight))
                    }

                    val downImgPt = screenToImage(down.position)
                    val selectedBox = boxes.find { it.id == selectedBoxId }

                    var activeDragMode = DragMode.NONE
                    var activeTargetBox: EditableBox? = null

                    // 1. Check if hit corner handle of currently selected box (touch target ~24dp)
                    if (selectedBox != null && !isAddBoxMode) {
                        val handleHitRadiusImg = (24.dp.toPx()) / currentDisplayScale
                        for (ci in 0 until 4) {
                            val cornerPt = selectedBox.pts[ci]
                            val dist = hypot(
                                (downImgPt.x - cornerPt.x).toDouble(),
                                (downImgPt.y - cornerPt.y).toDouble()
                            ).toFloat()
                            if (dist <= handleHitRadiusImg) {
                                activeDragMode = when (ci) {
                                    0 -> DragMode.RESIZE_CORNER_0
                                    1 -> DragMode.RESIZE_CORNER_1
                                    2 -> DragMode.RESIZE_CORNER_2
                                    else -> DragMode.RESIZE_CORNER_3
                                }
                                activeTargetBox = selectedBox
                                break
                            }
                        }
                    }

                    // 2. Add Box mode vs Box selection vs Pan
                    if (activeDragMode == DragMode.NONE) {
                        if (isAddBoxMode) {
                            activeDragMode = DragMode.DRAW_NEW_BOX
                            liveDrawingStartPt = downImgPt
                            liveDrawingCurrentPt = downImgPt
                        } else {
                            val hitBox = boxes.reversed().find { it.boundingRect().contains(downImgPt.x, downImgPt.y) }
                            if (hitBox != null) {
                                onSelectBox(hitBox.id)
                                activeDragMode = DragMode.MOVE_BOX
                                activeTargetBox = hitBox
                            } else {
                                activeDragMode = DragMode.PAN
                            }
                        }
                    }

                    dragMode = activeDragMode
                    var isMoved = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.isEmpty()) break

                        if (activePointers.size >= 2) {
                            // Two-finger gesture: Zoom & Pan canvas ONLY
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(0.5f, 6.0f)
                            offset += pan
                            event.changes.forEach { it.consume() }
                            activeDragMode = DragMode.NONE
                            dragMode = DragMode.NONE
                            liveDrawingStartPt = null
                            liveDrawingCurrentPt = null
                            isMoved = true
                        } else if (activePointers.size == 1) {
                            val change = activePointers.first()
                            val dragDelta = change.position - change.previousPosition
                            val totalDistFromDown = (change.position - down.position).getDistance()

                            if (totalDistFromDown > 6f) {
                                isMoved = true
                            }

                            if (isMoved) {
                                change.consume()
                                val curImgPt = screenToImage(change.position)

                                val box = activeTargetBox?.let { target -> boxes.find { it.id == target.id } }
                                if (box != null) {
                                    when (activeDragMode) {
                                        DragMode.MOVE_BOX -> {
                                            val dxImg = dragDelta.x / currentDisplayScale
                                            val dyImg = dragDelta.y / currentDisplayScale
                                            val moved = box.moveBy(dxImg, dyImg, imageWidth, imageHeight)
                                            onUpdateBox(moved)
                                        }
                                        DragMode.RESIZE_CORNER_0 -> {
                                            val resized = box.resizeCornerAnchor(0, curImgPt, imageWidth, imageHeight)
                                            onUpdateBox(resized)
                                        }
                                        DragMode.RESIZE_CORNER_1 -> {
                                            val resized = box.resizeCornerAnchor(1, curImgPt, imageWidth, imageHeight)
                                            onUpdateBox(resized)
                                        }
                                        DragMode.RESIZE_CORNER_2 -> {
                                            val resized = box.resizeCornerAnchor(2, curImgPt, imageWidth, imageHeight)
                                            onUpdateBox(resized)
                                        }
                                        DragMode.RESIZE_CORNER_3 -> {
                                            val resized = box.resizeCornerAnchor(3, curImgPt, imageWidth, imageHeight)
                                            onUpdateBox(resized)
                                        }
                                        else -> {}
                                    }
                                } else if (activeDragMode == DragMode.DRAW_NEW_BOX) {
                                    liveDrawingCurrentPt = curImgPt
                                } else if (activeDragMode == DragMode.PAN) {
                                    offset += dragDelta
                                }
                            }
                        }
                    }

                    // Handle Drag End for new box creation or Tap selection
                    if (activeDragMode == DragMode.DRAW_NEW_BOX && isMoved) {
                        val p1 = liveDrawingStartPt
                        val p2 = liveDrawingCurrentPt
                        if (p1 != null && p2 != null) {
                            val l = min(p1.x, p2.x)
                            val t = min(p1.y, p2.y)
                            val r = max(p1.x, p2.x)
                            val b = max(p1.y, p2.y)
                            if (r - l > 15f && b - t > 15f) {
                                onAddNewBox(RectF(l, t, r, b))
                            }
                        }
                    } else if (!isMoved && !isAddBoxMode) {
                        val hit = boxes.reversed().find { it.boundingRect().contains(downImgPt.x, downImgPt.y) }
                        onSelectBox(hit?.id)
                    }

                    dragMode = DragMode.NONE
                    liveDrawingStartPt = null
                    liveDrawingCurrentPt = null
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val viewWidth = size.width
            val viewHeight = size.height

            val baseScale = min(viewWidth / imageWidth, viewHeight / imageHeight)
            val fitWidth = imageWidth * baseScale
            val fitHeight = imageHeight * baseScale
            val centerX = viewWidth / 2f + offset.x
            val centerY = viewHeight / 2f + offset.y
            val imgLeft = centerX - (fitWidth * scale) / 2f
            val imgTop = centerY - (fitHeight * scale) / 2f
            val currentDisplayScale = baseScale * scale

            fun imageToScreen(imgX: Float, imgY: Float): Offset {
                return Offset(imgLeft + imgX * currentDisplayScale, imgTop + imgY * currentDisplayScale)
            }

            // 1. Draw base Manga Image
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                dstSize = IntSize((fitWidth * scale).toInt(), (fitHeight * scale).toInt())
            )

            // 2. Draw all bounding boxes
            for (box in boxes) {
                val isSelected = box.id == selectedBoxId
                val path = Path().apply {
                    val p0 = imageToScreen(box.pts[0].x, box.pts[0].y)
                    moveTo(p0.x, p0.y)
                    for (i in 1 until box.pts.size) {
                        val pi = imageToScreen(box.pts[i].x, box.pts[i].y)
                        lineTo(pi.x, pi.y)
                    }
                    close()
                }

                // Fill color
                val fillColor = if (isSelected) Color(0x55FF9800) else Color(0x3300E5FF)
                drawPath(path, color = fillColor)

                // Outline stroke
                val strokeColor = if (isSelected) Color(0xFFFF9800) else Color(0xFF00E5FF)
                val strokeWidth = if (isSelected) 3.5.dp.toPx() else 2.0.dp.toPx()
                drawPath(path, color = strokeColor, style = Stroke(width = strokeWidth))

                // Corner control handles for selected box
                if (isSelected) {
                    val handleRadius = 8.dp.toPx()
                    for (pt in box.pts) {
                        val screenPt = imageToScreen(pt.x, pt.y)
                        drawCircle(
                            color = Color(0x66000000),
                            radius = handleRadius + 1.5.dp.toPx(),
                            center = screenPt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = handleRadius,
                            center = screenPt
                        )
                        drawCircle(
                            color = Color(0xFFFF6D00),
                            radius = handleRadius,
                            center = screenPt,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }
            }

            // 3. Draw live rectangle when drawing new box
            if (dragMode == DragMode.DRAW_NEW_BOX && liveDrawingStartPt != null && liveDrawingCurrentPt != null) {
                val p1 = liveDrawingStartPt!!
                val p2 = liveDrawingCurrentPt!!
                val s1 = imageToScreen(p1.x, p1.y)
                val s2 = imageToScreen(p2.x, p2.y)

                val rectLeft = min(s1.x, s2.x)
                val rectTop = min(s1.y, s2.y)
                val rectW = max(s1.x, s2.x) - rectLeft
                val rectH = max(s1.y, s2.y) - rectTop

                drawRect(
                    color = Color(0x444CAF50),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectW, rectH)
                )
                drawRect(
                    color = Color(0xFF4CAF50),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectW, rectH),
                    style = Stroke(width = 2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f)))
                )
            }
        }
    }
}

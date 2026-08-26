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

    val currentBoxes by rememberUpdatedState(boxes)
    val currentSelectedBoxId by rememberUpdatedState(selectedBoxId)
    val currentIsAddBoxMode by rememberUpdatedState(isAddBoxMode)
    val currentOnSelectBox by rememberUpdatedState(onSelectBox)
    val currentOnUpdateBox by rememberUpdatedState(onUpdateBox)
    val currentOnAddNewBox by rememberUpdatedState(onAddNewBox)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF181818))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    val activeBoxes = currentBoxes
                    val activeSelectedId = currentSelectedBoxId
                    val activeIsAddMode = currentIsAddBoxMode

                    val viewWidth = size.width.toFloat()
                    val viewHeight = size.height.toFloat()
                    val baseScale = min(viewWidth / imageWidth, viewHeight / imageHeight)
                    val fitWidth = imageWidth * baseScale
                    val fitHeight = imageHeight * baseScale

                    fun screenToImage(pos: Offset, s: Float, off: Offset): PointF {
                        val cX = viewWidth / 2f + off.x
                        val cY = viewHeight / 2f + off.y
                        val iLeft = cX - (fitWidth * s) / 2f
                        val iTop = cY - (fitHeight * s) / 2f
                        val dScale = baseScale * s
                        val ix = (pos.x - iLeft) / dScale
                        val iy = (pos.y - iTop) / dScale
                        return PointF(ix.coerceIn(0f, imageWidth), iy.coerceIn(0f, imageHeight))
                    }

                    val downImgPt = screenToImage(down.position, scale, offset)
                    val selectedBox = activeBoxes.find { it.id == activeSelectedId }
                    val currentDisplayScale = baseScale * scale

                    var activeDragMode = DragMode.NONE
                    var initialBoxPts: List<PointF>? = null
                    var initialBoundingRect: RectF? = null
                    var dragTargetBoxId: Int? = null

                    // 1. Check if hit corner handle of currently selected box (touch target ~26dp)
                    if (selectedBox != null && !activeIsAddMode) {
                        val handleHitRadiusImg = (26.dp.toPx()) / currentDisplayScale
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
                                initialBoxPts = selectedBox.pts.map { PointF(it.x, it.y) }
                                initialBoundingRect = selectedBox.boundingRect()
                                dragTargetBoxId = selectedBox.id
                                break
                            }
                        }
                    }

                    // 2. Add Box mode vs Box selection vs Pan
                    if (activeDragMode == DragMode.NONE) {
                        if (activeIsAddMode) {
                            activeDragMode = DragMode.DRAW_NEW_BOX
                            liveDrawingStartPt = downImgPt
                            liveDrawingCurrentPt = downImgPt
                        } else {
                            val hitBox = activeBoxes.reversed().find { it.boundingRect().contains(downImgPt.x, downImgPt.y) }
                            if (hitBox != null) {
                                currentOnSelectBox(hitBox.id)
                                activeDragMode = DragMode.MOVE_BOX
                                initialBoxPts = hitBox.pts.map { PointF(it.x, it.y) }
                                initialBoundingRect = hitBox.boundingRect()
                                dragTargetBoxId = hitBox.id
                            } else {
                                activeDragMode = DragMode.PAN
                            }
                        }
                    }

                    dragMode = activeDragMode
                    var isMoved = false
                    val minSize = 15f

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
                            val distFromDown = (change.position - down.position).getDistance()

                            if (distFromDown > 4f) {
                                isMoved = true
                            }

                            if (isMoved) {
                                change.consume()
                                val curImgPt = screenToImage(change.position, scale, offset)

                                if (activeDragMode == DragMode.PAN) {
                                    offset += (change.position - change.previousPosition)
                                } else if (activeDragMode == DragMode.DRAW_NEW_BOX) {
                                    liveDrawingCurrentPt = curImgPt
                                } else if (initialBoundingRect != null && dragTargetBoxId != null) {
                                    val totalDx = curImgPt.x - downImgPt.x
                                    val totalDy = curImgPt.y - downImgPt.y

                                    val targetBox = currentBoxes.find { it.id == dragTargetBoxId }
                                    if (targetBox != null) {
                                        val initRect = initialBoundingRect
                                        if (activeDragMode == DragMode.MOVE_BOX && initialBoxPts != null) {
                                            val w = initRect.width()
                                            val h = initRect.height()
                                            val newLeft = (initRect.left + totalDx).coerceIn(0f, imageWidth - w)
                                            val newTop = (initRect.top + totalDy).coerceIn(0f, imageHeight - h)
                                            val actualDx = newLeft - initRect.left
                                            val actualDy = newTop - initRect.top

                                            val newPts = initialBoxPts.map {
                                                PointF((it.x + actualDx).coerceIn(0f, imageWidth), (it.y + actualDy).coerceIn(0f, imageHeight))
                                            }
                                            currentOnUpdateBox(targetBox.copy(pts = newPts))
                                        } else {
                                            val newRect = when (activeDragMode) {
                                                DragMode.RESIZE_CORNER_0 -> { // Top-Left (Anchor is Bottom-Right)
                                                    val newL = (initRect.left + totalDx).coerceIn(0f, initRect.right - minSize)
                                                    val newT = (initRect.top + totalDy).coerceIn(0f, initRect.bottom - minSize)
                                                    RectF(newL, newT, initRect.right, initRect.bottom)
                                                }
                                                DragMode.RESIZE_CORNER_1 -> { // Top-Right (Anchor is Bottom-Left)
                                                    val newR = (initRect.right + totalDx).coerceIn(initRect.left + minSize, imageWidth)
                                                    val newT = (initRect.top + totalDy).coerceIn(0f, initRect.bottom - minSize)
                                                    RectF(initRect.left, newT, newR, initRect.bottom)
                                                }
                                                DragMode.RESIZE_CORNER_2 -> { // Bottom-Right (Anchor is Top-Left)
                                                    val newR = (initRect.right + totalDx).coerceIn(initRect.left + minSize, imageWidth)
                                                    val newB = (initRect.bottom + totalDy).coerceIn(initRect.top + minSize, imageHeight)
                                                    RectF(initRect.left, initRect.top, newR, newB)
                                                }
                                                DragMode.RESIZE_CORNER_3 -> { // Bottom-Left (Anchor is Top-Right)
                                                    val newL = (initRect.left + totalDx).coerceIn(0f, initRect.right - minSize)
                                                    val newB = (initRect.bottom + totalDy).coerceIn(initRect.top + minSize, imageHeight)
                                                    RectF(newL, initRect.top, initRect.right, newB)
                                                }
                                                else -> null
                                            }
                                            if (newRect != null) {
                                                currentOnUpdateBox(EditableBox.fromRect(targetBox.id, newRect).copy(prob = targetBox.prob))
                                            }
                                        }
                                    }
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
                                currentOnAddNewBox(RectF(l, t, r, b))
                            }
                        }
                    } else if (!isMoved && !activeIsAddMode) {
                        val hit = currentBoxes.reversed().find { it.boundingRect().contains(downImgPt.x, downImgPt.y) }
                        currentOnSelectBox(hit?.id)
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

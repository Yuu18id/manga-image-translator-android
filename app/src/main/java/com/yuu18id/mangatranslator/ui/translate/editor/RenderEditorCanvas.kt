package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yuu18id.mangatranslator.domain.model.TextAlignment
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private enum class CanvasDragMode {
    NONE,
    PAN,
    MOVE_BOX,
    RESIZE_CORNER_0, // Top-Left
    RESIZE_CORNER_1, // Top-Right
    RESIZE_CORNER_2, // Bottom-Right
    RESIZE_CORNER_3  // Bottom-Left
}

@Composable
fun RenderEditorCanvas(
    inpaintedBitmap: ImageBitmap,
    blocks: List<EditableRenderBlock>,
    selectedBlockId: Int?,
    onSelectBlock: (Int?) -> Unit,
    onUpdateBlock: (EditableRenderBlock) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val typeface = remember {
        try {
            Typeface.createFromAsset(context.assets, "fonts/cc-wild-words-roman.ttf")
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }

    val imageWidth = inpaintedBitmap.width.toFloat()
    val imageHeight = inpaintedBitmap.height.toFloat()

    val currentBlocks by rememberUpdatedState(blocks)
    val currentSelectedBlockId by rememberUpdatedState(selectedBlockId)
    val currentOnSelectBlock by rememberUpdatedState(onSelectBlock)
    val currentOnUpdateBlock by rememberUpdatedState(onUpdateBlock)

    // Remembered Paint objects for zero-allocation 60 FPS rendering
    val testPaint = remember(typeface) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
        }
    }
    val fillPaint = remember(typeface) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            color = android.graphics.Color.BLACK
        }
    }
    val strokePaint = remember(typeface) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    }
    val borderPaint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.argb(255, 255, 152, 0)
        }
    }
    val bgHighlightPaint = remember {
        Paint().apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.argb(40, 255, 152, 0)
        }
    }
    val shadowPaint = remember {
        Paint().apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.argb(100, 0, 0, 0)
        }
    }
    val handleFillPaint = remember {
        Paint().apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
    }
    val handleBorderPaint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            color = android.graphics.Color.argb(255, 255, 109, 0)
        }
    }

    fun clampOffset(off: Offset, s: Float, viewW: Float, viewH: Float, fitW: Float, fitH: Float): Offset {
        val maxOffsetX = max(0f, (fitW * s - viewW) / 2f)
        val maxOffsetY = max(0f, (fitH * s - viewH) / 2f)
        return Offset(
            x = off.x.coerceIn(-maxOffsetX, maxOffsetX),
            y = off.y.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF181818))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    val activeBlocks = currentBlocks
                    val activeSelectedId = currentSelectedBlockId

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
                        return PointF(ix, iy)
                    }

                    val downImgPt = screenToImage(down.position, scale, offset)
                    val selectedBlock = activeBlocks.find { it.id == activeSelectedId }
                    val currentDisplayScale = baseScale * scale

                    var activeDragMode = CanvasDragMode.NONE
                    var initialBounds: RectF? = null
                    var dragTargetBlockId: Int? = null

                    // 1. Check if hit corner handle of currently selected block (touch target radius ~26dp)
                    if (selectedBlock != null) {
                        val handleHitRadiusImg = (26.dp.toPx()) / currentDisplayScale
                        val b = selectedBlock.bounds
                        val corners = listOf(
                            PointF(b.left, b.top),
                            PointF(b.right, b.top),
                            PointF(b.right, b.bottom),
                            PointF(b.left, b.bottom)
                        )
                        for (ci in 0 until 4) {
                            val dist = hypot(
                                (downImgPt.x - corners[ci].x).toDouble(),
                                (downImgPt.y - corners[ci].y).toDouble()
                            ).toFloat()
                            if (dist <= handleHitRadiusImg) {
                                activeDragMode = when (ci) {
                                    0 -> CanvasDragMode.RESIZE_CORNER_0
                                    1 -> CanvasDragMode.RESIZE_CORNER_1
                                    2 -> CanvasDragMode.RESIZE_CORNER_2
                                    else -> CanvasDragMode.RESIZE_CORNER_3
                                }
                                initialBounds = RectF(selectedBlock.bounds)
                                dragTargetBlockId = selectedBlock.id
                                break
                            }
                        }
                    }

                    // 2. If not corner handle, check if hit body of any block
                    if (activeDragMode == CanvasDragMode.NONE) {
                        val hitBlock = activeBlocks.reversed().find { it.bounds.contains(downImgPt.x, downImgPt.y) }
                        if (hitBlock != null) {
                            currentOnSelectBlock(hitBlock.id)
                            activeDragMode = CanvasDragMode.MOVE_BOX
                            initialBounds = RectF(hitBlock.bounds)
                            dragTargetBlockId = hitBlock.id
                        } else {
                            activeDragMode = CanvasDragMode.PAN
                        }
                    }

                    var isMoved = false
                    val minSize = 20f

                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.isEmpty()) break

                        if (activePointers.size >= 2) {
                            // Two-finger gesture: Centroid Focal Zoom & Pan Canvas
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(1.0f, 5.0f)

                            val center = Offset(viewWidth / 2f, viewHeight / 2f)
                            val centroidFromCenter = centroid - center
                            val scaleFactor = newScale / oldScale
                            val unClampedOffset = (offset - centroidFromCenter) * scaleFactor + centroidFromCenter + pan

                            scale = newScale
                            offset = clampOffset(unClampedOffset, newScale, viewWidth, viewHeight, fitWidth, fitHeight)

                            event.changes.forEach { it.consume() }
                            activeDragMode = CanvasDragMode.NONE
                            isMoved = true
                        } else if (activePointers.size == 1) {
                            val change = activePointers.first()
                            val distFromDown = (change.position - down.position).getDistance()

                            if (distFromDown > 4f) {
                                isMoved = true
                            }

                            if (isMoved) {
                                change.consume()
                                if (activeDragMode == CanvasDragMode.PAN) {
                                    if (scale > 1.0f) {
                                        val unClamped = offset + (change.position - change.previousPosition)
                                        offset = clampOffset(unClamped, scale, viewWidth, viewHeight, fitWidth, fitHeight)
                                    }
                                } else if (initialBounds != null && dragTargetBlockId != null) {
                                    val curImgPt = screenToImage(change.position, scale, offset)
                                    val totalDx = curImgPt.x - downImgPt.x
                                    val totalDy = curImgPt.y - downImgPt.y

                                    val targetBlock = currentBlocks.find { it.id == dragTargetBlockId }
                                    if (targetBlock != null) {
                                        val init = initialBounds
                                        val newBounds = when (activeDragMode) {
                                            CanvasDragMode.MOVE_BOX -> {
                                                val w = init.width()
                                                val h = init.height()
                                                val newL = (init.left + totalDx).coerceIn(0f, imageWidth - w)
                                                val newT = (init.top + totalDy).coerceIn(0f, imageHeight - h)
                                                RectF(newL, newT, newL + w, newT + h)
                                            }
                                            CanvasDragMode.RESIZE_CORNER_0 -> { // Top-Left (Anchor is Bottom-Right)
                                                val newL = (init.left + totalDx).coerceIn(0f, init.right - minSize)
                                                val newT = (init.top + totalDy).coerceIn(0f, init.bottom - minSize)
                                                RectF(newL, newT, init.right, init.bottom)
                                            }
                                            CanvasDragMode.RESIZE_CORNER_1 -> { // Top-Right (Anchor is Bottom-Left)
                                                val newR = (init.right + totalDx).coerceIn(init.left + minSize, imageWidth)
                                                val newT = (init.top + totalDy).coerceIn(0f, init.bottom - minSize)
                                                RectF(init.left, newT, newR, init.bottom)
                                            }
                                            CanvasDragMode.RESIZE_CORNER_2 -> { // Bottom-Right (Anchor is Top-Left)
                                                val newR = (init.right + totalDx).coerceIn(init.left + minSize, imageWidth)
                                                val newB = (init.bottom + totalDy).coerceIn(init.top + minSize, imageHeight)
                                                RectF(init.left, init.top, newR, newB)
                                            }
                                            CanvasDragMode.RESIZE_CORNER_3 -> { // Bottom-Left (Anchor is Top-Right)
                                                val newL = (init.left + totalDx).coerceIn(0f, init.right - minSize)
                                                val newB = (init.bottom + totalDy).coerceIn(init.top + minSize, imageHeight)
                                                RectF(newL, init.top, init.right, newB)
                                            }
                                            else -> null
                                        }

                                        if (newBounds != null) {
                                            currentOnUpdateBlock(targetBlock.copy(bounds = newBounds))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // If released without dragging -> Treat as Tap to select/deselect or Double Tap to Zoom
                    if (!isMoved) {
                        val tapHit = currentBlocks.reversed().find { it.bounds.contains(downImgPt.x, downImgPt.y) }
                        val now = System.currentTimeMillis()
                        if (tapHit == null && (now - lastTapTime) < 300L && (down.position - lastTapPos).getDistance() < 40f) {
                            if (scale > 1.05f) {
                                scale = 1.0f
                                offset = Offset.Zero
                            } else {
                                val targetScale = 2.5f
                                val center = Offset(viewWidth / 2f, viewHeight / 2f)
                                val tapFromCenter = down.position - center
                                val targetOffset = -tapFromCenter * (targetScale - 1f)
                                scale = targetScale
                                offset = clampOffset(targetOffset, targetScale, viewWidth, viewHeight, fitWidth, fitHeight)
                            }
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                            lastTapPos = down.position
                            currentOnSelectBlock(tapHit?.id)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val baseScale = min(canvasW / imageWidth, canvasH / imageHeight)
            val currentDisplayScale = baseScale * scale

            val displayedW = imageWidth * currentDisplayScale
            val displayedH = imageHeight * currentDisplayScale
            val imgLeft = (canvasW - displayedW) / 2f + offset.x
            val imgTop = (canvasH - displayedH) / 2f + offset.y

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                nativeCanvas.save()

                // Translate & Scale native canvas so all drawing is in 1:1 image pixel space!
                nativeCanvas.translate(imgLeft, imgTop)
                nativeCanvas.scale(currentDisplayScale, currentDisplayScale)

                // 1. Draw Background Inpainted Bitmap
                val nativeBitmap = inpaintedBitmap.asAndroidBitmap()
                nativeCanvas.drawBitmap(nativeBitmap, 0f, 0f, null)

                // 2. Render Text Blocks
                for (block in blocks) {
                    val isSelected = block.id == selectedBlockId
                    val b = block.bounds
                    val text = block.translatedText.trim()
                    if (text.isBlank() || b.width() <= 0 || b.height() <= 0) continue

                    fun wrapText(textToWrap: String, p: Paint, maxSpan: Float): List<String> {
                        val lines = mutableListOf<String>()
                        val paragraphs = textToWrap.split("\n")
                        for (para in paragraphs) {
                            val words = para.split(Regex("\\s+")).filter { it.isNotEmpty() }
                            if (words.isEmpty()) continue
                            var currentLine = StringBuilder()
                            var currentSpan = 0f
                            for (word in words) {
                                val wordWithSpace = if (currentLine.isEmpty()) word else " $word"
                                val wordSpan = p.measureText(wordWithSpace)
                                if (currentSpan + wordSpan > maxSpan && currentLine.isNotEmpty()) {
                                    lines.add(currentLine.toString())
                                    currentLine = StringBuilder(word)
                                    currentSpan = p.measureText(word)
                                } else {
                                    currentLine.append(wordWithSpace)
                                    currentSpan += wordSpan
                                }
                            }
                            if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                        }
                        return if (lines.isEmpty()) listOf(textToWrap) else lines
                    }

                    val maxAllowedWidth = b.width() * 0.94f
                    val maxAllowedHeight = b.height() * 0.92f

                    val fontSize: Float
                    val finalLines: List<String>

                    if (block.customFontSize != null) {
                        fontSize = block.customFontSize
                        testPaint.textSize = fontSize
                        finalLines = wrapText(text, testPaint, maxAllowedWidth)
                    } else {
                        var low = 12f
                        var high = min(maxAllowedHeight * 0.50f, 40f).coerceAtLeast(low + 2f)
                        var bestSize = low
                        var bestLines: List<String>? = null

                        var iter = 0
                        while (high - low >= 0.5f && iter < 10) {
                            iter++
                            val mid = (low + high) / 2f
                            testPaint.textSize = mid
                            val wrapped = wrapText(text, testPaint, maxAllowedWidth)
                            val totalH = wrapped.size * (mid * 1.18f)
                            val totalW = wrapped.maxOfOrNull { testPaint.measureText(it) } ?: 0f

                            if (totalW <= maxAllowedWidth * 1.05f && totalH <= maxAllowedHeight) {
                                bestSize = mid
                                bestLines = wrapped
                                low = mid
                            } else {
                                high = mid
                            }
                        }

                        fontSize = bestSize
                        testPaint.textSize = fontSize
                        finalLines = bestLines ?: wrapText(text, testPaint, maxAllowedWidth)
                    }

                    fillPaint.textSize = fontSize
                    fillPaint.textAlign = when (block.customAlignment) {
                        TextAlignment.LEFT -> Paint.Align.LEFT
                        TextAlignment.RIGHT -> Paint.Align.RIGHT
                        TextAlignment.CENTER, TextAlignment.AUTO -> Paint.Align.CENTER
                        else -> Paint.Align.CENTER
                    }

                    strokePaint.textSize = fontSize
                    strokePaint.strokeWidth = max(4.0f, fontSize * 0.28f)
                    strokePaint.textAlign = fillPaint.textAlign

                    val fm = fillPaint.fontMetrics
                    val lineHeight = fm.descent - fm.ascent
                    val totalBlockHeight = finalLines.size * lineHeight
                    val centerYBlock = b.top + b.height() / 2f
                    val firstLineBaseline = centerYBlock - totalBlockHeight / 2f - fm.ascent

                    val drawX = when (block.customAlignment) {
                        TextAlignment.LEFT -> b.left + b.width() * 0.05f
                        TextAlignment.RIGHT -> b.left + b.width() * 0.95f
                        TextAlignment.CENTER, TextAlignment.AUTO -> b.left + b.width() / 2f
                        else -> b.left + b.width() / 2f
                    }

                    finalLines.forEachIndexed { lineIdx, lineText ->
                        val baselineY = firstLineBaseline + lineIdx * lineHeight
                        nativeCanvas.drawText(lineText, drawX, baselineY, strokePaint)
                        nativeCanvas.drawText(lineText, drawX, baselineY, fillPaint)
                    }

                    // 3. Selection Box & High-Visibility Handles
                    if (isSelected) {
                        val strokeWInImg = 2.5.dp.toPx() / currentDisplayScale
                        borderPaint.strokeWidth = strokeWInImg

                        nativeCanvas.drawRect(b.left, b.top, b.right, b.bottom, bgHighlightPaint)
                        nativeCanvas.drawRect(b.left, b.top, b.right, b.bottom, borderPaint)

                        // 4 corner handles
                        val handleRadius = (8.dp.toPx()) / currentDisplayScale
                        handleBorderPaint.strokeWidth = 2.5.dp.toPx() / currentDisplayScale

                        val corners = listOf(
                            PointF(b.left, b.top),
                            PointF(b.right, b.top),
                            PointF(b.right, b.bottom),
                            PointF(b.left, b.bottom)
                        )
                        for (c in corners) {
                            nativeCanvas.drawCircle(c.x, c.y + 1.5f / currentDisplayScale, handleRadius + 1f / currentDisplayScale, shadowPaint)
                            nativeCanvas.drawCircle(c.x, c.y, handleRadius, handleFillPaint)
                            nativeCanvas.drawCircle(c.x, c.y, handleRadius, handleBorderPaint)
                        }
                    }
                }

                nativeCanvas.restore()
            }
        }
    }
}

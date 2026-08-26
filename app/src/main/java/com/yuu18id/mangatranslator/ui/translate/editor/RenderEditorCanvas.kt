package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
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

    val imageWidth = inpaintedBitmap.width.toFloat()
    val imageHeight = inpaintedBitmap.height.toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF181818))
            .pointerInput(blocks, selectedBlockId, scale, offset) {
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
                        return PointF(ix, iy)
                    }

                    val downImgPt = screenToImage(down.position)
                    val selectedBlock = blocks.find { it.id == selectedBlockId }

                    // 1. Check if hit corner handle of currently selected block (touch target radius ~24dp)
                    var activeDragMode = DragMode.NONE
                    var activeTargetBlock: EditableRenderBlock? = null

                    if (selectedBlock != null) {
                        val handleHitRadiusImg = (24.dp.toPx()) / currentDisplayScale
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
                                    0 -> DragMode.RESIZE_CORNER_0
                                    1 -> DragMode.RESIZE_CORNER_1
                                    2 -> DragMode.RESIZE_CORNER_2
                                    else -> DragMode.RESIZE_CORNER_3
                                }
                                activeTargetBlock = selectedBlock
                                break
                            }
                        }
                    }

                    // 2. If not corner handle, check if hit body of any block
                    if (activeDragMode == DragMode.NONE) {
                        val hitBlock = blocks.reversed().find { it.bounds.contains(downImgPt.x, downImgPt.y) }
                        if (hitBlock != null) {
                            onSelectBlock(hitBlock.id)
                            activeDragMode = DragMode.MOVE_BOX
                            activeTargetBlock = hitBlock
                        } else {
                            activeDragMode = DragMode.PAN
                        }
                    }

                    var isMoved = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.isEmpty()) break

                        if (activePointers.size >= 2) {
                            // Two-finger gesture: Pan & Zoom Canvas ONLY
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(0.5f, 6.0f)
                            offset += pan
                            event.changes.forEach { it.consume() }
                            activeDragMode = DragMode.NONE
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

                                val block = activeTargetBlock?.let { target -> blocks.find { it.id == target.id } }
                                if (block != null) {
                                    when (activeDragMode) {
                                        DragMode.MOVE_BOX -> {
                                            val dxImg = dragDelta.x / currentDisplayScale
                                            val dyImg = dragDelta.y / currentDisplayScale
                                            val moved = block.moveBy(dxImg, dyImg, imageWidth, imageHeight)
                                            onUpdateBlock(moved)
                                        }
                                        DragMode.RESIZE_CORNER_0 -> {
                                            val resized = block.resizeCornerAnchor(0, curImgPt, imageWidth, imageHeight)
                                            onUpdateBlock(resized)
                                        }
                                        DragMode.RESIZE_CORNER_1 -> {
                                            val resized = block.resizeCornerAnchor(1, curImgPt, imageWidth, imageHeight)
                                            onUpdateBlock(resized)
                                        }
                                        DragMode.RESIZE_CORNER_2 -> {
                                            val resized = block.resizeCornerAnchor(2, curImgPt, imageWidth, imageHeight)
                                            onUpdateBlock(resized)
                                        }
                                        DragMode.RESIZE_CORNER_3 -> {
                                            val resized = block.resizeCornerAnchor(3, curImgPt, imageWidth, imageHeight)
                                            onUpdateBlock(resized)
                                        }
                                        else -> {}
                                    }
                                } else if (activeDragMode == DragMode.PAN) {
                                    offset += dragDelta
                                }
                            }
                        }
                    }

                    // If released without dragging -> Treat as Tap to select/deselect
                    if (!isMoved) {
                        val hit = blocks.reversed().find { it.bounds.contains(downImgPt.x, downImgPt.y) }
                        onSelectBlock(hit?.id)
                    }
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

                    val testPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                        this.typeface = typeface
                    }

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
                        while (high - low >= 0.5f && iter < 14) {
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

                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                        this.typeface = typeface
                        textSize = fontSize
                        color = android.graphics.Color.BLACK
                        textAlign = when (block.customAlignment) {
                            TextAlignment.LEFT -> Paint.Align.LEFT
                            TextAlignment.RIGHT -> Paint.Align.RIGHT
                            TextAlignment.CENTER, TextAlignment.AUTO -> Paint.Align.CENTER
                            else -> Paint.Align.CENTER
                        }
                    }

                    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                        this.typeface = typeface
                        textSize = fontSize
                        color = android.graphics.Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = max(4.0f, fontSize * 0.28f)
                        strokeJoin = Paint.Join.ROUND
                        strokeCap = Paint.Cap.ROUND
                        textAlign = fillPaint.textAlign
                    }

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
                        val borderPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeWidth = strokeWInImg
                            color = android.graphics.Color.argb(255, 255, 152, 0)
                        }
                        val bgHighlightPaint = Paint().apply {
                            style = Paint.Style.FILL
                            color = android.graphics.Color.argb(40, 255, 152, 0)
                        }

                        nativeCanvas.drawRect(b.left, b.top, b.right, b.bottom, bgHighlightPaint)
                        nativeCanvas.drawRect(b.left, b.top, b.right, b.bottom, borderPaint)

                        // 4 corner handles (Crisp white fill with bold orange ring & shadow)
                        val handleRadius = (8.dp.toPx()) / currentDisplayScale
                        val shadowPaint = Paint().apply {
                            style = Paint.Style.FILL
                            color = android.graphics.Color.argb(100, 0, 0, 0)
                        }
                        val handleFillPaint = Paint().apply {
                            style = Paint.Style.FILL
                            color = android.graphics.Color.WHITE
                        }
                        val handleBorderPaint = Paint().apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 2.5.dp.toPx() / currentDisplayScale
                            color = android.graphics.Color.argb(255, 255, 109, 0)
                        }

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

package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yuu18id.mangatranslator.data.rendering.FontManager
import com.yuu18id.mangatranslator.data.rendering.LayoutResult
import com.yuu18id.mangatranslator.data.rendering.TextLayoutEngine
import com.yuu18id.mangatranslator.data.textline.TextPostProcessor
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.RenderConfig
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
    val fontManager = remember { FontManager(context.applicationContext) }
    val layoutEngine = remember { TextLayoutEngine(fontManager) }

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
    val fillPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = android.graphics.Color.BLACK
        }
    }
    val strokePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
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
            color = android.graphics.Color.argb(255, 255, 107, 0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(imageWidth, imageHeight) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val canvasW = size.width.toFloat()
                    val canvasH = size.height.toFloat()

                    if (canvasW <= 0f || canvasH <= 0f || imageWidth <= 0f || imageHeight <= 0f) return@awaitEachGesture

                    val scaleX = canvasW / imageWidth
                    val scaleY = canvasH / imageHeight
                    val baseScale = min(scaleX, scaleY)
                    val currentDisplayScale = baseScale * scale

                    val displayedW = imageWidth * currentDisplayScale
                    val displayedH = imageHeight * currentDisplayScale
                    val imgLeft = (canvasW - displayedW) / 2f + offset.x
                    val imgTop = (canvasH - displayedH) / 2f + offset.y

                    fun screenToImage(pos: Offset): PointF {
                        val ix = (pos.x - imgLeft) / currentDisplayScale
                        val iy = (pos.y - imgTop) / currentDisplayScale
                        return PointF(ix, iy)
                    }

                    val startPos = firstDown.position
                    val startImgPt = screenToImage(startPos)
                    val hitRadiusImg = (22.dp.toPx()) / currentDisplayScale

                    var dragMode = CanvasDragMode.NONE
                    var activeBlock = currentBlocks.find { it.id == currentSelectedBlockId }
                    var initialBlockState = activeBlock

                    if (activeBlock != null) {
                        val b = activeBlock.bounds
                        val corners = listOf(
                            PointF(b.left, b.top),
                            PointF(b.right, b.top),
                            PointF(b.right, b.bottom),
                            PointF(b.left, b.bottom)
                        )
                        for (i in corners.indices) {
                            val c = corners[i]
                            if (hypot(startImgPt.x - c.x, startImgPt.y - c.y) <= hitRadiusImg) {
                                dragMode = when (i) {
                                    0 -> CanvasDragMode.RESIZE_CORNER_0
                                    1 -> CanvasDragMode.RESIZE_CORNER_1
                                    2 -> CanvasDragMode.RESIZE_CORNER_2
                                    else -> CanvasDragMode.RESIZE_CORNER_3
                                }
                                break
                            }
                        }

                        if (dragMode == CanvasDragMode.NONE && b.contains(startImgPt.x, startImgPt.y)) {
                            dragMode = CanvasDragMode.MOVE_BOX
                        }
                    }

                    if (dragMode == CanvasDragMode.NONE) {
                        val clickedBlock = currentBlocks.findLast { it.bounds.contains(startImgPt.x, startImgPt.y) }
                        if (clickedBlock != null) {
                            currentOnSelectBlock(clickedBlock.id)
                            activeBlock = clickedBlock
                            initialBlockState = clickedBlock
                            dragMode = CanvasDragMode.MOVE_BOX
                        } else {
                            dragMode = CanvasDragMode.PAN
                        }
                    }

                    var lastCentroid = startPos
                    var hasDragged = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val downPointers = event.changes.filter { it.pressed }
                        if (downPointers.isEmpty()) break

                        if (downPointers.size > 1) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            scale = (scale * zoomChange).coerceIn(0.5f, 6f)
                            offset += panChange
                            hasDragged = true
                            event.changes.forEach { it.consume() }
                        } else {
                            val currentPos = downPointers.first().position
                            val pan = currentPos - lastCentroid
                            lastCentroid = currentPos

                            if (pan.getDistance() > 1.5f) {
                                hasDragged = true
                            }

                            when (dragMode) {
                                CanvasDragMode.PAN -> {
                                    offset += pan
                                    downPointers.first().consume()
                                }
                                CanvasDragMode.MOVE_BOX -> {
                                    if (activeBlock != null) {
                                        val dxImg = pan.x / currentDisplayScale
                                        val dyImg = pan.y / currentDisplayScale
                                        val updated = activeBlock.moveBy(dxImg, dyImg, imageWidth, imageHeight)
                                        activeBlock = updated
                                        currentOnUpdateBlock(updated)
                                        downPointers.first().consume()
                                    }
                                }
                                CanvasDragMode.RESIZE_CORNER_0,
                                CanvasDragMode.RESIZE_CORNER_1,
                                CanvasDragMode.RESIZE_CORNER_2,
                                CanvasDragMode.RESIZE_CORNER_3 -> {
                                    if (activeBlock != null) {
                                        val cornerIdx = when (dragMode) {
                                            CanvasDragMode.RESIZE_CORNER_0 -> 0
                                            CanvasDragMode.RESIZE_CORNER_1 -> 1
                                            CanvasDragMode.RESIZE_CORNER_2 -> 2
                                            else -> 3
                                        }
                                        val curImgPt = screenToImage(currentPos)
                                        val updated = activeBlock.resizeCornerAnchor(cornerIdx, curImgPt, imageWidth, imageHeight)
                                        activeBlock = updated
                                        currentOnUpdateBlock(updated)
                                        downPointers.first().consume()
                                    }
                                }
                                CanvasDragMode.NONE -> {}
                            }
                        }
                    }

                    // Tap / Click handling (single vs double tap)
                    if (!hasDragged) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300 && (startPos - lastTapPos).getDistance() < 35f) {
                            // Double tap: reset zoom & pan
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            // Single tap
                            val clickedBlock = currentBlocks.findLast { it.bounds.contains(startImgPt.x, startImgPt.y) }
                            currentOnSelectBlock(clickedBlock?.id)
                        }
                        lastTapTime = now
                        lastTapPos = startPos
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 0f || canvasH <= 0f || imageWidth <= 0f || imageHeight <= 0f) return@Canvas

            val scaleX = canvasW / imageWidth
            val scaleY = canvasH / imageHeight
            val baseScale = min(scaleX, scaleY)
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

                // 2. Render Text Blocks using the Unified Layout Engine (100% WYSIWYG match to final render)
                for (block in blocks) {
                    val isSelected = block.id == selectedBlockId
                    val b = block.bounds
                    val textToRender = TextPostProcessor.processText(block.translatedText, block.originalText)
                    if (textToRender.isBlank() || b.width() <= 0 || b.height() <= 0) continue

                    val blockTypeface = fontManager.getTypefaceForLanguage(block.language ?: Language.ENG, block.customFontStyle)
                    fillPaint.typeface = blockTypeface
                    strokePaint.typeface = blockTypeface

                    val renderConfig = RenderConfig(
                        alignment = block.customAlignment,
                        direction = if (block.isVertical) com.yuu18id.mangatranslator.domain.model.TextDirection.VERTICAL else com.yuu18id.mangatranslator.domain.model.TextDirection.HORIZONTAL
                    )

                    val layoutResult: LayoutResult = if (block.customFontSize != null) {
                        layoutEngine.layoutWithFontSize(
                            text = textToRender,
                            targetWidth = b.width(),
                            targetHeight = b.height(),
                            fontSize = block.customFontSize,
                            language = block.language,
                            config = renderConfig,
                            isVertical = block.isVertical,
                            fontStyle = block.customFontStyle
                        )
                    } else {
                        layoutEngine.calculateLayout(
                            text = textToRender,
                            targetWidth = b.width(),
                            targetHeight = b.height(),
                            estimatedOriginalFontSize = b.height() / 3f,
                            language = block.language,
                            config = renderConfig,
                            isVertical = block.isVertical,
                            fontStyle = block.customFontStyle
                        )
                    }

                    if (layoutResult.lines.isEmpty()) continue

                    val fontSize = layoutResult.fontSize
                    fillPaint.textSize = fontSize
                    strokePaint.textSize = fontSize
                    strokePaint.strokeWidth = max(5.0f, fontSize * 0.28f)

                    if (block.isVertical) {
                        drawVerticalTextOnCanvas(nativeCanvas, layoutResult, b, fillPaint, strokePaint)
                    } else {
                        drawHorizontalTextOnCanvas(nativeCanvas, layoutResult, b, fillPaint, strokePaint, block.customAlignment)
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

private fun drawHorizontalTextOnCanvas(
    canvas: android.graphics.Canvas,
    layoutResult: LayoutResult,
    bounds: RectF,
    textPaint: Paint,
    strokePaint: Paint,
    alignment: TextAlignment
) {
    val effectiveAlignment = if (alignment == TextAlignment.AUTO) TextAlignment.CENTER else alignment
    val fontMetrics = textPaint.fontMetrics
    val textHeight = fontMetrics.descent - fontMetrics.ascent
    val lineSpacing = layoutResult.fontSize * 1.18f
    val totalBlockHeight = (layoutResult.lines.size - 1) * lineSpacing + textHeight
    val firstLineBaseline = bounds.centerY() - totalBlockHeight / 2f - fontMetrics.ascent

    for ((index, line) in layoutResult.lines.withIndex()) {
        val lineWidth = textPaint.measureText(line)
        val startX = when (effectiveAlignment) {
            TextAlignment.LEFT -> bounds.left + 4f
            TextAlignment.RIGHT -> bounds.right - lineWidth - 4f
            else -> bounds.centerX() - lineWidth / 2f
        }
        val lineY = firstLineBaseline + index * lineSpacing

        canvas.drawText(line, startX, lineY, strokePaint)
        canvas.drawText(line, startX, lineY, textPaint)
    }
}

private fun drawVerticalTextOnCanvas(
    canvas: android.graphics.Canvas,
    layoutResult: LayoutResult,
    bounds: RectF,
    textPaint: Paint,
    strokePaint: Paint
) {
    var currentX = bounds.right - (bounds.width() - layoutResult.totalWidth) / 2f - (layoutResult.lineHeights.firstOrNull() ?: 0f) / 2f

    for ((index, line) in layoutResult.lines.withIndex()) {
        var currentY = bounds.top + (bounds.height() - calculateVerticalLineHeightOnCanvas(line, textPaint)) / 2f

        for (char in line) {
            val charStr = char.toString()
            val charWidth = textPaint.measureText(charStr)
            val boundsRect = Rect()
            textPaint.getTextBounds(charStr, 0, 1, boundsRect)

            val charX = currentX - charWidth / 2f
            currentY += boundsRect.height()

            canvas.drawText(charStr, charX, currentY, strokePaint)
            canvas.drawText(charStr, charX, currentY, textPaint)

            currentY += textPaint.fontMetrics.descent
        }

        if (index + 1 < layoutResult.lineHeights.size) {
            currentX -= layoutResult.lineHeights[index + 1] * 1.2f
        }
    }
}

private fun calculateVerticalLineHeightOnCanvas(line: String, paint: Paint): Float {
    var height = 0f
    val bounds = Rect()
    for (char in line) {
        paint.getTextBounds(char.toString(), 0, 1, bounds)
        height += bounds.height() + paint.fontMetrics.descent
    }
    return height
}

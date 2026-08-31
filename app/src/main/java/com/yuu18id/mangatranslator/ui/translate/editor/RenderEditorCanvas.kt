package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
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
import com.yuu18id.mangatranslator.data.rendering.FontManager
import com.yuu18id.mangatranslator.data.rendering.LayoutResult
import com.yuu18id.mangatranslator.data.rendering.TextLayoutEngine
import com.yuu18id.mangatranslator.data.textline.TextPostProcessor
import com.yuu18id.mangatranslator.domain.model.CustomFontFamily
import com.yuu18id.mangatranslator.domain.model.CustomFontStyle
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
    originalBitmap: ImageBitmap? = null,
    showOriginal: Boolean = false,
    isEyedropperActive: Boolean = false,
    onColorSampled: ((Int) -> Unit)? = null,
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
    val currentIsEyedropperActive by rememberUpdatedState(isEyedropperActive)
    val currentOnColorSampled by rememberUpdatedState(onColorSampled)
    val currentShowOriginal by rememberUpdatedState(showOriginal)

    var eyedropperScreenPos by remember { mutableStateOf<Offset?>(null) }
    var eyedropperImgPt by remember { mutableStateOf<PointF?>(null) }
    var sampledColorInt by remember { mutableStateOf<Int?>(null) }

    val sampleSourceBitmap = (originalBitmap ?: inpaintedBitmap).asAndroidBitmap()

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
            .pointerInput(imageWidth, imageHeight, isEyedropperActive) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val canvasW = size.width.toFloat()
                    val canvasH = size.height.toFloat()

                    if (canvasW <= 0f || canvasH <= 0f || imageWidth <= 0f || imageHeight <= 0f) return@awaitEachGesture

                    val baseScale = min(canvasW / imageWidth, canvasH / imageHeight)
                    val fitWidth = imageWidth * baseScale
                    val fitHeight = imageHeight * baseScale

                    fun screenToImage(pos: Offset, s: Float, off: Offset): PointF {
                        val cX = canvasW / 2f + off.x
                        val cY = canvasH / 2f + off.y
                        val iLeft = cX - (fitWidth * s) / 2f
                        val iTop = cY - (fitHeight * s) / 2f
                        val dScale = baseScale * s
                        val ix = (pos.x - iLeft) / dScale
                        val iy = (pos.y - iTop) / dScale
                        return PointF(ix.coerceIn(0f, imageWidth), iy.coerceIn(0f, imageHeight))
                    }

                    if (currentIsEyedropperActive) {
                        val startPos = firstDown.position
                        val startImgPt = screenToImage(startPos, scale, offset)
                        val px = startImgPt.x.toInt().coerceIn(0, sampleSourceBitmap.width - 1)
                        val py = startImgPt.y.toInt().coerceIn(0, sampleSourceBitmap.height - 1)
                        val col = sampleSourceBitmap.getPixel(px, py)
                        eyedropperScreenPos = startPos
                        eyedropperImgPt = PointF(px.toFloat(), py.toFloat())
                        sampledColorInt = col
                        currentOnColorSampled?.invoke(col)

                        while (true) {
                            val event = awaitPointerEvent()
                            val downPointers = event.changes.filter { it.pressed }
                            if (downPointers.isEmpty()) break

                            if (downPointers.size >= 2) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val oldScale = scale
                                val newScale = (scale * zoom).coerceIn(1.0f, 5.0f)

                                val center = Offset(canvasW / 2f, canvasH / 2f)
                                val centroidFromCenter = centroid - center
                                val scaleFactor = newScale / oldScale
                                val unClampedOffset = (offset - centroidFromCenter) * scaleFactor + centroidFromCenter + pan

                                scale = newScale
                                offset = clampOffset(unClampedOffset, newScale, canvasW, canvasH, fitWidth, fitHeight)

                                event.changes.forEach { it.consume() }
                            } else if (downPointers.size == 1) {
                                val curPos = downPointers[0].position
                                val curImgPt = screenToImage(curPos, scale, offset)
                                val curPx = curImgPt.x.toInt().coerceIn(0, sampleSourceBitmap.width - 1)
                                val curPy = curImgPt.y.toInt().coerceIn(0, sampleSourceBitmap.height - 1)
                                val curCol = sampleSourceBitmap.getPixel(curPx, curPy)
                                eyedropperScreenPos = curPos
                                eyedropperImgPt = PointF(curPx.toFloat(), curPy.toFloat())
                                sampledColorInt = curCol
                                currentOnColorSampled?.invoke(curCol)
                                downPointers[0].consume()
                            }
                        }
                        return@awaitEachGesture
                    }

                    val startPos = firstDown.position
                    val startImgPt = screenToImage(startPos, scale, offset)
                    val currentDisplayScale = baseScale * scale
                    val hitRadiusImg = (26.dp.toPx()) / currentDisplayScale

                    var dragMode = CanvasDragMode.NONE
                    var activeBlock = currentBlocks.find { it.id == currentSelectedBlockId }
                    var initialBounds = activeBlock?.bounds

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
                            initialBounds = clickedBlock.bounds
                            dragMode = CanvasDragMode.MOVE_BOX
                        } else {
                            dragMode = CanvasDragMode.PAN
                        }
                    }

                    var hasDragged = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val downPointers = event.changes.filter { it.pressed }
                        if (downPointers.isEmpty()) break

                        if (downPointers.size >= 2) {
                            // Two-finger gesture: Focal Centroid Zoom & Pan
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(1.0f, 5.0f)

                            val center = Offset(canvasW / 2f, canvasH / 2f)
                            val centroidFromCenter = centroid - center
                            val scaleFactor = newScale / oldScale
                            val unClampedOffset = (offset - centroidFromCenter) * scaleFactor + centroidFromCenter + pan

                            scale = newScale
                            offset = clampOffset(unClampedOffset, newScale, canvasW, canvasH, fitWidth, fitHeight)

                            event.changes.forEach { it.consume() }
                            dragMode = CanvasDragMode.NONE
                            hasDragged = true
                        } else if (downPointers.size == 1) {
                            val change = downPointers.first()
                            val distFromDown = (change.position - startPos).getDistance()

                            if (distFromDown > 4f) {
                                hasDragged = true
                            }

                            if (hasDragged) {
                                change.consume()
                                val curImgPt = screenToImage(change.position, scale, offset)

                                when (dragMode) {
                                    CanvasDragMode.PAN -> {
                                        if (scale > 1.0f) {
                                            val unClamped = offset + (change.position - change.previousPosition)
                                            offset = clampOffset(unClamped, scale, canvasW, canvasH, fitWidth, fitHeight)
                                        }
                                    }
                                    CanvasDragMode.MOVE_BOX -> {
                                        if (activeBlock != null && initialBounds != null) {
                                            val totalDx = curImgPt.x - startImgPt.x
                                            val totalDy = curImgPt.y - startImgPt.y
                                            val w = initialBounds.width()
                                            val h = initialBounds.height()
                                            val newLeft = (initialBounds.left + totalDx).coerceIn(0f, imageWidth - w)
                                            val newTop = (initialBounds.top + totalDy).coerceIn(0f, imageHeight - h)
                                            val updated = activeBlock.copy(bounds = RectF(newLeft, newTop, newLeft + w, newTop + h))
                                            activeBlock = updated
                                            currentOnUpdateBlock(updated)
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
                                            val updated = activeBlock.resizeCornerAnchor(cornerIdx, curImgPt, imageWidth, imageHeight)
                                            activeBlock = updated
                                            currentOnUpdateBlock(updated)
                                        }
                                    }
                                    CanvasDragMode.NONE -> {}
                                }
                            }
                        }
                    }

                    // Tap / Click handling (single vs double tap)
                    if (!hasDragged) {
                        val hitBlock = currentBlocks.findLast { it.bounds.contains(startImgPt.x, startImgPt.y) }
                        val now = System.currentTimeMillis()
                        if (hitBlock == null && (now - lastTapTime) < 300L && (firstDown.position - lastTapPos).getDistance() < 40f) {
                            // Double tap: toggle zoom 2.5x to focal point or reset to 1.0x
                            if (scale > 1.05f) {
                                scale = 1.0f
                                offset = Offset.Zero
                            } else {
                                val targetScale = 2.5f
                                val center = Offset(canvasW / 2f, canvasH / 2f)
                                val tapFromCenter = firstDown.position - center
                                val targetOffset = -tapFromCenter * (targetScale - 1f)
                                scale = targetScale
                                offset = clampOffset(targetOffset, targetScale, canvasW, canvasH, fitWidth, fitHeight)
                            }
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                            lastTapPos = firstDown.position
                            currentOnSelectBlock(hitBlock?.id)
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            if (canvasW <= 0f || canvasH <= 0f || imageWidth <= 0f || imageHeight <= 0f) return@Canvas

            val baseScale = min(canvasW / imageWidth, canvasH / imageHeight)
            val fitWidth = imageWidth * baseScale
            val fitHeight = imageHeight * baseScale
            val currentDisplayScale = baseScale * scale

            val centerX = canvasW / 2f + offset.x
            val centerY = canvasH / 2f + offset.y
            val imgLeft = centerX - (fitWidth * scale) / 2f
            val imgTop = centerY - (fitHeight * scale) / 2f

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                nativeCanvas.save()

                // Translate & Scale native canvas so all drawing is in 1:1 image pixel space!
                nativeCanvas.translate(imgLeft, imgTop)
                nativeCanvas.scale(currentDisplayScale, currentDisplayScale)

                // 1. Draw Background Bitmap (Original when showOriginal or in eyedropper mode, otherwise Inpainted)
                val sourceImageBitmap = if (currentShowOriginal || currentIsEyedropperActive) (originalBitmap ?: inpaintedBitmap) else inpaintedBitmap
                val nativeBitmap = sourceImageBitmap.asAndroidBitmap()
                nativeCanvas.drawBitmap(nativeBitmap, 0f, 0f, null)

                // 2. Render Text Blocks using the Unified Layout Engine (100% WYSIWYG match to final render)
                if (!currentShowOriginal || currentIsEyedropperActive) {
                    for (block in blocks) {
                        val isSelected = block.id == selectedBlockId
                        val b = block.bounds
                        val textToRender = TextPostProcessor.processText(block.translatedText, block.originalText)
                        if (textToRender.isBlank() || b.width() <= 0 || b.height() <= 0) continue

                        val blockTypeface = fontManager.getTypefaceForLanguage(
                            language = block.language ?: Language.ENG,
                            fontStyle = block.customFontStyle,
                            fontFamily = block.customFontFamily
                        )
                        fillPaint.typeface = blockTypeface
                        strokePaint.typeface = blockTypeface

                        val blockTextColor = block.getEffectiveTextColor()
                        val blockStrokeColor = block.getEffectiveStrokeColor(blockTextColor)
                        fillPaint.color = blockTextColor
                        strokePaint.color = blockStrokeColor

                        if (currentIsEyedropperActive) {
                            fillPaint.alpha = 45
                            strokePaint.alpha = 45
                        } else {
                            fillPaint.alpha = 255
                            strokePaint.alpha = 255
                        }

                    val isCJK = block.language in listOf(Language.JPN, Language.CHS, Language.CHT, Language.KOR)
                    val isEffectiveVertical = isCJK && block.isVertical

                    val renderConfig = RenderConfig(
                        alignment = block.customAlignment,
                        direction = if (isEffectiveVertical) com.yuu18id.mangatranslator.domain.model.TextDirection.VERTICAL else com.yuu18id.mangatranslator.domain.model.TextDirection.HORIZONTAL
                    )

                    val layoutResult: LayoutResult = if (block.customFontSize != null) {
                        layoutEngine.layoutWithFontSize(
                            text = textToRender,
                            targetWidth = b.width(),
                            targetHeight = b.height(),
                            fontSize = block.customFontSize,
                            language = block.language,
                            config = renderConfig,
                            isVertical = isEffectiveVertical,
                            fontStyle = block.customFontStyle,
                            fontFamily = block.customFontFamily
                        )
                    } else {
                        layoutEngine.calculateLayout(
                            text = textToRender,
                            targetWidth = b.width(),
                            targetHeight = b.height(),
                            estimatedOriginalFontSize = b.height() / 3f,
                            language = block.language,
                            config = renderConfig,
                            isVertical = isEffectiveVertical,
                            fontStyle = block.customFontStyle,
                            fontFamily = block.customFontFamily
                        )
                    }

                    if (layoutResult.lines.isEmpty()) continue

                    val fontSize = layoutResult.fontSize
                    fillPaint.textSize = fontSize
                    strokePaint.textSize = fontSize
                    strokePaint.strokeWidth = max(5.0f, fontSize * 0.28f)

                    if (isEffectiveVertical) {
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
                }

                nativeCanvas.restore()

                // 4. Magnifier Loupe when Eyedropper mode is active
                if (currentIsEyedropperActive && eyedropperScreenPos != null && eyedropperImgPt != null && sampledColorInt != null) {
                    val scrPos = eyedropperScreenPos!!
                    val imgPt = eyedropperImgPt!!
                    val sampleCol = sampledColorInt!!

                    val loupeRadius = 135f
                    val loupeY = if (scrPos.y - 210f < loupeRadius + 30f) {
                        scrPos.y + 210f
                    } else {
                        scrPos.y - 210f
                    }
                    val loupeCenter = Offset(scrPos.x.coerceIn(loupeRadius + 24f, canvasW - loupeRadius - 24f), loupeY)

                    // Draw magnified image circular clip
                    nativeCanvas.save()
                    val clipPath = android.graphics.Path().apply {
                        addCircle(loupeCenter.x, loupeCenter.y, loupeRadius, android.graphics.Path.Direction.CW)
                    }
                    nativeCanvas.clipPath(clipPath)

                    val bgLoupePaint = Paint().apply {
                        style = Paint.Style.FILL
                        color = android.graphics.Color.DKGRAY
                    }
                    nativeCanvas.drawCircle(loupeCenter.x, loupeCenter.y, loupeRadius, bgLoupePaint)

                    val zoomFactor = 6.0f
                    nativeCanvas.translate(loupeCenter.x, loupeCenter.y)
                    nativeCanvas.scale(zoomFactor, zoomFactor)
                    nativeCanvas.translate(-imgPt.x, -imgPt.y)

                    nativeCanvas.drawBitmap(sampleSourceBitmap, 0f, 0f, null)
                    nativeCanvas.restore()

                    // Draw outer color ring and borders
                    val ringPaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 14f
                        color = sampleCol
                        isAntiAlias = true
                    }
                    val ringBorderPaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        color = android.graphics.Color.WHITE
                        isAntiAlias = true
                    }
                    val ringShadowPaint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                        color = android.graphics.Color.BLACK
                        isAntiAlias = true
                    }

                    nativeCanvas.drawCircle(loupeCenter.x, loupeCenter.y, loupeRadius, ringPaint)
                    nativeCanvas.drawCircle(loupeCenter.x, loupeCenter.y, loupeRadius + 7f, ringBorderPaint)
                    nativeCanvas.drawCircle(loupeCenter.x, loupeCenter.y, loupeRadius - 7f, ringBorderPaint)
                    nativeCanvas.drawCircle(loupeCenter.x, loupeCenter.y, loupeRadius + 9f, ringShadowPaint)

                    // Draw center crosshairs
                    val crossPaint = Paint().apply {
                        color = android.graphics.Color.RED
                        strokeWidth = 3f
                        style = Paint.Style.STROKE
                        isAntiAlias = true
                    }
                    val chSize = 18f
                    nativeCanvas.drawLine(loupeCenter.x - chSize, loupeCenter.y, loupeCenter.x + chSize, loupeCenter.y, crossPaint)
                    nativeCanvas.drawLine(loupeCenter.x, loupeCenter.y - chSize, loupeCenter.x, loupeCenter.y + chSize, crossPaint)
                    nativeCanvas.drawCircle(loupeCenter.x, loupeCenter.y, 4f, crossPaint)

                    // Hex code badge below the loupe
                    val hexStr = String.format("#%06X", (0xFFFFFF and sampleCol))
                    val textBgPaint = Paint().apply {
                        color = android.graphics.Color.argb(220, 0, 0, 0)
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val textPaint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 30f
                        textAlign = Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                    }
                    val tagRect = RectF(loupeCenter.x - 75f, loupeCenter.y + loupeRadius + 10f, loupeCenter.x + 75f, loupeCenter.y + loupeRadius + 52f)
                    nativeCanvas.drawRoundRect(tagRect, 16f, 16f, textBgPaint)
                    nativeCanvas.drawText(hexStr, loupeCenter.x, loupeCenter.y + loupeRadius + 41f, textPaint)
                }
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
    com.yuu18id.mangatranslator.data.rendering.MangaTextDrawHelper.drawHorizontalText(
        canvas = canvas,
        layoutResult = layoutResult,
        bounds = bounds,
        textPaint = textPaint,
        strokePaint = strokePaint,
        alignment = alignment,
        disableFontBorder = false
    )
}

private fun drawVerticalTextOnCanvas(
    canvas: android.graphics.Canvas,
    layoutResult: LayoutResult,
    bounds: RectF,
    textPaint: Paint,
    strokePaint: Paint
) {
    com.yuu18id.mangatranslator.data.rendering.MangaTextDrawHelper.drawVerticalText(
        canvas = canvas,
        layoutResult = layoutResult,
        bounds = bounds,
        textPaint = textPaint,
        strokePaint = strokePaint,
        disableFontBorder = false
    )
}

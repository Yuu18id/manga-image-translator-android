package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuu18id.mangatranslator.R
import com.yuu18id.mangatranslator.data.textline.TextPostProcessor
import com.yuu18id.mangatranslator.domain.model.CustomFontFamily
import com.yuu18id.mangatranslator.domain.model.CustomFontStyle
import com.yuu18id.mangatranslator.domain.model.TextAlignment
import com.yuu18id.mangatranslator.domain.model.TextBlock
import kotlin.math.min
import kotlin.math.sqrt

fun computeEffectiveFontSize(block: EditableRenderBlock): Float {
    if (block.customFontSize != null) return block.customFontSize
    val b = block.bounds
    val text = block.translatedText.trim()
    if (text.isBlank() || b.width() <= 0 || b.height() <= 0) return 18f

    val maxAllowedWidth = b.width() * 0.92f
    val maxAllowedHeight = b.height() * 0.90f
    val len = text.length.coerceAtLeast(1)
    val area = maxAllowedWidth * maxAllowedHeight
    val charArea = area / len
    return sqrt(charArea * 0.70f).coerceIn(10f, 42f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderEditorDialog(
    originalBitmap: ImageBitmap? = null,
    inpaintedBitmap: ImageBitmap,
    initialBlocks: List<TextBlock>,
    onDismiss: () -> Unit,
    onConfirmBlocks: (List<TextBlock>) -> Unit
) {
    BackHandler(onBack = onDismiss)

    var blocks by remember(initialBlocks) {
        mutableStateOf(
            initialBlocks.mapIndexed { idx, b ->
                val clean = b.copy(translatedText = TextPostProcessor.processText(b.translatedText, b.text))
                EditableRenderBlock.fromTextBlock(idx + 1, clean)
            }
        )
    }
    var selectedBlockId by remember { mutableStateOf<Int?>(null) }
    var isEditingTextContent by remember { mutableStateOf(false) }
    var textEditingDraft by remember { mutableStateOf("") }
    var textEditingAlignment by remember { mutableStateOf(TextAlignment.CENTER) }
    var showColorPicker by remember { mutableStateOf(false) }
    var isEyedropperActive by remember { mutableStateOf(false) }
    var eyedropperSampledColor by remember { mutableStateOf<Int?>(null) }
    var showOriginalImage by remember { mutableStateOf(false) }

    val selectedBlock = blocks.find { it.id == selectedBlockId }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.typeset_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        if (originalBitmap != null) {
                            IconButton(
                                onClick = { showOriginalImage = !showOriginalImage }
                            ) {
                                Icon(
                                    imageVector = if (showOriginalImage) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (showOriginalImage) stringResource(R.string.typeset_show_translated) else stringResource(R.string.typeset_show_original),
                                    tint = if (showOriginalImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (selectedBlock != null) {
                            IconButton(
                                onClick = {
                                    blocks = blocks.filter { it.id != selectedBlockId }
                                    selectedBlockId = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                blocks = initialBlocks.mapIndexed { idx, b ->
                                    EditableRenderBlock.fromTextBlock(idx + 1, b)
                                }
                                selectedBlockId = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_reset)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (isEyedropperActive) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val sampleCol = eyedropperSampledColor ?: selectedBlock?.getEffectiveTextColor() ?: android.graphics.Color.BLACK
                                val sampleColorObj = Color(sampleCol)
                                val lum = 0.299 * (sampleColorObj.red * 255) + 0.587 * (sampleColorObj.green * 255) + 0.114 * (sampleColorObj.blue * 255)
                                val strokeColor = if (lum > 140) Color.Black else Color.White

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(sampleColorObj, CircleShape)
                                        .border(2.dp, strokeColor, CircleShape)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    val hexStr = String.format("#%06X", (0xFFFFFF and sampleCol))
                                    Text(
                                        text = hexStr,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.typeset_eyedropper_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { isEyedropperActive = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_cancel))
                                }

                                Button(
                                    onClick = {
                                        if (eyedropperSampledColor != null && selectedBlock != null) {
                                            val updated = selectedBlock.copy(customTextColor = eyedropperSampledColor)
                                            blocks = blocks.map { if (it.id == updated.id) updated else it }
                                        }
                                        isEyedropperActive = false
                                    },
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.typeset_eyedropper_apply))
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Inspector Card for Selected Block (Does NOT flicker on drag!)
                            if (selectedBlock != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Row 1: Header (ID Badge + Text Preview + Edit Text CTA)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "#${selectedBlock.id}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                            Text(
                                                text = selectedBlock.translatedText.ifBlank { stringResource(R.string.typeset_empty_bubble) },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        FilledTonalButton(
                                            onClick = {
                                                textEditingDraft = selectedBlock.translatedText
                                                textEditingAlignment = selectedBlock.customAlignment ?: TextAlignment.CENTER
                                                isEditingTextContent = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.typeset_edit_text_btn), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    // Row 2: Typography & Styling (Font Family, Style, Size)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Font Family (Wild Words vs Badaboom)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                val isBadaboom = selectedBlock.customFontFamily == CustomFontFamily.BADABOOM

                                                IconButton(
                                                    onClick = {
                                                        val updated = selectedBlock.copy(customFontFamily = CustomFontFamily.WILD_WORDS)
                                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.TextFields,
                                                        contentDescription = stringResource(R.string.typeset_font_wild_words),
                                                        tint = if (!isBadaboom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val updated = selectedBlock.copy(customFontFamily = CustomFontFamily.BADABOOM)
                                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Campaign,
                                                        contentDescription = stringResource(R.string.typeset_font_badaboom),
                                                        tint = if (isBadaboom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Font Style Controls (Bold, Italic)
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                val isBold = selectedBlock.customFontStyle.isBold
                                                val isItalic = selectedBlock.customFontStyle.isItalic

                                                IconButton(
                                                    onClick = {
                                                        val newStyle = selectedBlock.customFontStyle.withBold(!isBold)
                                                        val updated = selectedBlock.copy(customFontStyle = newStyle)
                                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FormatBold,
                                                        contentDescription = stringResource(R.string.typeset_style_bold),
                                                        tint = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val newStyle = selectedBlock.customFontStyle.withItalic(!isItalic)
                                                        val updated = selectedBlock.copy(customFontStyle = newStyle)
                                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FormatItalic,
                                                        contentDescription = stringResource(R.string.typeset_style_italic),
                                                        tint = if (isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Font Size Stepper Pill
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        val cur = computeEffectiveFontSize(selectedBlock)
                                                        val updated = selectedBlock.copy(customFontSize = (cur - 2f).coerceAtLeast(8f))
                                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.typeset_decrease_font), modifier = Modifier.size(16.dp))
                                                }

                                                Text(
                                                    text = "${computeEffectiveFontSize(selectedBlock).toInt()} pt",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )

                                                IconButton(
                                                    onClick = {
                                                        val cur = computeEffectiveFontSize(selectedBlock)
                                                        val updated = selectedBlock.copy(customFontSize = (cur + 2f).coerceAtMost(60f))
                                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.typeset_increase_font), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }

                                    // Row 3: Layout & Color (Alignment, Swatch/Palette, Eyedropper)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Text Alignment Controls
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                val currentAlign = selectedBlock.customAlignment ?: TextAlignment.CENTER
                                                IconButton(
                                                    onClick = {
                                                        val updated = selectedBlock.copy(customAlignment = TextAlignment.LEFT)
                                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                                                        contentDescription = stringResource(R.string.typeset_align_left),
                                                        tint = if (currentAlign == TextAlignment.LEFT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val updated = selectedBlock.copy(customAlignment = TextAlignment.CENTER)
                                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FormatAlignCenter,
                                                        contentDescription = stringResource(R.string.typeset_align_center),
                                                        tint = if (currentAlign == TextAlignment.CENTER || currentAlign == TextAlignment.AUTO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val updated = selectedBlock.copy(customAlignment = TextAlignment.RIGHT)
                                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.FormatAlignRight,
                                                        contentDescription = stringResource(R.string.typeset_align_right),
                                                        tint = if (currentAlign == TextAlignment.RIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Text Color & Eyedropper Control
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            val effectiveColorInt = selectedBlock.getEffectiveTextColor()
                                            val isCustomColor = selectedBlock.customTextColor != null

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { showColorPicker = true }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .background(Color(effectiveColorInt), CircleShape)
                                                            .border(
                                                                width = 1.dp,
                                                                color = MaterialTheme.colorScheme.outline,
                                                                shape = CircleShape
                                                            )
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Palette,
                                                        contentDescription = stringResource(R.string.typeset_color_picker_title),
                                                        tint = if (isCustomColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isCustomColor) String.format("#%06X", (0xFFFFFF and effectiveColorInt)) else stringResource(R.string.typeset_color_auto),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isCustomColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        isEyedropperActive = true
                                                        showColorPicker = false
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Colorize,
                                                        contentDescription = stringResource(R.string.typeset_color_pick_from_image),
                                                        tint = if (isEyedropperActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Subtle Guidance Hint
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.typeset_hint_idle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Primary Confirmation Action Button
                        Button(
                            onClick = {
                                val updatedTextBlocks = blocks.map { 
                                    val tb = it.toTextBlock()
                                    tb.copy(translatedText = TextPostProcessor.processText(tb.translatedText, tb.text))
                                }
                                onConfirmBlocks(updatedTextBlocks)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.typeset_save_apply, blocks.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                RenderEditorCanvas(
                    inpaintedBitmap = inpaintedBitmap,
                    originalBitmap = originalBitmap,
                    showOriginal = showOriginalImage,
                    blocks = blocks,
                    selectedBlockId = selectedBlockId,
                    onSelectBlock = { selectedBlockId = it },
                    onUpdateBlock = { updated ->
                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                    },
                    isEyedropperActive = isEyedropperActive,
                    onColorSampled = { eyedropperSampledColor = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Dialog for editing translated text content
        if (isEditingTextContent && selectedBlock != null) {
            AlertDialog(
                onDismissRequest = { isEditingTextContent = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.typeset_dialog_edit_title, selectedBlock.id), style = MaterialTheme.typography.titleLarge)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (selectedBlock.originalText.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = stringResource(R.string.typeset_ocr_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = selectedBlock.originalText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = textEditingDraft,
                            onValueChange = { textEditingDraft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            label = { Text(stringResource(R.string.typeset_translated_label)) },
                            shape = RoundedCornerShape(14.dp)
                        )

                        // Alignment Selector Row inside Edit Text Dialog
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.typeset_alignment_label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { textEditingAlignment = TextAlignment.LEFT },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                                            contentDescription = stringResource(R.string.typeset_align_left),
                                            tint = if (textEditingAlignment == TextAlignment.LEFT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { textEditingAlignment = TextAlignment.CENTER },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FormatAlignCenter,
                                            contentDescription = stringResource(R.string.typeset_align_center),
                                            tint = if (textEditingAlignment == TextAlignment.CENTER || textEditingAlignment == TextAlignment.AUTO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { textEditingAlignment = TextAlignment.RIGHT },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.FormatAlignRight,
                                            contentDescription = stringResource(R.string.typeset_align_right),
                                            tint = if (textEditingAlignment == TextAlignment.RIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cleanText = TextPostProcessor.processText(textEditingDraft)
                            val updated = selectedBlock.copy(translatedText = cleanText, customAlignment = textEditingAlignment)
                            blocks = blocks.map { if (it.id == updated.id) updated else it }
                            isEditingTextContent = false
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.action_apply))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isEditingTextContent = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showColorPicker && selectedBlock != null) {
            TypesetColorPickerDialog(
                currentColor = selectedBlock.customTextColor,
                onDismiss = { showColorPicker = false },
                onStartEyedropper = {
                    showColorPicker = false
                    isEyedropperActive = true
                },
                onColorSelected = { picked ->
                    val updated = selectedBlock.copy(customTextColor = picked)
                    blocks = blocks.map { if (it.id == updated.id) updated else it }
                    showColorPicker = false
                }
            )
        }
    }
}

@Composable
fun TypesetColorPickerDialog(
    currentColor: Int?,
    onDismiss: () -> Unit,
    onStartEyedropper: () -> Unit,
    onColorSelected: (Int?) -> Unit
) {
    val initialColor = if (currentColor != null) Color(currentColor) else Color.Black
    var red by remember { mutableFloatStateOf(initialColor.red * 255f) }
    var green by remember { mutableFloatStateOf(initialColor.green * 255f) }
    var blue by remember { mutableFloatStateOf(initialColor.blue * 255f) }
    var isDefault by remember { mutableStateOf(currentColor == null) }

    val currentPickedColor = Color(
        red = (red / 255f).coerceIn(0f, 1f),
        green = (green / 255f).coerceIn(0f, 1f),
        blue = (blue / 255f).coerceIn(0f, 1f)
    )

    val previewColor = if (isDefault) Color.Black else currentPickedColor
    val previewColorInt = previewColor.toArgb()

    fun hexString(c: Color): String {
        val r = (c.red * 255).toInt().coerceIn(0, 255)
        val g = (c.green * 255).toInt().coerceIn(0, 255)
        val b = (c.blue * 255).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    var hexText by remember(currentPickedColor) { mutableStateOf(hexString(currentPickedColor)) }

    val presetColors = listOf(
        stringResource(R.string.typeset_color_black) to android.graphics.Color.BLACK,
        stringResource(R.string.typeset_color_white) to android.graphics.Color.WHITE,
        stringResource(R.string.typeset_color_red) to android.graphics.Color.rgb(229, 57, 53),
        stringResource(R.string.typeset_color_crimson) to android.graphics.Color.rgb(136, 14, 79),
        stringResource(R.string.typeset_color_orange) to android.graphics.Color.rgb(251, 140, 0),
        stringResource(R.string.typeset_color_yellow) to android.graphics.Color.rgb(253, 216, 53),
        stringResource(R.string.typeset_color_green) to android.graphics.Color.rgb(67, 160, 71),
        stringResource(R.string.typeset_color_cyan) to android.graphics.Color.rgb(0, 172, 193),
        stringResource(R.string.typeset_color_blue) to android.graphics.Color.rgb(30, 136, 229),
        stringResource(R.string.typeset_color_navy) to android.graphics.Color.rgb(13, 71, 161),
        stringResource(R.string.typeset_color_purple) to android.graphics.Color.rgb(142, 36, 170),
        stringResource(R.string.typeset_color_magenta) to android.graphics.Color.rgb(216, 27, 96)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.typeset_color_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Live Color Preview Swatch Card
                val lum = 0.299 * (previewColor.red * 255) + 0.587 * (previewColor.green * 255) + 0.114 * (previewColor.blue * 255)
                val strokeColor = if (lum > 140) Color.Black else Color.White

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(previewColor, CircleShape)
                                .border(2.dp, strokeColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (isDefault) stringResource(R.string.typeset_color_auto) else hexString(previewColor),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "RGB: (${(previewColor.red * 255).toInt()}, ${(previewColor.green * 255).toInt()}, ${(previewColor.blue * 255).toInt()})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. Preset Quick Color Swatches
                Text(
                    text = stringResource(R.string.typeset_color_preset),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Default Reset Button
                OutlinedButton(
                    onClick = {
                        isDefault = true
                        red = 0f; green = 0f; blue = 0f
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = if (isDefault) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.typeset_color_reset_default))
                }

                // Grid of Preset Colors (2 rows of 6)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.chunked(6).forEach { rowColors ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowColors.forEach { (name, colorInt) ->
                                val swatchColor = Color(colorInt)
                                val isSelected = !isDefault && previewColorInt == colorInt

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(swatchColor, CircleShape)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            isDefault = false
                                            red = swatchColor.red * 255f
                                            green = swatchColor.green * 255f
                                            blue = swatchColor.blue * 255f
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = name,
                                            tint = if (colorInt == android.graphics.Color.WHITE || colorInt == android.graphics.Color.rgb(253, 216, 53)) Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 3. Eyedropper from Image Button
                FilledTonalButton(
                    onClick = onStartEyedropper,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Colorize, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.typeset_color_pick_from_image),
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                // 4. Custom Color Sliders (RGB + Hex Input)
                Text(
                    text = stringResource(R.string.typeset_color_custom),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Red Slider
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Red (R)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                        Text("${red.toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = red,
                        onValueChange = {
                            isDefault = false
                            red = it
                        },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE53935),
                            activeTrackColor = Color(0xFFE53935)
                        )
                    )
                }

                // Green Slider
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Green (G)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF43A047), fontWeight = FontWeight.Bold)
                        Text("${green.toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = green,
                        onValueChange = {
                            isDefault = false
                            green = it
                        },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF43A047),
                            activeTrackColor = Color(0xFF43A047)
                        )
                    )
                }

                // Blue Slider
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Blue (B)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E88E5), fontWeight = FontWeight.Bold)
                        Text("${blue.toInt()}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = blue,
                        onValueChange = {
                            isDefault = false
                            blue = it
                        },
                        valueRange = 0f..255f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF1E88E5),
                            activeTrackColor = Color(0xFF1E88E5)
                        )
                    )
                }

                // Hex Input Field
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        hexText = input
                        val cleaned = input.removePrefix("#").trim()
                        if (cleaned.length == 6) {
                            try {
                                val parsed = android.graphics.Color.parseColor("#$cleaned")
                                isDefault = false
                                red = (android.graphics.Color.red(parsed)).toFloat()
                                green = (android.graphics.Color.green(parsed)).toFloat()
                                blue = (android.graphics.Color.blue(parsed)).toFloat()
                            } catch (_: Exception) {}
                        }
                    },
                    label = { Text(stringResource(R.string.typeset_color_hex)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isDefault) {
                        onColorSelected(null)
                    } else {
                        onColorSelected(previewColorInt)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

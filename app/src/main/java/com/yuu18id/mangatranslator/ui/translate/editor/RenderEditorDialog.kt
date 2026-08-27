package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuu18id.mangatranslator.R
import com.yuu18id.mangatranslator.data.textline.TextPostProcessor
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
                        Column {
                            Text(
                                text = stringResource(R.string.typeset_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.typeset_bubbles_count, blocks.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                                    // Row 2: Typesetting Steppers, Style & Alignment
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                RenderEditorCanvas(
                    inpaintedBitmap = inpaintedBitmap,
                    blocks = blocks,
                    selectedBlockId = selectedBlockId,
                    onSelectBlock = { selectedBlockId = it },
                    onUpdateBlock = { updated ->
                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                    },
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
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cleanText = TextPostProcessor.processText(textEditingDraft)
                            val updated = selectedBlock.copy(translatedText = cleanText)
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
    }
}

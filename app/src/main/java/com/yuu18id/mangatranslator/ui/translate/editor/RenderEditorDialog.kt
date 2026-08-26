package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuu18id.mangatranslator.domain.model.TextAlignment
import com.yuu18id.mangatranslator.domain.model.TextBlock
import kotlin.math.max
import kotlin.math.min

fun computeEffectiveFontSize(block: EditableRenderBlock): Float {
    if (block.customFontSize != null) return block.customFontSize
    val b = block.bounds
    val text = block.translatedText.trim()
    if (text.isBlank() || b.width() <= 0 || b.height() <= 0) return 18f

    val maxAllowedWidth = b.width() * 0.94f
    val maxAllowedHeight = b.height() * 0.92f
    val paint = Paint()

    var low = 12f
    var high = min(maxAllowedHeight * 0.50f, 40f).coerceAtLeast(low + 2f)
    var bestSize = low
    var iter = 0
    while (high - low >= 0.5f && iter < 14) {
        iter++
        val mid = (low + high) / 2f
        paint.textSize = mid
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var linesCount = 1
        var curSpan = 0f
        for (w in words) {
            val span = paint.measureText(if (curSpan == 0f) w else " $w")
            if (curSpan + span > maxAllowedWidth && curSpan > 0f) {
                linesCount++
                curSpan = paint.measureText(w)
            } else {
                curSpan += span
            }
        }
        val totalH = linesCount * (mid * 1.18f)
        if (totalH <= maxAllowedHeight) {
            bestSize = mid
            low = mid
        } else {
            high = mid
        }
    }
    return bestSize
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
            initialBlocks.mapIndexed { idx, b -> EditableRenderBlock.fromTextBlock(idx + 1, b) }
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
                                text = "Review Render & Typeset",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${blocks.size} balon teks (geser/ubah ukuran/edit kata)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    actions = {
                        if (selectedBlock != null) {
                            // Quick Edit Text Button
                            IconButton(
                                onClick = {
                                    textEditingDraft = selectedBlock.translatedText
                                    isEditingTextContent = true
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Teks", tint = MaterialTheme.colorScheme.primary)
                            }

                            // Decrease Font Size (-)
                            IconButton(
                                onClick = {
                                    val cur = computeEffectiveFontSize(selectedBlock)
                                    val updated = selectedBlock.copy(customFontSize = (cur - 2f).coerceAtLeast(8f))
                                    blocks = blocks.map { if (it.id == updated.id) updated else it }
                                }
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Perkecil Font")
                            }

                            // Font size display
                            Text(
                                text = "${computeEffectiveFontSize(selectedBlock).toInt()}pt",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Increase Font Size (+)
                            IconButton(
                                onClick = {
                                    val cur = computeEffectiveFontSize(selectedBlock)
                                    val updated = selectedBlock.copy(customFontSize = (cur + 2f).coerceAtMost(60f))
                                    blocks = blocks.map { if (it.id == updated.id) updated else it }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Perbesar Font")
                            }

                            // Delete Selected Block
                            IconButton(
                                onClick = {
                                    blocks = blocks.filter { it.id != selectedBlockId }
                                    selectedBlockId = null
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus Balon", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        // Reset Button
                        IconButton(
                            onClick = {
                                blocks = initialBlocks.mapIndexed { idx, b -> EditableRenderBlock.fromTextBlock(idx + 1, b) }
                                selectedBlockId = null
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Awal")
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
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Selected Block Details Strip
                        AnimatedVisibility(
                            visible = selectedBlock != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            if (selectedBlock != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color(0xFFFFC107), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "#${selectedBlock.id}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                        Text(
                                            text = selectedBlock.translatedText.take(20) + if (selectedBlock.translatedText.length > 20) "..." else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Font Size stepper buttons
                                        IconButton(
                                            onClick = {
                                                val cur = computeEffectiveFontSize(selectedBlock)
                                                val updated = selectedBlock.copy(customFontSize = (cur - 2f).coerceAtLeast(8f))
                                                blocks = blocks.map { if (it.id == updated.id) updated else it }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Kecilkan Font", modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = "${computeEffectiveFontSize(selectedBlock).toInt()}pt",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = {
                                                val cur = computeEffectiveFontSize(selectedBlock)
                                                val updated = selectedBlock.copy(customFontSize = (cur + 2f).coerceAtMost(60f))
                                                blocks = blocks.map { if (it.id == updated.id) updated else it }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Besarkan Font", modifier = Modifier.size(16.dp))
                                        }

                                        // Alignment Toggle
                                        IconButton(
                                            onClick = {
                                                val nextAlign = when (selectedBlock.customAlignment) {
                                                    TextAlignment.CENTER, TextAlignment.AUTO -> TextAlignment.LEFT
                                                    TextAlignment.LEFT -> TextAlignment.RIGHT
                                                    TextAlignment.RIGHT -> TextAlignment.CENTER
                                                    else -> TextAlignment.CENTER
                                                }
                                                val updated = selectedBlock.copy(customAlignment = nextAlign)
                                                blocks = blocks.map { if (it.id == updated.id) updated else it }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (selectedBlock.customAlignment) {
                                                    TextAlignment.LEFT -> Icons.Default.FormatAlignLeft
                                                    TextAlignment.RIGHT -> Icons.Default.FormatAlignRight
                                                    TextAlignment.CENTER, TextAlignment.AUTO -> Icons.Default.FormatAlignCenter
                                                    else -> Icons.Default.FormatAlignCenter
                                                },
                                                contentDescription = "Perataan Teks",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Edit Text Button
                                        Button(
                                            onClick = {
                                                textEditingDraft = selectedBlock.translatedText
                                                isEditingTextContent = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // Primary Action Button: Save and Apply Render
                        Button(
                            onClick = {
                                val updatedTextBlocks = blocks.map { it.toTextBlock() }
                                onConfirmBlocks(updatedTextBlocks)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Simpan & Terapkan Render (${blocks.size} Balon)",
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
                        Text("Edit Teks Terjemahan (#${selectedBlock.id})")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (selectedBlock.originalText.isNotBlank()) {
                            Text(
                                text = "Teks Asli: ${selectedBlock.originalText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = textEditingDraft,
                            onValueChange = { textEditingDraft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            label = { Text("Teks Terjemahan") },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val updated = selectedBlock.copy(translatedText = textEditingDraft)
                            blocks = blocks.map { if (it.id == updated.id) updated else it }
                            isEditingTextContent = false
                        }
                    ) {
                        Text("Terapkan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isEditingTextContent = false }) {
                        Text("Batal")
                    }
                }
            )
        }
    }
}

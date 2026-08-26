package com.yuu18id.mangatranslator.ui.translate.editor

import android.graphics.RectF
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuu18id.mangatranslator.R
import com.yuu18id.mangatranslator.domain.model.Quadrilateral

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionEditorDialog(
    imageBitmap: ImageBitmap,
    initialDetections: List<Quadrilateral>,
    onDismiss: () -> Unit,
    onConfirmDetections: (List<Quadrilateral>) -> Unit
) {
    BackHandler(onBack = onDismiss)

    var boxes by remember(initialDetections) {
        mutableStateOf(
            initialDetections.mapIndexed { idx, q -> EditableBox.fromQuadrilateral(idx + 1, q) }
        )
    }
    var selectedBoxId by remember { mutableStateOf<Int?>(null) }
    var isAddBoxMode by remember { mutableStateOf(false) }
    var nextBoxId by remember(initialDetections) { mutableIntStateOf(initialDetections.size + 1) }

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
                                text = stringResource(R.string.editor_review_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.editor_bubbles_detected, boxes.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        // Quick Delete action in Top Bar if a box is selected
                        if (selectedBoxId != null) {
                            IconButton(
                                onClick = {
                                    boxes = boxes.filter { it.id != selectedBoxId }
                                    selectedBoxId = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.editor_action_delete_box),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Toggle Add Box Mode
                        FilledIconToggleButton(
                            checked = isAddBoxMode,
                            onCheckedChange = {
                                isAddBoxMode = it
                                if (it) selectedBoxId = null
                            }
                        ) {
                            Icon(
                                imageVector = if (isAddBoxMode) Icons.Default.Check else Icons.Default.AddBox,
                                contentDescription = stringResource(R.string.editor_action_add_box)
                            )
                        }

                        // Reset to original detections
                        IconButton(
                            onClick = {
                                boxes = initialDetections.mapIndexed { idx, q ->
                                    EditableBox.fromQuadrilateral(idx + 1, q)
                                }
                                selectedBoxId = null
                                isAddBoxMode = false
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.editor_action_reset))
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
                        // Selected Box Info & Action
                        AnimatedVisibility(
                            visible = selectedBoxId != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color(0xFFFFC107), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "#$selectedBoxId",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.editor_selected_bubble),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        boxes = boxes.filter { it.id != selectedBoxId }
                                        selectedBoxId = null
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.editor_delete_sfx), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        // Primary Action Button: Proceed to Translate
                        Button(
                            onClick = {
                                val quads = boxes.map { it.toQuadrilateral() }
                                onConfirmDetections(quads)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.editor_continue_translate, boxes.size),
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
                // Interactive Canvas
                DetectionEditorCanvas(
                    imageBitmap = imageBitmap,
                    boxes = boxes,
                    selectedBoxId = selectedBoxId,
                    isAddBoxMode = isAddBoxMode,
                    onSelectBox = { id ->
                        selectedBoxId = id
                        if (id != null) isAddBoxMode = false
                    },
                    onUpdateBox = { updated ->
                        boxes = boxes.map { if (it.id == updated.id) updated else it }
                    },
                    onAddNewBox = { rect ->
                        val newBox = EditableBox.fromRect(nextBoxId++, rect)
                        boxes = boxes + newBox
                        selectedBoxId = newBox.id
                        isAddBoxMode = false
                    }
                )

                // Top Guidance Pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isAddBoxMode -> MaterialTheme.colorScheme.primaryContainer
                        selectedBoxId != null -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    },
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isAddBoxMode -> Icons.Default.TouchApp
                                selectedBoxId != null -> Icons.Default.OpenWith
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when {
                                isAddBoxMode -> MaterialTheme.colorScheme.onPrimaryContainer
                                selectedBoxId != null -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = when {
                                isAddBoxMode -> stringResource(R.string.editor_hint_add)
                                selectedBoxId != null -> stringResource(R.string.editor_hint_selected, selectedBoxId!!)
                                else -> stringResource(R.string.editor_hint_idle)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                isAddBoxMode -> MaterialTheme.colorScheme.onPrimaryContainer
                                selectedBoxId != null -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

package com.yuu18id.mangatranslator.ui.batch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yuu18id.mangatranslator.R
import com.yuu18id.mangatranslator.domain.model.BatchPageItem
import com.yuu18id.mangatranslator.domain.model.BatchPageStatus
import com.yuu18id.mangatranslator.domain.model.PipelineStage
import com.yuu18id.mangatranslator.ui.translate.LanguageEngineSelectorBar
import com.yuu18id.mangatranslator.ui.translate.editor.DetectionEditorDialog
import com.yuu18id.mangatranslator.ui.translate.editor.RenderEditorDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(
    viewModel: BatchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenChapterReader: (List<Long>) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var showRenameAlbumDialog by remember { mutableStateOf(false) }
    var albumNameInput by remember { mutableStateOf("") }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.batch_clear_dialog_title)) },
            text = { Text(stringResource(R.string.batch_clear_dialog_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showRenameAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showRenameAlbumDialog = false },
            icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.album_rename_edit)) },
            text = {
                OutlinedTextField(
                    value = albumNameInput,
                    onValueChange = { albumNameInput = it },
                    label = { Text(stringResource(R.string.album_name_label)) },
                    placeholder = { Text(stringResource(R.string.album_rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAlbumName(albumNameInput)
                        showRenameAlbumDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameAlbumDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.batch_title), fontWeight = FontWeight.Bold)
                            if (uiState.pages.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.batch_page_count, uiState.pages.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        if (uiState.pages.isNotEmpty() && !uiState.isProcessing) {
                            IconButton(onClick = viewModel::sortByFileName) {
                                Icon(Icons.Default.SortByAlpha, contentDescription = stringResource(R.string.batch_sort_filename))
                            }
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_clear_all))
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (!uiState.isProcessing && uiState.pages.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { multiImagePicker.launch("image/*") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = if (uiState.completedCount < uiState.pages.size) 76.dp else 8.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.batch_add_pages))
                    }
                }
            }
        ) { paddingValues ->
            if (uiState.pages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.batch_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.batch_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { multiImagePicker.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.batch_select_pages))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                ) {
                    // Shared Language & Translator Bar with Review Mode Toggle
                    LanguageEngineSelectorBar(
                        sourceLang = uiState.sourceLang,
                        targetLang = uiState.targetLang,
                        translatorType = uiState.translatorType,
                        isTranslating = uiState.isProcessing,
                        isReviewModeEnabled = uiState.isReviewModeEnabled,
                        showReviewToggle = true,
                        onTargetLangChanged = viewModel::setTargetLang,
                        onTranslatorTypeChanged = viewModel::setTranslatorType,
                        onToggleReviewMode = viewModel::toggleReviewMode
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Overall Batch Progress / Control Banner
                    if (uiState.isProcessing || uiState.completedCount > 0) {
                        BatchProgressBanner(
                            isProcessing = uiState.isProcessing,
                            total = uiState.pages.size,
                            completed = uiState.completedCount,
                            failed = uiState.failedCount,
                            progress = uiState.overallProgress,
                            albumName = uiState.currentBatchName,
                            onEditAlbumName = {
                                albumNameInput = uiState.currentBatchName
                                showRenameAlbumDialog = true
                            },
                            onCancel = viewModel::cancelBatchTranslation,
                            onReadChapter = {
                                val completedIds = uiState.pages.mapNotNull { it.historyId }
                                if (completedIds.isNotEmpty()) {
                                    onOpenChapterReader(completedIds)
                                }
                            },
                            onRetryFailed = viewModel::startBatchTranslation
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Page List with Order Management & Typeset Action
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(
                            items = uiState.pages,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            BatchPageCard(
                                item = item,
                                index = index,
                                totalCount = uiState.pages.size,
                                isProcessingActive = uiState.isProcessing,
                                onMoveUp = { viewModel.movePageUp(index) },
                                onMoveDown = { viewModel.movePageDown(index) },
                                onDelete = { viewModel.removePage(item.id) },
                                onEditTypeset = { viewModel.openRenderEditor(index) },
                                onRetry = { viewModel.startBatchTranslation(index) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(72.dp)) // Padding for FAB
                        }
                    }

                    // Bottom Action Button
                    if (!uiState.isProcessing && uiState.completedCount < uiState.pages.size) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Button(
                                onClick = viewModel::startBatchTranslation,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 14.dp)
                            ) {
                                Icon(Icons.Default.Translate, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.batch_start_btn, uiState.pages.size - uiState.completedCount),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Detection Review Dialog (Pops up when Review Mode is active during batch)
        if (uiState.isShowingDetectionEditor && uiState.reviewImageBitmap != null) {
            DetectionEditorDialog(
                imageBitmap = uiState.reviewImageBitmap!!,
                initialDetections = uiState.pendingDetections,
                onDismiss = viewModel::dismissDetectionEditor,
                onConfirmDetections = viewModel::confirmDetections
            )
        }

        // Render Typeset Editor Dialog (Pops up when user edits typeset of any completed page)
        if (uiState.isShowingRenderEditor && uiState.renderEditorInpaintedBitmap != null) {
            RenderEditorDialog(
                inpaintedBitmap = uiState.renderEditorInpaintedBitmap!!,
                initialBlocks = uiState.renderEditorBlocks,
                onDismiss = viewModel::dismissRenderEditor,
                onConfirmBlocks = viewModel::applyEditedRender
            )
        }
    }
}

@Composable
fun BatchProgressBanner(
    isProcessing: Boolean,
    total: Int,
    completed: Int,
    failed: Int,
    progress: Float,
    albumName: String,
    onEditAlbumName: () -> Unit,
    onCancel: () -> Unit,
    onReadChapter: () -> Unit,
    onRetryFailed: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isProcessing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isProcessing) stringResource(R.string.batch_status_in_progress) else if (failed > 0) stringResource(R.string.batch_status_error) else stringResource(R.string.batch_status_all_done),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$completed / $total",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )

            // Album Name Display & Editing Row (Available once batch has progress/completed)
            if (!isProcessing && completed > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.album_name_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = albumName.ifBlank { stringResource(R.string.album_rename_title) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = onEditAlbumName,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.album_rename_edit),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isProcessing) {
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    if (failed > 0) {
                        TextButton(onClick = onRetryFailed) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.batch_retry_failed, failed))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (completed > 0) {
                        Button(
                            onClick = onReadChapter,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.batch_read_chapter, completed))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchPageCard(
    item: BatchPageItem,
    index: Int,
    totalCount: Int,
    isProcessingActive: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onEditTypeset: () -> Unit,
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current
    val imageRequest = remember(item.uriString) {
        ImageRequest.Builder(context)
            .data(Uri.parse(item.uriString))
            .crossfade(false)
            .size(160, 220)
            .build()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (item.status) {
                BatchPageStatus.PROCESSING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                BatchPageStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant
                BatchPageStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page Number Badge
            Surface(
                shape = CircleShape,
                color = when (item.status) {
                    BatchPageStatus.COMPLETED -> Color(0xFF2E7D32)
                    BatchPageStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                    BatchPageStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (item.status == BatchPageStatus.COMPLETED) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    } else if (item.status == BatchPageStatus.PROCESSING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else if (item.status == BatchPageStatus.FAILED) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))

                val statusText = when (item.status) {
                    BatchPageStatus.IDLE, BatchPageStatus.QUEUED -> stringResource(R.string.batch_status_queued)
                    BatchPageStatus.PROCESSING -> when (item.currentStage) {
                        PipelineStage.DETECTION -> stringResource(R.string.translate_stage_detection)
                        PipelineStage.OCR, PipelineStage.TEXTLINE_MERGE -> stringResource(R.string.translate_stage_ocr)
                        PipelineStage.TRANSLATION -> stringResource(R.string.translate_stage_translation)
                        PipelineStage.MASK_REFINEMENT, PipelineStage.INPAINTING -> stringResource(R.string.translate_stage_inpainting)
                        PipelineStage.RENDERING -> stringResource(R.string.translate_stage_rendering)
                    }
                    BatchPageStatus.COMPLETED -> stringResource(R.string.batch_status_done)
                    BatchPageStatus.FAILED -> item.errorMessage ?: stringResource(R.string.batch_status_failed)
                    BatchPageStatus.CANCELLED -> stringResource(R.string.translate_cancelled)
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (item.status) {
                        BatchPageStatus.COMPLETED -> Color(0xFF2E7D32)
                        BatchPageStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                        BatchPageStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.status == BatchPageStatus.PROCESSING) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
            }

            // Actions: Edit Typeset for completed pages, Retry for failed, Reorder & Delete for pending pages
            if (!isProcessingActive && item.status != BatchPageStatus.PROCESSING) {
                if (item.status == BatchPageStatus.COMPLETED) {
                    FilledTonalButton(
                        onClick = onEditTypeset,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_typeset), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                } else if (item.status == BatchPageStatus.FAILED) {
                    FilledTonalButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_retry), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                } else {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.action_move_up), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.action_move_down), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

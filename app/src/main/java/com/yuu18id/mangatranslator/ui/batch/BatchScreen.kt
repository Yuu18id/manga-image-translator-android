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
import com.yuu18id.mangatranslator.ui.translate.DropdownSelector

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

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.gallery_dialog_clear_title)) },
            text = { Text("Are you sure you want to clear all pages in the current batch?") },
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
            if (!uiState.isProcessing) {
                FloatingActionButton(
                    onClick = { multiImagePicker.launch("image/*") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                        Text("Select Manga Pages")
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
                // Shared Language & Translator Bar
                com.yuu18id.mangatranslator.ui.translate.LanguageEngineSelectors(
                    sourceLang = uiState.sourceLang,
                    targetLang = uiState.targetLang,
                    translatorType = uiState.translatorType,
                    onSourceLangChanged = viewModel::setSourceLang,
                    onTargetLangChanged = viewModel::setTargetLang,
                    onTranslatorTypeChanged = viewModel::setTranslatorType
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

                // Page List with Order Management
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
                            onDelete = { viewModel.removePage(item.id) }
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
}

@Composable
fun BatchProgressBanner(
    isProcessing: Boolean,
    total: Int,
    completed: Int,
    failed: Int,
    progress: Float,
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
                    text = if (isProcessing) "Translating Chapter..." else if (failed > 0) "Batch Completed with Errors" else "Chapter Translation Completed!",
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
    onDelete: () -> Unit
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
                    BatchPageStatus.PROCESSING -> item.stageMessage.ifBlank { "Processing..." }
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

            // Reorder & Delete Actions
            if (!isProcessingActive && item.status != BatchPageStatus.PROCESSING) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

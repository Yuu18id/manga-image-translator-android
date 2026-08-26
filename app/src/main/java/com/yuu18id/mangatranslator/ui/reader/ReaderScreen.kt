package com.yuu18id.mangatranslator.ui.reader

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.filled.Translate
import com.yuu18id.mangatranslator.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    translationId: String? = null,
    viewModel: ReaderViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToTranslate: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current



    var showUi by remember { mutableStateOf(true) }
    var showOriginal by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.reader_dialog_delete_title)) },
            text = { Text(stringResource(R.string.reader_dialog_delete_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCurrentItem(onDeleted = onNavigateBack)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset zoom when page changes
    LaunchedEffect(uiState.currentPageIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    val currentImagePath = if (showOriginal) uiState.originalImagePath else uiState.translatedImagePath

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onTap = {
                        showUi = !showUi
                    }
                )
            }
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (!currentImagePath.isNullOrBlank()) {
            AsyncImage(
                model = currentImagePath,
                contentDescription = "Manga Page",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
            )
        }

        // Top UI
        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    if (uiState.totalPages > 1) {
                        Text(
                            text = stringResource(R.string.reader_page_indicator, uiState.currentPageIndex + 1, uiState.totalPages),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    } else {
                        Text("")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
                    }
                },
                actions = {
                    // Translate Ulang (Re-translate)
                    IconButton(onClick = {
                        val currentItem = uiState.pages.getOrNull(uiState.currentPageIndex)
                        if (currentItem != null) {
                            onNavigateToTranslate("history:${currentItem.id}")
                        } else {
                            val path = uiState.originalImagePath
                            if (path != null) {
                                val file = File(path)
                                if (file.exists()) {
                                    val uri = android.net.Uri.fromFile(file).toString()
                                    onNavigateToTranslate(uri)
                                }
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = "Translate Ulang",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = {
                        uiState.translatedImagePath?.let { path ->
                            val file = File(path)
                            if (file.exists()) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share)))
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share), tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.65f)
                )
            )
        }

        // Bottom UI with Page Navigation & Original Switch
        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomAppBar(
                containerColor = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Page Button
                    if (uiState.totalPages > 1) {
                        IconButton(
                            onClick = viewModel::previousPage,
                            enabled = uiState.currentPageIndex > 0
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.reader_prev_page),
                                tint = if (uiState.currentPageIndex > 0) Color.White else Color.Gray,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(32.dp))
                    }

                    // Original Toggle Switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.reader_show_translated),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (!showOriginal) MaterialTheme.colorScheme.primary else Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = showOriginal,
                            onCheckedChange = { showOriginal = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = Color.Gray,
                                uncheckedTrackColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.reader_show_original),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (showOriginal) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }

                    // Next Page Button
                    if (uiState.totalPages > 1) {
                        IconButton(
                            onClick = viewModel::nextPage,
                            enabled = uiState.currentPageIndex < uiState.totalPages - 1
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.reader_next_page),
                                tint = if (uiState.currentPageIndex < uiState.totalPages - 1) Color.White else Color.Gray,
                                modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = 180f }
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(32.dp))
                    }
                }
            }
        }
    }
}

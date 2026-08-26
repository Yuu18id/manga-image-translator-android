package com.yuu18id.mangatranslator.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.yuu18id.mangatranslator.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HistoryDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToReader: (String) -> Unit,
    onNavigateToTranslate: (String) -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null
) {
    val items by viewModel.galleryItems.collectAsStateWithLifecycle()
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode by remember { derivedStateOf { selectedKeys.isNotEmpty() } }

    var showClearAllDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Intercept back button when in selection mode
    BackHandler(enabled = isSelectionMode) {
        selectedKeys = emptySet()
    }

    // Clear All Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.gallery_dialog_clear_title)) },
            text = { Text(stringResource(R.string.gallery_dialog_clear_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAll()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Batch Delete Dialog
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.gallery_dialog_delete_title, selectedKeys.size)) },
            text = { Text(stringResource(R.string.gallery_dialog_delete_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        val idsToDelete = items.filter { it.key in selectedKeys }.flatMap { it.allIds }
                        viewModel.deleteSelectedItems(idsToDelete)
                        selectedKeys = emptySet()
                        showBatchDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Contextual Action Bar (Google Photos Style)
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.gallery_selected_count, selectedKeys.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedKeys = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedKeys = if (selectedKeys.size == items.size) {
                                emptySet()
                            } else {
                                items.map { it.key }.toSet()
                            }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { showBatchDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                // Standard Gallery Top Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.gallery_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (items.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.gallery_item_count, items.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        }
                    },
                    actions = {
                        if (items.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.gallery_menu_clear_all), color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showClearAllDialog = true
                                        }
                                    )
                                }
                            }
                        }

                        if (onNavigateToSettings != null) {
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.nav_settings)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.gallery_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.gallery_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(
                    items = items,
                    key = { it.key },
                    contentType = { if (it is GalleryUiItem.Album) "album_item" else "single_item" }
                ) { item ->
                    val isSelected by remember { derivedStateOf { selectedKeys.contains(item.key) } }

                    when (item) {
                        is GalleryUiItem.Single -> {
                            GallerySingleCard(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedKeys = if (isSelected) selectedKeys - item.key else selectedKeys + item.key
                                    } else {
                                        onNavigateToReader(item.item.id.toString())
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedKeys = setOf(item.key)
                                    }
                                },
                                onReTranslate = {
                                    onNavigateToTranslate("history:${item.item.id}")
                                }
                            )
                        }
                        is GalleryUiItem.Album -> {
                            GalleryAlbumCard(
                                album = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedKeys = if (isSelected) selectedKeys - item.key else selectedKeys + item.key
                                    } else {
                                        val idsArg = item.pageItems.map { it.id }.joinToString(",")
                                        onNavigateToReader(idsArg)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        selectedKeys = setOf(item.key)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GallerySingleCard(
    item: GalleryUiItem.Single,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onReTranslate: () -> Unit
) {
    val dateString = remember(item.timestamp) {
        HistoryDateFormat.format(Date(item.timestamp))
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.coverPath.isNotBlank()) {
                AsyncImage(
                    model = item.coverPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.gallery_no_image), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Top Overlay: Language Badge & Selection Checkmark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Language Pill
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${item.sourceLang.code} → ${item.targetLang.code}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                if (isSelectionMode) {
                    SelectionIndicator(isSelected = isSelected)
                }
            }

            // Bottom Bar: Re-translate Button & Date
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSelectionMode) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp),
                        onClick = onReTranslate
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = stringResource(R.string.gallery_retranslate_btn),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryAlbumCard(
    album: GalleryUiItem.Album,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateString = remember(album.timestamp) {
        HistoryDateFormat.format(Date(album.timestamp))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = if (isSelected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (album.coverPath.isNotBlank()) {
                AsyncImage(
                    model = album.coverPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.gallery_no_image), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Dark subtle scrim gradient at bottom for text contrast
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Top Overlay: Language Pill & Page Count Album Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Language Pill
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${album.sourceLang.code} → ${album.targetLang.code}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                if (isSelectionMode) {
                    SelectionIndicator(isSelected = isSelected)
                } else {
                    // Album Multi-Page Badge
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.gallery_album_badge, album.pageCount),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // Bottom Overlay: Album Title & Date
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.gallery_album_type),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    )
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionIndicator(isSelected: Boolean) {
    if (isSelected) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.gallery_selected_badge),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    } else {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.4f),
            border = BorderStroke(2.dp, Color.White),
            modifier = Modifier.size(24.dp)
        ) {}
    }
}

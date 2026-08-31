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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.LocalContext
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
    val exportingKeys by viewModel.exportingKeys.collectAsStateWithLifecycle()
    var selectedKeys by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode by remember { derivedStateOf { selectedKeys.isNotEmpty() } }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { event ->
            when (event) {
                is ExportEvent.Success -> {
                    val msg = if (event.count == 1) {
                        context.getString(R.string.gallery_save_success_single)
                    } else {
                        context.getString(R.string.gallery_save_success_multiple, event.count)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is ExportEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    var showClearAllDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var albumToRename by remember { mutableStateOf<GalleryUiItem.Album?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    // Intercept back button when in selection mode
    BackHandler(enabled = isSelectionMode) {
        selectedKeys = emptySet()
    }

    // Rename Album Dialog
    if (albumToRename != null) {
        AlertDialog(
            onDismissRequest = { albumToRename = null },
            icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.album_rename_edit)) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text(stringResource(R.string.album_name_label)) },
                    placeholder = { Text(stringResource(R.string.album_rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        albumToRename?.let { album ->
                            viewModel.renameAlbum(album.batchId, renameInputText)
                        }
                        albumToRename = null
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { albumToRename = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        val isAnyExporting = exportingKeys.isNotEmpty()
                        IconButton(
                            onClick = {
                                viewModel.saveSelectedItems(selectedKeys)
                                selectedKeys = emptySet()
                            },
                            enabled = !isAnyExporting
                        ) {
                            if (isAnyExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = stringResource(R.string.gallery_save_selected),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
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
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.gallery_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
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
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
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
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = items,
                    key = { it.key }
                ) { item ->
                    val isSelected by remember { derivedStateOf { selectedKeys.contains(item.key) } }
                    val isItemExporting by remember(exportingKeys, item.key) {
                        derivedStateOf { exportingKeys.contains(item.key) }
                    }

                    when (item) {
                        is GalleryUiItem.Single -> {
                            GallerySingleCard(
                                item = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                isExporting = isItemExporting,
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
                                },
                                onSave = {
                                    viewModel.saveSingleItem(item.item)
                                }
                            )
                        }
                        is GalleryUiItem.Album -> {
                            GalleryAlbumCard(
                                album = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                isExporting = isItemExporting,
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
                                },
                                onRename = {
                                    albumToRename = item
                                    renameInputText = item.title
                                },
                                onSave = {
                                    viewModel.saveAlbum(item)
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
    isExporting: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onReTranslate: () -> Unit,
    onSave: () -> Unit = {}
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

            // Bottom Bar: Actions & Date
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSelectionMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp),
                            onClick = { if (!isExporting) onSave() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = stringResource(R.string.action_save_to_gallery),
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
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
    isExporting: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit = {},
    onSave: () -> Unit = {}
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

            // Bottom Overlay: Album Title, Actions & Date
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isSelectionMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp),
                                onClick = { if (!isExporting) onSave() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isExporting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = stringResource(R.string.gallery_save_album),
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp),
                                onClick = onRename
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.album_rename_edit),
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
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

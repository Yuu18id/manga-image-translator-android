package com.yuu18id.mangatranslator.ui.translate

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuu18id.mangatranslator.R
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.PipelineStage
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    imageUri: String? = null,
    viewModel: TranslateViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(imageUri) {
        if (!imageUri.isNullOrBlank()) {
            viewModel.setImageUri(android.net.Uri.parse(imageUri))
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    fun shareTranslatedImage() {
        coroutineScope.launch(Dispatchers.IO) {
            val bitmap = viewModel.getTranslatedBitmap() ?: return@launch
            val file = File(context.cacheDir, "share_translated_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share)))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.translatedImage != null) {
                                stringResource(R.string.translate_edit_title)
                            } else {
                                stringResource(R.string.translate_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        if (uiState.translatedImage != null && !progressState.isTranslating) {
                            IconButton(onClick = { shareTranslatedImage() }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.action_share)
                                )
                            }
                            IconButton(onClick = viewModel::startTranslation) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.translate_full_rescan)
                                )
                            }
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Interactive Preview Area with Toggle Original & Zoom
                TranslateImageViewer(
                    originalImage = uiState.originalImage,
                    translatedImage = uiState.translatedImage,
                    showOriginal = uiState.showOriginal,
                    onToggleOriginal = viewModel::toggleShowOriginal,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // 2. Language & Engine Selection Bar (Clean Symmetrical Card + Review Detection Toggle when not translated)
                LanguageEngineSelectorBar(
                    sourceLang = uiState.sourceLang,
                    targetLang = uiState.targetLang,
                    translatorType = uiState.translatorType,
                    isTranslating = progressState.isTranslating,
                    isReviewModeEnabled = uiState.isReviewModeEnabled,
                    showReviewToggle = uiState.translatedImage == null,
                    onTargetLangChanged = viewModel::setTargetLang,
                    onTranslatorTypeChanged = viewModel::setTranslatorType,
                    onToggleReviewMode = viewModel::toggleReviewMode
                )

                // 3. Progress Card
                if (progressState.isTranslating) {
                    TranslationProgressCard(
                        stage = progressState.currentStage,
                        progress = progressState.progress,
                        message = progressState.stageMessage
                    )
                }

                // 4. Primary Action Controls
                TranslateActionButtons(
                    isTranslating = progressState.isTranslating,
                    isReviewModeEnabled = uiState.isReviewModeEnabled,
                    hasOriginal = uiState.originalImage != null,
                    hasTranslated = uiState.translatedImage != null,
                    canFastRetranslate = uiState.canFastRetranslate,
                    onCancel = viewModel::cancelTranslation,
                    onTranslate = viewModel::startTranslation,
                    onFastRetranslate = { viewModel.retranslateTextOnly() },
                    onOpenRenderEditor = viewModel::openRenderEditor
                )
            }
        }

        // Detection Review and Manipulation Editor (Fullscreen overlay in the Activity window)
        if (uiState.isShowingDetectionEditor && uiState.originalImage != null) {
            com.yuu18id.mangatranslator.ui.translate.editor.DetectionEditorDialog(
                imageBitmap = uiState.originalImage!!,
                initialDetections = uiState.pendingDetections,
                onDismiss = viewModel::dismissDetectionEditor,
                onConfirmDetections = { curatedBoxes ->
                    viewModel.applyEditedDetectionsAndTranslate(curatedBoxes)
                }
            )
        }

        // Render / Typesetting Review and Manipulation Editor (Fullscreen overlay in the Activity window)
        if (uiState.isShowingRenderEditor && uiState.inpaintedImage != null) {
            com.yuu18id.mangatranslator.ui.translate.editor.RenderEditorDialog(
                inpaintedBitmap = uiState.inpaintedImage!!,
                initialBlocks = uiState.currentTextBlocks,
                onDismiss = viewModel::dismissRenderEditor,
                onConfirmBlocks = { updatedBlocks ->
                    viewModel.applyEditedRender(updatedBlocks)
                }
            )
        }
    }
}

@Composable
fun TranslateImageViewer(
    originalImage: ImageBitmap?,
    translatedImage: ImageBitmap?,
    showOriginal: Boolean,
    onToggleOriginal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale == 1f) {
                        offset = Offset.Zero
                    } else {
                        offset += pan
                    }
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val imageBitmap = if (showOriginal) originalImage else (translatedImage ?: originalImage)
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Translation Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.translate_no_image),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Comparison Pill (when translation result is ready)
            if (translatedImage != null && originalImage != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showOriginal,
                            onClick = onToggleOriginal,
                            label = { Text(stringResource(R.string.translate_toggle_original), style = MaterialTheme.typography.labelSmall) },
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        FilterChip(
                            selected = !showOriginal,
                            onClick = onToggleOriginal,
                            label = { Text(stringResource(R.string.translate_toggle_translated), style = MaterialTheme.typography.labelSmall) },
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageEngineSelectorBar(
    sourceLang: Language?,
    targetLang: Language,
    translatorType: TranslatorType,
    isTranslating: Boolean,
    isReviewModeEnabled: Boolean = false,
    showReviewToggle: Boolean = true,
    onTargetLangChanged: (Language) -> Unit,
    onTranslatorTypeChanged: (TranslatorType) -> Unit,
    onToggleReviewMode: () -> Unit = {}
) {
    var showTargetLangSheet by remember { mutableStateOf(false) }
    var showEngineSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Language & Engine Selection Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Target Language Picker Pill
                Surface(
                    onClick = { if (!isTranslating) showTargetLangSheet = true },
                    enabled = !isTranslating,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.translate_target_lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = targetLang.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Engine Picker Pill
                Surface(
                    onClick = { if (!isTranslating) showEngineSheet = true },
                    enabled = !isTranslating,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.translate_engine),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = translatorType.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Review Detection Toggle (shown when translating a new image)
        if (showReviewToggle) {
            Surface(
                onClick = onToggleReviewMode,
                enabled = !isTranslating,
                shape = RoundedCornerShape(12.dp),
                color = if (isReviewModeEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    1.dp,
                    if (isReviewModeEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isReviewModeEnabled) Icons.Default.HighlightAlt else Icons.Default.CropFree,
                            contentDescription = null,
                            tint = if (isReviewModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.translate_review_mode),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isReviewModeEnabled) FontWeight.Bold else FontWeight.Medium,
                            color = if (isReviewModeEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(
                        checked = isReviewModeEnabled,
                        onCheckedChange = { onToggleReviewMode() },
                        enabled = !isTranslating,
                        modifier = Modifier.scale(0.85f)
                    )
                }
            }
        }
    }

    // Modal Bottom Sheet: Target Language Selection
    if (showTargetLangSheet) {
        LanguageSelectionSheet(
            title = stringResource(R.string.translate_select_target),
            languages = Language.values().toList(),
            selectedLanguage = targetLang,
            onSelect = {
                onTargetLangChanged(it)
                showTargetLangSheet = false
            },
            onDismiss = { showTargetLangSheet = false }
        )
    }

    // Modal Bottom Sheet: Translation Engine Selection
    if (showEngineSheet) {
        EngineSelectionSheet(
            selectedEngine = translatorType,
            engines = TranslatorType.values().toList(),
            onSelect = {
                onTranslatorTypeChanged(it)
                showEngineSheet = false
            },
            onDismiss = { showEngineSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionSheet(
    title: String,
    languages: List<Language>,
    selectedLanguage: Language,
    onSelect: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery, languages) {
        if (searchQuery.isBlank()) languages
        else languages.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.nativeName.contains(searchQuery, ignoreCase = true) ||
            it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.translate_search_language)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredLanguages) { lang ->
                    val isSelected = lang == selectedLanguage
                    Surface(
                        onClick = { onSelect(lang) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSelectionSheet(
    selectedEngine: TranslatorType,
    engines: List<TranslatorType>,
    onSelect: (TranslatorType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.translate_select_engine),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                engines.forEach { engine ->
                    val isSelected = engine == selectedEngine
                    Surface(
                        onClick = { onSelect(engine) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = engine.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (engine.requiresApiKey) stringResource(R.string.translate_engine_requires_key) else stringResource(R.string.translate_engine_free),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TranslationProgressCard(
    stage: PipelineStage,
    progress: Float,
    message: String
) {
    val stageTitle = when (stage) {
        PipelineStage.DETECTION -> stringResource(R.string.translate_stage_detection)
        PipelineStage.OCR, PipelineStage.TEXTLINE_MERGE -> stringResource(R.string.translate_stage_ocr)
        PipelineStage.TRANSLATION -> stringResource(R.string.translate_stage_translation)
        PipelineStage.MASK_REFINEMENT, PipelineStage.INPAINTING -> stringResource(R.string.translate_stage_inpainting)
        PipelineStage.RENDERING -> stringResource(R.string.translate_stage_rendering)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stageTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
fun TranslateActionButtons(
    isTranslating: Boolean,
    isReviewModeEnabled: Boolean,
    hasOriginal: Boolean,
    hasTranslated: Boolean,
    canFastRetranslate: Boolean,
    onCancel: () -> Unit,
    onTranslate: () -> Unit,
    onFastRetranslate: () -> Unit,
    onOpenRenderEditor: () -> Unit
) {
    if (isTranslating) {
        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
        }
    } else {
        if (!hasTranslated) {
            Button(
                onClick = onTranslate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = hasOriginal
            ) {
                Icon(
                    imageVector = if (isReviewModeEnabled) Icons.Default.HighlightAlt else Icons.Default.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isReviewModeEnabled) stringResource(R.string.translate_action_review_btn) else stringResource(R.string.translate_action_btn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Post-translation Action Bar: Re-translate Text (Primary) & Edit Typeset (Secondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary: Re-translate Text with current engine/language
                Button(
                    onClick = {
                        if (canFastRetranslate) {
                            onFastRetranslate()
                        } else {
                            onTranslate()
                        }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.translate_retranslate_text),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Secondary: Edit Typeset (Balloons, font size, styling, alignment)
                FilledTonalButton(
                    onClick = onOpenRenderEditor,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.translate_edit_typeset),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

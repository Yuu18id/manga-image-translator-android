package com.yuu18id.mangatranslator.ui.translate

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.yuu18id.mangatranslator.R
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.PipelineStage
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    imageUri: String? = null,
    viewModel: TranslateViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenInReader: ((Long) -> Unit)? = null
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.translate_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (uiState.translatedImage != null) {
                        IconButton(onClick = viewModel::toggleShowOriginal) {
                            Icon(
                                imageVector = if (uiState.showOriginal) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = stringResource(R.string.translate_toggle_original)
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Image View (Isolated from progress updates)
            TranslateImageViewer(
                originalImage = uiState.originalImage,
                translatedImage = uiState.translatedImage,
                showOriginal = uiState.showOriginal,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selectors (Remembered lists to prevent allocations)
            LanguageEngineSelectors(
                sourceLang = uiState.sourceLang,
                targetLang = uiState.targetLang,
                translatorType = uiState.translatorType,
                onSourceLangChanged = viewModel::setSourceLang,
                onTargetLangChanged = viewModel::setTargetLang,
                onTranslatorTypeChanged = viewModel::setTranslatorType
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Card (Only this recomposes during active translation progress updates)
            if (progressState.isTranslating) {
                TranslationProgressCard(
                    stage = progressState.currentStage,
                    progress = progressState.progress,
                    message = progressState.stageMessage
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Action Buttons
            TranslateActionButtons(
                isTranslating = progressState.isTranslating,
                hasOriginal = uiState.originalImage != null,
                hasTranslated = uiState.translatedImage != null,
                savedHistoryId = uiState.savedHistoryId,
                onCancel = viewModel::cancelTranslation,
                onTranslate = viewModel::startTranslation,
                onOpenReader = { uiState.savedHistoryId?.let { onOpenInReader?.invoke(it) } },
                onShare = {
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
            )
        }
    }
}

@Composable
fun TranslateImageViewer(
    originalImage: ImageBitmap?,
    translatedImage: ImageBitmap?,
    showOriginal: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val imageBitmap = if (showOriginal) originalImage else (translatedImage ?: originalImage)
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Translation Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(stringResource(R.string.translate_no_image), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LanguageEngineSelectors(
    sourceLang: Language?,
    targetLang: Language,
    translatorType: TranslatorType,
    onSourceLangChanged: (Language?) -> Unit,
    onTargetLangChanged: (Language) -> Unit,
    onTranslatorTypeChanged: (TranslatorType) -> Unit
) {
    val sourceOptions = remember { listOf(Language.JPN) }
    val targetOptions = remember { Language.values().toList() }
    val translatorOptions = remember { TranslatorType.values().toList() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        DropdownSelector(
            label = stringResource(R.string.translate_source_lang),
            options = sourceOptions,
            selectedOption = sourceLang ?: Language.JPN,
            onOptionSelected = onSourceLangChanged,
            optionLabel = { it?.displayName ?: Language.JPN.displayName }
        )
        DropdownSelector(
            label = stringResource(R.string.translate_target_lang),
            options = targetOptions,
            selectedOption = targetLang,
            onOptionSelected = onTargetLangChanged,
            optionLabel = { it.displayName }
        )
        DropdownSelector(
            label = stringResource(R.string.translate_engine),
            options = translatorOptions,
            selectedOption = translatorType,
            onOptionSelected = onTranslatorTypeChanged,
            optionLabel = { it.displayName }
        )
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stageTitle, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun TranslateActionButtons(
    isTranslating: Boolean,
    hasOriginal: Boolean,
    hasTranslated: Boolean,
    savedHistoryId: Long?,
    onCancel: () -> Unit,
    onTranslate: () -> Unit,
    onOpenReader: () -> Unit,
    onShare: () -> Unit
) {
    if (isTranslating) {
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    } else {
        if (!hasTranslated) {
            Button(
                onClick = onTranslate,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasOriginal
            ) {
                Text(stringResource(R.string.translate_action_btn))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onOpenReader,
                    enabled = savedHistoryId != null
                ) {
                    Text(stringResource(R.string.translate_open_in_reader))
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                }
                Button(onClick = onTranslate) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    label: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.width(110.dp)
    ) {
        OutlinedTextField(
            value = optionLabel(selectedOption),
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(optionLabel(selectionOption)) },
                    onClick = {
                        onOptionSelected(selectionOption)
                        expanded = false
                    }
                )
            }
        }
    }
}

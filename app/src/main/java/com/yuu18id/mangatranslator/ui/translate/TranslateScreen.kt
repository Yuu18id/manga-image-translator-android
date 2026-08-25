package com.yuu18id.mangatranslator.ui.translate

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
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
    val uiState by viewModel.uiState.collectAsState()
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
                    if (uiState.translatedBitmap != null) {
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
            // Main Image View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(targetState = uiState.showOriginal, label = "image_crossfade") { showOrig ->
                    val bitmap = if (showOrig) uiState.originalBitmap else (uiState.translatedBitmap ?: uiState.originalBitmap)
                    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
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

            Spacer(modifier = Modifier.height(16.dp))

            // Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val autoText = stringResource(R.string.translate_auto_detect)
                DropdownSelector(
                    label = stringResource(R.string.translate_source_lang),
                    options = listOf(null) + Language.values().toList(),
                    selectedOption = uiState.sourceLang,
                    onOptionSelected = { viewModel.setSourceLang(it) },
                    optionLabel = { it?.displayName ?: autoText }
                )
                DropdownSelector(
                    label = stringResource(R.string.translate_target_lang),
                    options = Language.values().toList(),
                    selectedOption = uiState.targetLang,
                    onOptionSelected = { viewModel.setTargetLang(it) },
                    optionLabel = { it.displayName }
                )
                DropdownSelector(
                    label = stringResource(R.string.translate_engine),
                    options = TranslatorType.values().toList(),
                    selectedOption = uiState.translatorType,
                    onOptionSelected = { viewModel.setTranslatorType(it) },
                    optionLabel = { it.displayName }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Card
            if (uiState.isTranslating) {
                val stageTitle = when (uiState.currentStage) {
                    PipelineStage.DETECTION -> stringResource(R.string.translate_stage_detection)
                    PipelineStage.OCR -> stringResource(R.string.translate_stage_ocr)
                    PipelineStage.TEXTLINE_MERGE -> stringResource(R.string.translate_stage_ocr)
                    PipelineStage.TRANSLATION -> stringResource(R.string.translate_stage_translation)
                    PipelineStage.MASK_REFINEMENT -> stringResource(R.string.translate_stage_inpainting)
                    PipelineStage.INPAINTING -> stringResource(R.string.translate_stage_inpainting)
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
                            progress = { uiState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.stageMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = uiState.stageMessage, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Action Buttons
            if (uiState.isTranslating) {
                Button(
                    onClick = viewModel::cancelTranslation,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            } else {
                if (uiState.translatedBitmap == null) {
                    Button(
                        onClick = viewModel::startTranslation,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.originalBitmap != null
                    ) {
                        Text(stringResource(R.string.translate_action_btn))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { uiState.savedHistoryId?.let { onOpenInReader?.invoke(it) } },
                            enabled = uiState.savedHistoryId != null
                        ) {
                            Text(stringResource(R.string.translate_open_in_reader))
                        }
                        IconButton(onClick = {
                            val bitmap = uiState.translatedBitmap ?: return@IconButton
                            val file = File(context.cacheDir, "share_translated_${System.currentTimeMillis()}.png")
                            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share)))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                        Button(onClick = viewModel::startTranslation) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
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

package com.yuu18id.mangatranslator.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TranslatorType

import androidx.compose.ui.res.stringResource
import com.yuu18id.mangatranslator.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_clear_title)) },
            text = { Text(stringResource(R.string.settings_dialog_clear_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                }) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LanguageEngineSection(viewModel, uiState)
            ApiKeysSection(viewModel, uiState)
            ModelQualitySection(viewModel, uiState)
            StorageSection { showClearHistoryDialog = true }
            AboutSection()
        }
    }
}

@Composable
fun LanguageEngineSection(viewModel: SettingsViewModel, uiState: SettingsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_language_engine), style = MaterialTheme.typography.titleMedium)
            
            val autoText = stringResource(R.string.translate_auto_detect)
            SettingsDropdown(
                label = stringResource(R.string.settings_default_source_lang),
                options = listOf(null) + Language.values().toList(),
                selectedOption = uiState.config.translator.sourceLang,
                onOptionSelected = { viewModel.updateSourceLanguage(it) },
                optionLabel = { it?.displayName ?: autoText }
            )
            
            SettingsDropdown(
                label = stringResource(R.string.settings_default_target_lang),
                options = Language.values().toList(),
                selectedOption = uiState.config.translator.targetLang,
                onOptionSelected = { viewModel.updateTargetLanguage(it) },
                optionLabel = { it.displayName }
            )

            SettingsDropdown(
                label = stringResource(R.string.settings_default_translator),
                options = TranslatorType.values().toList(),
                selectedOption = uiState.config.translator.translatorType,
                onOptionSelected = { viewModel.updateTranslator(it) },
                optionLabel = { it.displayName }
            )
        }
    }
}

@Composable
fun ApiKeysSection(viewModel: SettingsViewModel, uiState: SettingsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_api_keys), style = MaterialTheme.typography.titleMedium)
            
            ApiKeyField("OpenAI Key", uiState.openAiKey) { viewModel.saveApiKey(TranslatorType.OPENAI, it) }
            ApiKeyField("DeepL Key", uiState.deepLKey) { viewModel.saveApiKey(TranslatorType.DEEPL, it) }
            ApiKeyField("Gemini Key", uiState.geminiKey) { viewModel.saveApiKey(TranslatorType.GEMINI, it) }
            ApiKeyField("DeepSeek Key", uiState.deepSeekKey) { viewModel.saveApiKey(TranslatorType.DEEPSEEK, it) }
            ApiKeyField("Groq Key", uiState.groqKey) { viewModel.saveApiKey(TranslatorType.GROQ, it) }
            ApiKeyField("Papago Key", uiState.papagoKey) { viewModel.saveApiKey(TranslatorType.PAPAGO, it) }
        }
    }
}

@Composable
fun ApiKeyField(label: String, value: String, onValueChange: (String) -> Unit) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
            }
        }
    )
}

@Composable
fun ModelQualitySection(viewModel: SettingsViewModel, uiState: SettingsUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_model_quality), style = MaterialTheme.typography.titleMedium)
            
            SettingsDropdown(
                label = stringResource(R.string.settings_detection_res),
                options = listOf(1024, 1536, 2048),
                selectedOption = uiState.config.detector.detectionSize,
                onOptionSelected = { viewModel.updateDetectionResolution(it) },
                optionLabel = { "$it" }
            )

            SettingsDropdown(
                label = stringResource(R.string.settings_inpainting_res),
                options = listOf(512, 1024, 2048),
                selectedOption = uiState.config.inpainter.inpaintingSize,
                onOptionSelected = { viewModel.updateInpaintingResolution(it) },
                optionLabel = { "$it" }
            )

            Column {
                Text(stringResource(R.string.settings_font_size_offset, uiState.config.render.fontSizeOffset), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = uiState.config.render.fontSizeOffset.toFloat(),
                    onValueChange = { viewModel.updateFontSizeOffset(it.toInt()) },
                    valueRange = -5f..5f,
                    steps = 9
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsDropdown(
    label: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = optionLabel(selectedOption),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun StorageSection(onClearHistory: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onClearHistory, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.settings_clear_history_btn))
            }
        }
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.settings_developed_by, "yuu18id"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            FilledTonalButton(onClick = { 
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yuu18id/manga-image-translator")))
            }) {
                Text(stringResource(R.string.settings_github))
            }
        }
    }
}

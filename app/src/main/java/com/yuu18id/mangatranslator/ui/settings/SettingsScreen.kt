package com.yuu18id.mangatranslator.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuu18id.mangatranslator.R
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TranslatorType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.settings_dialog_clear_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_dialog_clear_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_clear_all))
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
                title = { 
                    Text(
                        stringResource(R.string.settings_title), 
                        fontWeight = FontWeight.Bold 
                    ) 
                },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Language & Translation Defaults
            SettingsSection(
                title = stringResource(R.string.settings_language_engine),
                icon = Icons.Default.Translate
            ) {
                SettingsDropdownItem(
                    label = stringResource(R.string.settings_default_target_lang),
                    icon = Icons.Default.Check,
                    options = remember { Language.values().toList() },
                    selectedOption = uiState.config.translator.targetLang,
                    onOptionSelected = viewModel::updateTargetLanguage,
                    optionLabel = { it.displayName }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsDropdownItem(
                    label = stringResource(R.string.settings_default_translator),
                    icon = Icons.Default.SmartToy,
                    options = remember { TranslatorType.values().toList() },
                    selectedOption = uiState.config.translator.translatorType,
                    onOptionSelected = viewModel::updateTranslator,
                    optionLabel = { it.displayName }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                SettingsDropdownItem(
                    label = stringResource(R.string.settings_default_ocr_engine),
                    icon = Icons.Default.TextFields,
                    options = remember { com.yuu18id.mangatranslator.domain.model.OcrType.values().toList() },
                    selectedOption = uiState.config.ocr.ocrType,
                    onOptionSelected = viewModel::updateOcrType,
                    optionLabel = { context.getString(it.titleResId) }
                )
            }

            // 2. AI Translation Engines & API Keys
            ApiKeysSettingsSection(viewModel = viewModel, uiState = uiState)

            // 3. Storage Management
            SettingsSection(
                title = stringResource(R.string.settings_storage),
                icon = Icons.Default.Storage
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearHistoryDialog = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_clear_history_btn),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.settings_storage_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. About & App Info
            AboutAppSection()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun ApiKeysSettingsSection(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    var expandedProvider by remember { mutableStateOf<TranslatorType?>(uiState.config.translator.translatorType) }

    SettingsSection(
        title = stringResource(R.string.settings_api_keys),
        icon = Icons.Default.Key
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_api_keys_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            // Providers list
            val providers = remember {
                listOf(
                    Triple(TranslatorType.GROQ, "Groq (Ultra Fast - Free Tier)", "https://console.groq.com/keys"),
                    Triple(TranslatorType.GEMINI, "Google Gemini", "https://aistudio.google.com/app/apikey"),
                    Triple(TranslatorType.OPENROUTER, "OpenRouter (All LLMs)", "https://openrouter.ai/keys"),
                    Triple(TranslatorType.DEEPSEEK, "DeepSeek", "https://platform.deepseek.com/api_keys"),
                    Triple(TranslatorType.OPENAI, "OpenAI (GPT-4o)", "https://platform.openai.com/api-keys"),
                    Triple(TranslatorType.DEEPL, "DeepL", "https://www.deepl.com/pro-api"),
                    Triple(TranslatorType.PAPAGO, "Naver Papago", "https://developers.naver.com/main/")
                )
            }

            providers.forEach { (type, name, portalUrl) ->
                val currentKey = when (type) {
                    TranslatorType.GROQ -> uiState.groqKey
                    TranslatorType.GEMINI -> uiState.geminiKey
                    TranslatorType.OPENROUTER -> uiState.openRouterKey
                    TranslatorType.DEEPSEEK -> uiState.deepSeekKey
                    TranslatorType.OPENAI -> uiState.openAiKey
                    TranslatorType.DEEPL -> uiState.deepLKey
                    TranslatorType.PAPAGO -> uiState.papagoKey
                    else -> ""
                }
                val isCurrentDefault = uiState.config.translator.translatorType == type
                val isExpanded = expandedProvider == type

                ProviderApiKeyCard(
                    providerType = type,
                    title = name,
                    portalUrl = portalUrl,
                    currentKey = currentKey,
                    isDefault = isCurrentDefault,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedProvider = if (isExpanded) null else type
                    },
                    onKeySaved = { viewModel.saveApiKey(type, it) },
                    extraContent = if (type == TranslatorType.OPENROUTER) {
                        {
                            OpenRouterModelSelector(
                                currentModel = uiState.openRouterModel,
                                onModelSelected = viewModel::updateOpenRouterModel
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
fun ProviderApiKeyCard(
    providerType: TranslatorType,
    title: String,
    portalUrl: String,
    currentKey: String,
    isDefault: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onKeySaved: (String) -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val hasKey = currentKey.isNotBlank()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDefault) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isDefault) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Status dot
                    Surface(
                        shape = CircleShape,
                        color = if (hasKey) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(10.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isDefault) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_key_active),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (hasKey) stringResource(R.string.settings_key_configured) else stringResource(R.string.settings_key_none),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasKey) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.settings_get_key),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) stringResource(R.string.action_collapse) else stringResource(R.string.action_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ApiKeyInputField(
                        label = "$title API Key",
                        value = currentKey,
                        onValueChange = onKeySaved
                    )

                    extraContent?.invoke()
                }
            }
        }
    }
}

@Composable
fun OpenRouterModelSelector(
    currentModel: String,
    onModelSelected: (String) -> Unit
) {
    var localModel by remember(currentModel) { mutableStateOf(currentModel) }
    val presets = remember {
        listOf(
            "google/gemini-2.0-flash-001",
            "deepseek/deepseek-chat",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-sonnet",
            "meta-llama/llama-3.3-70b-instruct"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = localModel,
            onValueChange = { localModel = it },
            label = { Text("OpenRouter Model ID") },
            placeholder = { Text("e.g. google/gemini-2.0-flash-001") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && localModel != currentModel) {
                        onModelSelected(localModel)
                    }
                }
        )

        Text(
            text = stringResource(R.string.settings_preset_models),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(presets) { preset ->
                val isSelected = currentModel == preset
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        localModel = preset
                        onModelSelected(preset)
                    },
                    label = {
                        Text(
                            text = preset.substringAfter("/"),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun ApiKeyInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var localValue by remember(value) { mutableStateOf(value) }

    OutlinedTextField(
        value = localValue,
        onValueChange = { localValue = it },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.settings_paste_key_hint)) },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (!focusState.isFocused && localValue != value) {
                    onValueChange(localValue.trim())
                }
            },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (localValue.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            localValue = ""
                            onValueChange("")
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.settings_clear_key), modifier = Modifier.size(18.dp))
                    }
                }
                val icon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = icon, contentDescription = if (passwordVisible) stringResource(R.string.settings_hide_key) else stringResource(R.string.settings_show_key))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsDropdownItem(
    label: String,
    icon: ImageVector,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = optionLabel(selectedOption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
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
fun AboutAppSection() {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = stringResource(R.string.settings_version),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            Text(
                text = stringResource(R.string.settings_developed_by, "yuu18id"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yuu18id/manga-image-translator")))
                    }
                },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_github))
            }
        }
    }
}

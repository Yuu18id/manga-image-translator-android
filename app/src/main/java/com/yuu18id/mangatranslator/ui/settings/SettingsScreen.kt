package com.yuu18id.mangatranslator.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

            // 2. AI Translation Engines & Dynamic API Models
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

            // 4. About & App Info
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
                    Triple(TranslatorType.CLAUDE, "Anthropic Claude", "https://console.anthropic.com/settings/keys"),
                    Triple(TranslatorType.DEEPSEEK, "DeepSeek", "https://platform.deepseek.com/api_keys"),
                    Triple(TranslatorType.GLM, "Zhipu AI (GLM)", "https://open.bigmodel.cn/usercenter/apikeys"),
                    Triple(TranslatorType.OPENAI, "OpenAI (GPT-4o)", "https://platform.openai.com/api-keys"),
                    Triple(TranslatorType.CUSTOM, "Custom OpenAI-Compatible (Ollama, LM Studio)", ""),
                    Triple(TranslatorType.DEEPL, "DeepL", "https://www.deepl.com/pro-api"),
                    Triple(TranslatorType.PAPAGO, "Naver Papago", "https://developers.naver.com/main/")
                )
            }

            providers.forEach { (type, name, portalUrl) ->
                val currentKey = uiState.apiKeys[type] ?: ""
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
                    extraContent = if (type.isLlm) {
                        {
                            if (type == TranslatorType.CUSTOM) {
                                var localUrl by remember(uiState.customBaseUrl) { mutableStateOf(uiState.customBaseUrl) }
                                OutlinedTextField(
                                    value = localUrl,
                                    onValueChange = { localUrl = it },
                                    label = { Text(stringResource(R.string.settings_custom_base_url)) },
                                    placeholder = { Text(stringResource(R.string.settings_custom_base_url_hint)) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .onFocusChanged { focus ->
                                            if (!focus.isFocused && localUrl != uiState.customBaseUrl) {
                                                viewModel.saveCustomBaseUrl(localUrl)
                                            }
                                        }
                                )
                            }

                            val activeModel = uiState.selectedModels[type] ?: type.defaultModel
                            val cachedList = uiState.cachedModels[type] ?: emptyList()
                            val isFetching = uiState.isFetchingModels[type] == true
                            val fetchErr = uiState.fetchError[type]

                            DynamicModelSelector(
                                providerType = type,
                                currentModel = activeModel,
                                availableModels = cachedList,
                                isFetching = isFetching,
                                fetchError = fetchErr,
                                onModelSelected = { modelId -> viewModel.selectModel(type, modelId) },
                                onFetchModels = { viewModel.fetchModels(type) }
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
    val hasKey = currentKey.isNotBlank() || providerType == TranslatorType.CUSTOM

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
                    if (portalUrl.isNotBlank()) {
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
                        label = if (providerType == TranslatorType.CUSTOM) "API Key (Optional for local Ollama)" else "$title API Key",
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
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(if (passwordVisible) R.string.settings_hide_key else R.string.settings_show_key),
                        modifier = Modifier.size(18.dp)
                    )
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
                .menuAnchor()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = optionLabel(selectedOption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
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
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun AboutAppSection() {
    SettingsSection(
        title = stringResource(R.string.settings_about),
        icon = Icons.Default.Info
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.settings_version, com.yuu18id.mangatranslator.BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_developed_by, "Yuu18id"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.yuu18id.mangatranslator.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuu18id.mangatranslator.R
import com.yuu18id.mangatranslator.data.translation.model.AiModelInfo
import com.yuu18id.mangatranslator.domain.model.TranslatorType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicModelSelector(
    providerType: TranslatorType,
    currentModel: String,
    availableModels: List<AiModelInfo>,
    isFetching: Boolean,
    fetchError: String?,
    onModelSelected: (String) -> Unit,
    onFetchModels: () -> Unit
) {
    var showModelPickerDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var manualModelInput by remember(currentModel) { mutableStateOf(currentModel) }
    var isManualInputExpanded by remember { mutableStateOf(false) }

    val effectiveCurrentModel = currentModel.ifBlank { providerType.defaultModel }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Model Selection Header & Active Model Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_active_model_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = effectiveCurrentModel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Fetch / Refresh Button with Loading Indicator
                    FilledTonalButton(
                        onClick = onFetchModels,
                        enabled = !isFetching,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_fetching_models), style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.settings_fetch_models_btn), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Choose Model Dropdown trigger
                OutlinedButton(
                    onClick = { showModelPickerDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_choose_from_models, availableModels.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }

                // Error / Info feedback if fetch failed or key missing
                if (!fetchError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = fetchError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Quick Preset Chips (Top 5 models)
        val topPresets = remember(availableModels) { availableModels.take(5) }
        if (topPresets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_preset_models),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(topPresets, key = { it.id }) { modelInfo ->
                        val isSelected = effectiveCurrentModel == modelInfo.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onModelSelected(modelInfo.id) },
                            label = {
                                Text(
                                    text = modelInfo.id.substringAfter("/"),
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

        // Manual Model ID Input (Collapsible)
        TextButton(
            onClick = { isManualInputExpanded = !isManualInputExpanded },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Icon(
                imageVector = if (isManualInputExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.settings_custom_model_id), style = MaterialTheme.typography.labelSmall)
        }

        AnimatedVisibility(visible = isManualInputExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = manualModelInput,
                    onValueChange = { manualModelInput = it },
                    label = { Text("Model ID") },
                    placeholder = { Text("e.g. gpt-4o or gemini-2.0-flash") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onModelSelected(manualModelInput.trim()) },
                    shape = RoundedCornerShape(10.dp),
                    enabled = manualModelInput.isNotBlank() && manualModelInput != effectiveCurrentModel
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }

    // Searchable Model Picker Dialog
    if (showModelPickerDialog) {
        val filteredModels = remember(searchQuery, availableModels) {
            if (searchQuery.isBlank()) {
                availableModels
            } else {
                availableModels.filter {
                    it.id.contains(searchQuery, ignoreCase = true) ||
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                showModelPickerDialog = false
                searchQuery = ""
            },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_select_model_title, providerType.displayName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.translate_search_language)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            text = {
                Box(modifier = Modifier.heightIn(max = 380.dp).fillMaxWidth()) {
                    if (filteredModels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No models found matching \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(filteredModels, key = { it.id }) { modelInfo ->
                                val isSelected = effectiveCurrentModel == modelInfo.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onModelSelected(modelInfo.id)
                                            showModelPickerDialog = false
                                            searchQuery = ""
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = modelInfo.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (modelInfo.displayName != modelInfo.id || modelInfo.description.isNotBlank()) {
                                                Text(
                                                    text = if (modelInfo.description.isNotBlank()) "${modelInfo.id} • ${modelInfo.description}" else modelInfo.id,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showModelPickerDialog = false
                    searchQuery = ""
                }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

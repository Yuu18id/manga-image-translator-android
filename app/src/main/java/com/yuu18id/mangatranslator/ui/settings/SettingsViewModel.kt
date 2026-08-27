package com.yuu18id.mangatranslator.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuu18id.mangatranslator.data.translation.model.AiModelInfo
import com.yuu18id.mangatranslator.data.translation.model.ModelFetcherService
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TranslationConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import com.yuu18id.mangatranslator.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val config: TranslationConfig = TranslationConfig(),
    val apiKeys: Map<TranslatorType, String> = emptyMap(),
    val selectedModels: Map<TranslatorType, String> = emptyMap(),
    val cachedModels: Map<TranslatorType, List<AiModelInfo>> = emptyMap(),
    val isFetchingModels: Map<TranslatorType, Boolean> = emptyMap(),
    val fetchError: Map<TranslatorType, String?> = emptyMap(),
    val customBaseUrl: String = "http://localhost:11434/v1"
) {
    // Backwards-compatible convenience properties
    val openAiKey: String get() = apiKeys[TranslatorType.OPENAI] ?: ""
    val openRouterKey: String get() = apiKeys[TranslatorType.OPENROUTER] ?: ""
    val openRouterModel: String get() = selectedModels[TranslatorType.OPENROUTER] ?: "google/gemini-2.0-flash-001"
    val deepLKey: String get() = apiKeys[TranslatorType.DEEPL] ?: ""
    val geminiKey: String get() = apiKeys[TranslatorType.GEMINI] ?: ""
    val deepSeekKey: String get() = apiKeys[TranslatorType.DEEPSEEK] ?: ""
    val groqKey: String get() = apiKeys[TranslatorType.GROQ] ?: ""
    val claudeKey: String get() = apiKeys[TranslatorType.CLAUDE] ?: ""
    val glmKey: String get() = apiKeys[TranslatorType.GLM] ?: ""
    val customKey: String get() = apiKeys[TranslatorType.CUSTOM] ?: ""
    val papagoKey: String get() = apiKeys[TranslatorType.PAPAGO] ?: ""
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val modelFetcherService: ModelFetcherService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val config = settingsRepository.getTranslationConfig().first()
            val customUrl = settingsRepository.getCustomBaseUrl().first()

            val keysMap = mutableMapOf<TranslatorType, String>()
            val modelsMap = mutableMapOf<TranslatorType, String>()
            val cachedModelsMap = mutableMapOf<TranslatorType, List<AiModelInfo>>()

            for (type in TranslatorType.values()) {
                if (type.requiresApiKey) {
                    keysMap[type] = settingsRepository.getApiKey(type).first()
                }
                if (type.isLlm) {
                    modelsMap[type] = settingsRepository.getModel(type).first()
                    cachedModelsMap[type] = settingsRepository.getCachedModels(type).first()
                }
            }

            _uiState.update {
                it.copy(
                    config = config,
                    apiKeys = keysMap,
                    selectedModels = modelsMap,
                    cachedModels = cachedModelsMap,
                    customBaseUrl = customUrl
                )
            }
        }
    }

    fun updateSourceLanguage(language: Language?) {
        viewModelScope.launch {
            val updatedConfig = _uiState.value.config.copy(
                translator = _uiState.value.config.translator.copy(sourceLang = language)
            )
            settingsRepository.saveTranslationConfig(updatedConfig)
            _uiState.update { it.copy(config = updatedConfig) }
        }
    }

    fun updateTargetLanguage(language: Language) {
        viewModelScope.launch {
            val updatedConfig = _uiState.value.config.copy(
                translator = _uiState.value.config.translator.copy(targetLang = language)
            )
            settingsRepository.saveTranslationConfig(updatedConfig)
            _uiState.update { it.copy(config = updatedConfig) }
        }
    }

    fun updateTranslator(translator: TranslatorType) {
        viewModelScope.launch {
            val updatedConfig = _uiState.value.config.copy(
                translator = _uiState.value.config.translator.copy(translatorType = translator)
            )
            settingsRepository.saveTranslationConfig(updatedConfig)
            _uiState.update { it.copy(config = updatedConfig) }
        }
    }

    fun updateOcrType(ocrType: com.yuu18id.mangatranslator.domain.model.OcrType) {
        viewModelScope.launch {
            val updatedConfig = _uiState.value.config.copy(
                ocr = _uiState.value.config.ocr.copy(ocrType = ocrType)
            )
            settingsRepository.saveTranslationConfig(updatedConfig)
            _uiState.update { it.copy(config = updatedConfig) }
        }
    }

    fun saveApiKey(translatorType: TranslatorType, key: String) {
        viewModelScope.launch {
            settingsRepository.saveApiKey(translatorType, key.trim())
            _uiState.update { state ->
                val newKeys = state.apiKeys.toMutableMap().apply { put(translatorType, key.trim()) }
                state.copy(apiKeys = newKeys)
            }

            // Auto-fetch models when user pastes/updates key if LLM provider
            if (translatorType.isLlm && key.isNotBlank()) {
                fetchModels(translatorType)
            }
        }
    }

    fun selectModel(translatorType: TranslatorType, modelId: String) {
        viewModelScope.launch {
            settingsRepository.saveModel(translatorType, modelId.trim())
            _uiState.update { state ->
                val newModels = state.selectedModels.toMutableMap().apply { put(translatorType, modelId.trim()) }
                state.copy(selectedModels = newModels)
            }
        }
    }

    fun updateOpenRouterModel(modelId: String) {
        selectModel(TranslatorType.OPENROUTER, modelId)
    }

    fun saveCustomBaseUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.saveCustomBaseUrl(url.trim())
            _uiState.update { it.copy(customBaseUrl = url.trim()) }
        }
    }

    fun fetchModels(provider: TranslatorType) {
        viewModelScope.launch {
            _uiState.update { state ->
                val fetching = state.isFetchingModels.toMutableMap().apply { put(provider, true) }
                val errors = state.fetchError.toMutableMap().apply { put(provider, null) }
                state.copy(isFetchingModels = fetching, fetchError = errors)
            }

            val apiKey = _uiState.value.apiKeys[provider] ?: ""
            val baseUrl = _uiState.value.customBaseUrl

            val result = modelFetcherService.fetchModels(provider, apiKey, baseUrl)

            _uiState.update { state ->
                val fetching = state.isFetchingModels.toMutableMap().apply { put(provider, false) }
                if (result.isSuccess) {
                    val models = result.getOrNull() ?: emptyList()
                    viewModelScope.launch { settingsRepository.saveCachedModels(provider, models) }
                    val newCached = state.cachedModels.toMutableMap().apply { put(provider, models) }
                    val errors = state.fetchError.toMutableMap().apply { put(provider, null) }
                    state.copy(isFetchingModels = fetching, cachedModels = newCached, fetchError = errors)
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to fetch models"
                    val errors = state.fetchError.toMutableMap().apply { put(provider, errorMsg) }
                    state.copy(isFetchingModels = fetching, fetchError = errors)
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }
}

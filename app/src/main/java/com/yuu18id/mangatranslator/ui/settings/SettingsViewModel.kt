package com.yuu18id.mangatranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsUiState(
    val config: TranslationConfig = TranslationConfig(),
    val openAiKey: String = "",
    val openRouterKey: String = "",
    val openRouterModel: String = "google/gemini-2.0-flash-001",
    val deepLKey: String = "",
    val geminiKey: String = "",
    val deepSeekKey: String = "",
    val groqKey: String = "",
    val papagoKey: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val config = settingsRepository.getTranslationConfig().first()
            val openAi = settingsRepository.getApiKey(TranslatorType.OPENAI).first()
            val openRouter = settingsRepository.getApiKey(TranslatorType.OPENROUTER).first()
            val openRouterModel = settingsRepository.getOpenRouterModel().first()
            val deepL = settingsRepository.getApiKey(TranslatorType.DEEPL).first()
            val gemini = settingsRepository.getApiKey(TranslatorType.GEMINI).first()
            val deepSeek = settingsRepository.getApiKey(TranslatorType.DEEPSEEK).first()
            val groq = settingsRepository.getApiKey(TranslatorType.GROQ).first()
            val papago = settingsRepository.getApiKey(TranslatorType.PAPAGO).first()

            _uiState.update {
                it.copy(
                    config = config,
                    openAiKey = openAi,
                    openRouterKey = openRouter,
                    openRouterModel = if (openRouterModel.isNotBlank()) openRouterModel else "google/gemini-2.0-flash-001",
                    deepLKey = deepL,
                    geminiKey = gemini,
                    deepSeekKey = deepSeek,
                    groqKey = groq,
                    papagoKey = papago
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

    fun updateOpenRouterModel(modelId: String) {
        viewModelScope.launch {
            settingsRepository.saveOpenRouterModel(modelId)
            _uiState.update { it.copy(openRouterModel = modelId) }
        }
    }

    fun updateDetectionResolution(size: Int) {
        viewModelScope.launch {
            val updatedConfig = _uiState.value.config.copy(
                detector = _uiState.value.config.detector.copy(detectionSize = size)
            )
            settingsRepository.saveTranslationConfig(updatedConfig)
            _uiState.update { it.copy(config = updatedConfig) }
        }
    }

    fun updateInpaintingResolution(size: Int) {
        viewModelScope.launch {
            val updatedConfig = _uiState.value.config.copy(
                inpainter = _uiState.value.config.inpainter.copy(inpaintingSize = size)
            )
            settingsRepository.saveTranslationConfig(updatedConfig)
            _uiState.update { it.copy(config = updatedConfig) }
        }
    }

    fun updateFontSizeOffset(offset: Int) {
        viewModelScope.launch {
            val updatedConfig = _uiState.value.config.copy(
                render = _uiState.value.config.render.copy(fontSizeOffset = offset)
            )
            settingsRepository.saveTranslationConfig(updatedConfig)
            _uiState.update { it.copy(config = updatedConfig) }
        }
    }

    fun saveApiKey(translatorType: TranslatorType, key: String) {
        viewModelScope.launch {
            settingsRepository.saveApiKey(translatorType, key)
            _uiState.update { state ->
                when (translatorType) {
                    TranslatorType.OPENAI -> state.copy(openAiKey = key)
                    TranslatorType.OPENROUTER -> state.copy(openRouterKey = key)
                    TranslatorType.DEEPL -> state.copy(deepLKey = key)
                    TranslatorType.GEMINI -> state.copy(geminiKey = key)
                    TranslatorType.DEEPSEEK -> state.copy(deepSeekKey = key)
                    TranslatorType.GROQ -> state.copy(groqKey = key)
                    TranslatorType.PAPAGO -> state.copy(papagoKey = key)
                    else -> state
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

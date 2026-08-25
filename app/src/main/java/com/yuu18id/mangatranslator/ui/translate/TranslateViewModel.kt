package com.yuu18id.mangatranslator.ui.translate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.PipelineStage
import com.yuu18id.mangatranslator.domain.model.PipelineState
import com.yuu18id.mangatranslator.domain.model.TranslationConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import com.yuu18id.mangatranslator.domain.repository.SettingsRepository
import com.yuu18id.mangatranslator.domain.usecase.TranslateImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class TranslateUiState(
    val selectedImageUri: Uri? = null,
    val originalBitmap: Bitmap? = null,
    val translatedBitmap: Bitmap? = null,
    val currentStage: PipelineStage = PipelineStage.DETECTION,
    val stageMessage: String = "",
    val progress: Float = 0f,
    val isTranslating: Boolean = false,
    val showOriginal: Boolean = false,
    val sourceLang: Language? = null, // null for Auto
    val targetLang: Language = Language.ENG,
    val translatorType: TranslatorType = TranslatorType.DEEPL,
    val error: String? = null,
    val savedHistoryId: Long? = null
)

@HiltViewModel
class TranslateViewModel @Inject constructor(
    private val translateImageUseCase: TranslateImageUseCase,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranslateUiState())
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    private var translationJob: Job? = null

    init {
        viewModelScope.launch {
            val config = settingsRepository.getTranslationConfig().first()
            _uiState.update {
                it.copy(
                    sourceLang = config.translator.sourceLang,
                    targetLang = config.translator.targetLang,
                    translatorType = config.translator.translatorType
                )
            }
        }
    }

    fun setImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    decodeBitmapFromUri(uri)
                }
                _uiState.update {
                    it.copy(
                        selectedImageUri = uri,
                        originalBitmap = bitmap,
                        translatedBitmap = null, // Reset translation
                        error = null,
                        savedHistoryId = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load image: ${e.message}") }
            }
        }
    }

    fun setSourceLang(lang: Language?) {
        _uiState.update { it.copy(sourceLang = lang) }
    }

    fun setTargetLang(lang: Language) {
        _uiState.update { it.copy(targetLang = lang) }
    }

    fun setTranslatorType(type: TranslatorType) {
        _uiState.update { it.copy(translatorType = type) }
    }

    fun toggleShowOriginal() {
        _uiState.update { it.copy(showOriginal = !it.showOriginal) }
    }

    fun startTranslation() {
        val state = _uiState.value
        val bitmap = state.originalBitmap ?: return
        
        if (state.isTranslating) return

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isTranslating = true, 
                    error = null, 
                    progress = 0f, 
                    showOriginal = false,
                    stageMessage = "Initializing..."
                ) 
            }

            val savedConfig = settingsRepository.getTranslationConfig().first()
            val config = savedConfig.copy(
                translator = savedConfig.translator.copy(
                    translatorType = state.translatorType,
                    targetLang = state.targetLang,
                    sourceLang = state.sourceLang
                )
            )

            try {
                translateImageUseCase(bitmap, config).collect { pipelineState ->
                    when (pipelineState) {
                        is PipelineState.Progress -> {
                            _uiState.update {
                                it.copy(
                                    currentStage = pipelineState.stage,
                                    progress = pipelineState.progress,
                                    stageMessage = pipelineState.message
                                )
                            }
                        }
                        is PipelineState.Completed -> {
                            val result = pipelineState.result
                            val resultBitmap = result.translatedImage
                            val historyId = historyRepository.saveTranslation(result)

                            _uiState.update {
                                it.copy(
                                    isTranslating = false,
                                    translatedBitmap = resultBitmap,
                                    progress = 1.0f,
                                    stageMessage = "Translation complete",
                                    savedHistoryId = historyId
                                )
                            }
                        }
                        is PipelineState.Error -> {
                            _uiState.update {
                                it.copy(
                                    isTranslating = false,
                                    error = pipelineState.message,
                                    stageMessage = "Error occurred"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTranslating = false,
                        error = e.message ?: "Translation failed",
                        stageMessage = "Error occurred"
                    )
                }
            }
        }
    }

    fun cancelTranslation() {
        translationJob?.cancel()
        _uiState.update {
            it.copy(
                isTranslating = false,
                stageMessage = "Cancelled",
                progress = 0f
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeBitmapFromUri(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap, prefix: String = "translated_"): File {
        val dir = File(context.filesDir, "translations")
        if (!dir.exists()) dir.mkdirs()
        
        val file = File(dir, "${prefix}${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}

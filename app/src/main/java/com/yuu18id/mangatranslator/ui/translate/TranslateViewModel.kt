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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

@Immutable
data class TranslateUiState(
    val selectedImageUri: Uri? = null,
    val originalImage: ImageBitmap? = null,
    val translatedImage: ImageBitmap? = null,
    val showOriginal: Boolean = false,
    val sourceLang: Language? = Language.JPN,
    val targetLang: Language = Language.ENG,
    val translatorType: TranslatorType = TranslatorType.DEEPL,
    val error: String? = null,
    val savedHistoryId: Long? = null
)

@Immutable
data class TranslateProgressState(
    val currentStage: PipelineStage = PipelineStage.DETECTION,
    val stageMessage: String = "",
    val progress: Float = 0f,
    val isTranslating: Boolean = false
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

    private val _progressState = MutableStateFlow(TranslateProgressState())
    val progressState: StateFlow<TranslateProgressState> = _progressState.asStateFlow()

    private var translationJob: Job? = null
    
    private var _originalBitmap: Bitmap? = null
    private var _translatedBitmap: Bitmap? = null

    fun getTranslatedBitmap(): Bitmap? = _translatedBitmap

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
                _originalBitmap = bitmap
                _translatedBitmap = null
                _uiState.update {
                    it.copy(
                        selectedImageUri = uri,
                        originalImage = bitmap.asImageBitmap(),
                        translatedImage = null, // Reset translation
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
        val bitmap = _originalBitmap ?: return
        val state = _uiState.value
        
        if (_progressState.value.isTranslating) return

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _progressState.update { 
                it.copy(
                    isTranslating = true, 
                    progress = 0f, 
                    stageMessage = "Initializing..."
                ) 
            }
            _uiState.update {
                it.copy(
                    error = null, 
                    showOriginal = false
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
                            _progressState.update {
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
                            _translatedBitmap = resultBitmap
                            val historyId = historyRepository.saveTranslation(result)

                            _progressState.update {
                                it.copy(
                                    isTranslating = false,
                                    progress = 1.0f,
                                    stageMessage = "Translation complete"
                                )
                            }
                            _uiState.update {
                                it.copy(
                                    translatedImage = resultBitmap.asImageBitmap(),
                                    savedHistoryId = historyId
                                )
                            }
                        }
                        is PipelineState.Error -> {
                            _progressState.update {
                                it.copy(
                                    isTranslating = false,
                                    stageMessage = "Error occurred"
                                )
                            }
                            _uiState.update {
                                it.copy(
                                    error = pipelineState.message
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _progressState.update {
                    it.copy(
                        isTranslating = false,
                        stageMessage = "Error occurred"
                    )
                }
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Translation failed"
                    )
                }
            }
        }
    }

    fun cancelTranslation() {
        translationJob?.cancel()
        _progressState.update {
            it.copy(
                isTranslating = false,
                stageMessage = "Cancelled",
                progress = 0f
            )
        }
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            val maxDim = 2048
            if (info.size.width > maxDim || info.size.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(info.size.width, info.size.height)
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt()
                )
            }
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

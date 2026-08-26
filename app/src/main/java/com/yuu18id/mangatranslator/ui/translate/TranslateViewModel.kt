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

import com.yuu18id.mangatranslator.data.ml.TextRenderer
import com.yuu18id.mangatranslator.data.translation.TranslatorFactory
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslationResult

@Immutable
data class TranslateUiState(
    val selectedImageUri: Uri? = null,
    val originalImage: ImageBitmap? = null,
    val translatedImage: ImageBitmap? = null,
    val inpaintedImage: ImageBitmap? = null,
    val currentTextBlocks: List<TextBlock> = emptyList(),
    val showOriginal: Boolean = false,
    val isReviewModeEnabled: Boolean = false,
    val isShowingDetectionEditor: Boolean = false,
    val isShowingRenderEditor: Boolean = false,
    val pendingDetections: List<com.yuu18id.mangatranslator.domain.model.Quadrilateral> = emptyList(),
    val sourceLang: Language? = Language.JPN,
    val targetLang: Language = Language.ENG,
    val translatorType: TranslatorType = TranslatorType.DEEPL,
    val error: String? = null,
    val savedHistoryId: Long? = null
) {
    val canFastRetranslate: Boolean get() = inpaintedImage != null && currentTextBlocks.isNotEmpty()
}

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
    private val translatorFactory: TranslatorFactory,
    private val textRenderer: TextRenderer,
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
    private var _inpaintedBitmap: Bitmap? = null
    private var _currentTextBlocks: List<TextBlock> = emptyList()
    private var _pendingRawMask: Bitmap? = null

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
        val uriStr = uri.toString()
        if (uriStr.startsWith("history:")) {
            val historyId = uriStr.removePrefix("history:").toLongOrNull()
            if (historyId != null) {
                loadFromHistory(historyId)
                return
            }
        }

        viewModelScope.launch {
            try {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    decodeBitmapFromUri(uri)
                }
                _originalBitmap = bitmap
                _translatedBitmap = null
                _inpaintedBitmap = null
                _currentTextBlocks = emptyList()
                _pendingRawMask?.recycle()
                _pendingRawMask = null
                _uiState.update {
                    it.copy(
                        selectedImageUri = uri,
                        originalImage = bitmap.asImageBitmap(),
                        translatedImage = null, // Reset translation
                        inpaintedImage = null,
                        currentTextBlocks = emptyList(),
                        error = null,
                        savedHistoryId = null,
                        isShowingDetectionEditor = false,
                        isShowingRenderEditor = false,
                        pendingDetections = emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load image: ${e.message}") }
            }
        }
    }

    fun loadFromHistory(historyId: Long) {
        viewModelScope.launch {
            _progressState.update {
                it.copy(
                    isTranslating = true,
                    progress = 0.5f,
                    stageMessage = "Memuat data terjemahan..."
                )
            }
            try {
                val record = historyRepository.getFullHistoryRecord(historyId)
                if (record != null) {
                    _originalBitmap = record.originalBitmap
                    _translatedBitmap = record.translatedBitmap
                    _inpaintedBitmap = record.inpaintedBitmap
                    _currentTextBlocks = record.textBlocks
                    _pendingRawMask?.recycle()
                    _pendingRawMask = null

                    _uiState.update {
                        it.copy(
                            selectedImageUri = Uri.parse("history:$historyId"),
                            originalImage = record.originalBitmap?.asImageBitmap(),
                            translatedImage = record.translatedBitmap?.asImageBitmap(),
                            inpaintedImage = record.inpaintedBitmap?.asImageBitmap(),
                            currentTextBlocks = record.textBlocks,
                            sourceLang = record.item.sourceLang,
                            targetLang = record.item.targetLang,
                            translatorType = record.item.translatorType,
                            savedHistoryId = record.item.id,
                            error = null,
                            isShowingDetectionEditor = false,
                            isShowingRenderEditor = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Gagal memuat history: ${e.message}") }
            } finally {
                _progressState.update { it.copy(isTranslating = false) }
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

    fun toggleReviewMode() {
        _uiState.update { it.copy(isReviewModeEnabled = !it.isReviewModeEnabled) }
    }

    fun dismissDetectionEditor() {
        _pendingRawMask?.recycle()
        _pendingRawMask = null
        _uiState.update { it.copy(isShowingDetectionEditor = false, pendingDetections = emptyList()) }
        _progressState.update { it.copy(isTranslating = false, progress = 0f, stageMessage = "") }
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
                    progress = 0.1f, 
                    stageMessage = "Mendeteksi teks pada gambar..."
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

            if (state.isReviewModeEnabled) {
                // Run detection only, then pause and show interactive editor
                try {
                    val detectionResult = translateImageUseCase.detectOnly(bitmap, config.detector)
                    _pendingRawMask?.recycle()
                    _pendingRawMask = detectionResult.mask
                    _progressState.update { it.copy(isTranslating = false, progress = 0f, stageMessage = "") }
                    _uiState.update {
                        it.copy(
                            isShowingDetectionEditor = true,
                            pendingDetections = detectionResult.textlines
                        )
                    }
                } catch (e: Exception) {
                    _progressState.update { it.copy(isTranslating = false, stageMessage = "Error detection") }
                    _uiState.update { it.copy(error = e.message ?: "Detection failed") }
                }
                return@launch
            }

            // Direct auto translation
            try {
                translateImageUseCase(bitmap, config).collect { pipelineState ->
                    handlePipelineState(pipelineState)
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

    fun applyEditedDetectionsAndTranslate(curatedTextlines: List<com.yuu18id.mangatranslator.domain.model.Quadrilateral>) {
        val bitmap = _originalBitmap ?: return
        val state = _uiState.value
        val rawMask = _pendingRawMask
        _pendingRawMask = null

        _uiState.update {
            it.copy(
                isShowingDetectionEditor = false,
                pendingDetections = emptyList(),
                error = null
            )
        }

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _progressState.update {
                it.copy(
                    isTranslating = true,
                    progress = 0.2f,
                    stageMessage = "Memproses teks terpilih..."
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
                translateImageUseCase.executeFromDetections(bitmap, curatedTextlines, config, rawMask).collect { pipelineState ->
                    handlePipelineState(pipelineState)
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

    fun openRenderEditor() {
        if (_inpaintedBitmap != null && _currentTextBlocks.isNotEmpty()) {
            _uiState.update { it.copy(isShowingRenderEditor = true) }
        }
    }

    fun dismissRenderEditor() {
        _uiState.update { it.copy(isShowingRenderEditor = false) }
    }

    fun applyEditedRender(updatedBlocks: List<TextBlock>) {
        val inpainted = _inpaintedBitmap ?: return
        _uiState.update { it.copy(isShowingRenderEditor = false) }

        viewModelScope.launch {
            _progressState.update {
                it.copy(
                    isTranslating = true,
                    progress = 0.9f,
                    stageMessage = "Menerapkan render teks baru..."
                )
            }

            try {
                val savedConfig = settingsRepository.getTranslationConfig().first()
                val (newFinalImage, finalizedBlocks) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    textRenderer.renderWithUpdatedBlocks(inpainted, updatedBlocks, savedConfig.render)
                }
                _translatedBitmap = newFinalImage
                _currentTextBlocks = finalizedBlocks

                val original = _originalBitmap ?: inpainted
                val updatedResult = TranslationResult(
                    originalImage = original,
                    translatedImage = newFinalImage,
                    textBlocks = finalizedBlocks,
                    config = savedConfig,
                    timestamp = System.currentTimeMillis(),
                    processingTimeMs = 0L,
                    inpaintedImage = inpainted
                )
                val currentHistoryId = _uiState.value.savedHistoryId
                val historyId = if (currentHistoryId != null) {
                    historyRepository.updateTranslation(currentHistoryId, updatedResult)
                } else {
                    historyRepository.saveTranslation(updatedResult)
                }

                _progressState.update {
                    it.copy(
                        isTranslating = false,
                        progress = 1.0f,
                        stageMessage = "Render selesai"
                    )
                }
                _uiState.update {
                    it.copy(
                        translatedImage = newFinalImage.asImageBitmap(),
                        inpaintedImage = inpainted.asImageBitmap(),
                        currentTextBlocks = finalizedBlocks,
                        savedHistoryId = historyId
                    )
                }
            } catch (e: Exception) {
                _progressState.update {
                    it.copy(
                        isTranslating = false,
                        stageMessage = "Gagal render"
                    )
                }
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to render"
                    )
                }
            }
        }
    }

    fun retranslateTextOnly(
        newTranslatorType: TranslatorType? = null,
        newTargetLang: Language? = null
    ) {
        val inpainted = _inpaintedBitmap ?: return
        val currentBlocks = _currentTextBlocks
        if (currentBlocks.isEmpty()) return

        val state = _uiState.value
        val activeTranslatorType = newTranslatorType ?: state.translatorType
        val activeTargetLang = newTargetLang ?: state.targetLang
        val activeSourceLang = state.sourceLang

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _progressState.update {
                it.copy(
                    isTranslating = true,
                    currentStage = PipelineStage.TRANSLATION,
                    progress = 0.4f,
                    stageMessage = "Menerjemahkan ulang teks saja ($activeTranslatorType)..."
                )
            }

            try {
                val savedConfig = settingsRepository.getTranslationConfig().first()
                val translatorConfig = savedConfig.translator.copy(
                    translatorType = activeTranslatorType,
                    targetLang = activeTargetLang,
                    sourceLang = activeSourceLang
                )

                // 1. Create specific cloud/offline translator
                val translator = translatorFactory.getTranslator(activeTranslatorType)
                
                // Clear old translated text so engine translates fresh from original Japanese source text
                val blocksToTranslate = currentBlocks.map { it.copy(translatedText = "") }
                val translatedBlocks: List<TextBlock> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    translator.translate(blocksToTranslate, translatorConfig)
                }

                _progressState.update {
                    it.copy(
                        currentStage = PipelineStage.RENDERING,
                        progress = 0.85f,
                        stageMessage = "Me-render tipografi baru..."
                    )
                }

                // 2. Render directly onto cached inpainted bitmap
                val (newFinalImage, finalizedBlocks) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    textRenderer.renderWithUpdatedBlocks(inpainted, translatedBlocks, savedConfig.render)
                }

                _translatedBitmap = newFinalImage
                _currentTextBlocks = finalizedBlocks

                val original = _originalBitmap ?: inpainted
                val updatedResult = TranslationResult(
                    originalImage = original,
                    translatedImage = newFinalImage,
                    textBlocks = finalizedBlocks,
                    config = savedConfig.copy(translator = translatorConfig),
                    timestamp = System.currentTimeMillis(),
                    processingTimeMs = 0L,
                    inpaintedImage = inpainted
                )
                val currentHistoryId = _uiState.value.savedHistoryId
                val historyId = if (currentHistoryId != null) {
                    historyRepository.updateTranslation(currentHistoryId, updatedResult)
                } else {
                    historyRepository.saveTranslation(updatedResult)
                }

                _progressState.update {
                    it.copy(
                        isTranslating = false,
                        progress = 1.0f,
                        stageMessage = ""
                    )
                }

                _uiState.update {
                    it.copy(
                        translatedImage = newFinalImage.asImageBitmap(),
                        inpaintedImage = inpainted.asImageBitmap(),
                        currentTextBlocks = finalizedBlocks,
                        savedHistoryId = historyId,
                        translatorType = activeTranslatorType,
                        targetLang = activeTargetLang
                    )
                }
            } catch (e: Exception) {
                _progressState.update {
                    it.copy(
                        isTranslating = false,
                        stageMessage = "Gagal menerjemahkan ulang"
                    )
                }
                _uiState.update {
                    it.copy(error = e.message ?: "Gagal menerjemahkan ulang teks")
                }
            }
        }
    }

    private suspend fun handlePipelineState(pipelineState: PipelineState) {
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
                _inpaintedBitmap = result.inpaintedImage ?: resultBitmap
                _currentTextBlocks = result.textBlocks
                val currentHistoryId = _uiState.value.savedHistoryId
                val historyId = if (currentHistoryId != null) {
                    historyRepository.updateTranslation(currentHistoryId, result)
                } else {
                    historyRepository.saveTranslation(result)
                }

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
                        inpaintedImage = _inpaintedBitmap?.asImageBitmap(),
                        currentTextBlocks = result.textBlocks,
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

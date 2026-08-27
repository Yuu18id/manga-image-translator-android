package com.yuu18id.mangatranslator.ui.batch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuu18id.mangatranslator.data.ml.TextRenderer
import com.yuu18id.mangatranslator.domain.model.*
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import com.yuu18id.mangatranslator.domain.repository.SettingsRepository
import com.yuu18id.mangatranslator.domain.usecase.TranslateImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BatchViewModel @Inject constructor(
    private val translateImageUseCase: TranslateImageUseCase,
    private val textRenderer: TextRenderer,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchUiState())
    val uiState: StateFlow<BatchUiState> = _uiState.asStateFlow()

    private var batchJob: Job? = null
    private var detectionReviewDeferred: CompletableDeferred<List<Quadrilateral>?>? = null
    private var currentReviewRawMask: Bitmap? = null

    private var currentEditingInpaintedBitmap: Bitmap? = null
    private var currentEditingOriginalBitmap: Bitmap? = null

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

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val currentPages = _uiState.value.pages.toMutableList()
            val startIndex = currentPages.size
            uris.forEachIndexed { i, uri ->
                val fileName = queryFileName(uri) ?: "Page_${startIndex + i + 1}.jpg"
                val item = BatchPageItem(
                    id = UUID.randomUUID().toString(),
                    uriString = uri.toString(),
                    fileName = fileName,
                    orderIndex = startIndex + i + 1
                )
                currentPages.add(item)
            }
            _uiState.update { it.copy(pages = currentPages) }
        }
    }

    fun movePageUp(index: Int) {
        if (index <= 0 || index >= _uiState.value.pages.size) return
        val pages = _uiState.value.pages.toMutableList()
        val item = pages.removeAt(index)
        pages.add(index - 1, item)
        updateOrderIndices(pages)
    }

    fun movePageDown(index: Int) {
        if (index < 0 || index >= _uiState.value.pages.size - 1) return
        val pages = _uiState.value.pages.toMutableList()
        val item = pages.removeAt(index)
        pages.add(index + 1, item)
        updateOrderIndices(pages)
    }

    fun removePage(pageId: String) {
        val pages = _uiState.value.pages.filter { it.id != pageId }.toMutableList()
        updateOrderIndices(pages)
    }

    fun sortByFileName() {
        val pages = _uiState.value.pages.sortedWith(NaturalOrderComparator { it.fileName }).toMutableList()
        updateOrderIndices(pages)
    }

    private fun updateOrderIndices(pages: MutableList<BatchPageItem>) {
        val reindexed = pages.mapIndexed { i, p -> p.copy(orderIndex = i + 1) }
        _uiState.update { it.copy(pages = reindexed) }
    }

    fun clearAll() {
        if (_uiState.value.isProcessing) {
            cancelBatchTranslation()
        }
        _uiState.update {
            it.copy(
                pages = emptyList(),
                isProcessing = false,
                currentProcessingIndex = -1,
                overallProgress = 0f,
                completedCount = 0,
                failedCount = 0,
                error = null,
                currentBatchId = null,
                currentBatchName = ""
            )
        }
    }

    fun updateAlbumName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val batchId = _uiState.value.currentBatchId
        val completedIds = _uiState.value.pages.mapNotNull { it.historyId }

        _uiState.update { it.copy(currentBatchName = trimmed) }

        viewModelScope.launch(Dispatchers.IO) {
            if (!batchId.isNullOrBlank()) {
                historyRepository.updateBatchName(batchId, trimmed)
            } else if (completedIds.isNotEmpty()) {
                historyRepository.updateBatchNameByIds(completedIds, trimmed)
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

    fun toggleReviewMode() {
        _uiState.update { it.copy(isReviewModeEnabled = !it.isReviewModeEnabled) }
    }

    // Detection Review Handling
    fun confirmDetections(curatedQuads: List<Quadrilateral>) {
        _uiState.update {
            it.copy(
                isShowingDetectionEditor = false,
                reviewImageBitmap = null,
                pendingDetections = emptyList(),
                reviewPageIndex = -1
            )
        }
        detectionReviewDeferred?.complete(curatedQuads)
        detectionReviewDeferred = null
    }

    fun dismissDetectionEditor() {
        _uiState.update {
            it.copy(
                isShowingDetectionEditor = false,
                reviewImageBitmap = null,
                pendingDetections = emptyList(),
                reviewPageIndex = -1
            )
        }
        detectionReviewDeferred?.complete(null)
        detectionReviewDeferred = null
    }

    // Render Typeset Editor Handling
    fun openRenderEditor(pageIndex: Int) {
        val page = _uiState.value.pages.getOrNull(pageIndex) ?: return
        val historyId = page.historyId ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val record = historyRepository.getFullHistoryRecord(historyId)
            if (record != null && record.inpaintedBitmap != null) {
                currentEditingInpaintedBitmap = record.inpaintedBitmap
                currentEditingOriginalBitmap = record.originalBitmap ?: record.inpaintedBitmap
                _uiState.update {
                    it.copy(
                        isShowingRenderEditor = true,
                        renderEditorInpaintedBitmap = record.inpaintedBitmap.asImageBitmap(),
                        renderEditorBlocks = record.textBlocks,
                        editingHistoryId = historyId,
                        editingPageIndex = pageIndex
                    )
                }
            }
        }
    }

    fun dismissRenderEditor() {
        _uiState.update {
            it.copy(
                isShowingRenderEditor = false,
                renderEditorInpaintedBitmap = null,
                renderEditorBlocks = emptyList(),
                editingHistoryId = null,
                editingPageIndex = -1
            )
        }
        currentEditingInpaintedBitmap = null
        currentEditingOriginalBitmap = null
    }

    fun applyEditedRender(updatedBlocks: List<TextBlock>) {
        val inpainted = currentEditingInpaintedBitmap ?: return
        val historyId = _uiState.value.editingHistoryId ?: return
        val pageIndex = _uiState.value.editingPageIndex

        dismissRenderEditor()

        viewModelScope.launch {
            try {
                val savedConfig = settingsRepository.getTranslationConfig().first()
                val (newFinalImage, finalizedBlocks) = withContext(Dispatchers.Default) {
                    textRenderer.renderWithUpdatedBlocks(inpainted, updatedBlocks, savedConfig.render)
                }

                val original = currentEditingOriginalBitmap ?: inpainted
                val updatedResult = TranslationResult(
                    originalImage = original,
                    translatedImage = newFinalImage,
                    textBlocks = finalizedBlocks,
                    config = savedConfig,
                    timestamp = System.currentTimeMillis(),
                    processingTimeMs = 0L,
                    inpaintedImage = inpainted
                )
                historyRepository.updateTranslation(historyId, updatedResult)

                if (pageIndex in _uiState.value.pages.indices) {
                    _uiState.update { current ->
                        val updatedList = current.pages.toMutableList()
                        updatedList[pageIndex] = updatedList[pageIndex].copy(
                            status = BatchPageStatus.COMPLETED,
                            stageMessage = ""
                        )
                        current.copy(pages = updatedList)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Typeset update error: ${e.message}") }
            }
        }
    }

    fun startBatchTranslation() {
        val state = _uiState.value
        val pagesToProcess = state.pages
        if (pagesToProcess.isEmpty() || state.isProcessing) return

        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    completedCount = 0,
                    failedCount = 0,
                    overallProgress = 0f,
                    error = null
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

            val total = pagesToProcess.size
            val currentBatchId = _uiState.value.currentBatchId ?: "batch_${System.currentTimeMillis()}"
            val currentBatchName = if (_uiState.value.currentBatchName.isNotBlank()) {
                _uiState.value.currentBatchName
            } else {
                "Chapter ($total Pages)"
            }

            _uiState.update {
                it.copy(
                    currentBatchId = currentBatchId,
                    currentBatchName = currentBatchName
                )
            }

            for (i in pagesToProcess.indices) {
                val page = _uiState.value.pages[i]
                if (page.status == BatchPageStatus.COMPLETED) {
                    continue
                }

                _uiState.update { current ->
                    val updatedList = current.pages.toMutableList()
                    updatedList[i] = page.copy(
                        status = BatchPageStatus.PROCESSING,
                        progress = 0f,
                        stageMessage = "Loading..."
                    )
                    current.copy(
                        pages = updatedList,
                        currentProcessingIndex = i,
                        overallProgress = (i.toFloat() / total.toFloat())
                    )
                }

                var originalBitmap: Bitmap? = null
                try {
                    originalBitmap = withContext(Dispatchers.IO) {
                        decodeBitmapFromUri(Uri.parse(page.uriString))
                    }

                    val pipelineFlow: Flow<PipelineState> = if (state.isReviewModeEnabled) {
                        // 1. Detection Only
                        _uiState.update { current ->
                            val updatedList = current.pages.toMutableList()
                            updatedList[i] = updatedList[i].copy(
                                stageMessage = "Detecting text bubbles...",
                                progress = 0.15f
                            )
                            current.copy(pages = updatedList)
                        }

                        val detResult = translateImageUseCase.detectOnly(originalBitmap, config.detector)
                        currentReviewRawMask = detResult.mask

                        val deferred = CompletableDeferred<List<Quadrilateral>?>()
                        detectionReviewDeferred = deferred

                        _uiState.update {
                            it.copy(
                                isShowingDetectionEditor = true,
                                reviewImageBitmap = originalBitmap.asImageBitmap(),
                                pendingDetections = detResult.textlines,
                                reviewPageIndex = i
                            )
                        }

                        // Wait for user review response
                        val curatedQuads = deferred.await()
                        val finalQuads = curatedQuads ?: detResult.textlines

                        translateImageUseCase.executeFromDetections(
                            image = originalBitmap,
                            customTextlines = finalQuads,
                            config = config,
                            rawMask = currentReviewRawMask
                        )
                    } else {
                        translateImageUseCase(originalBitmap, config)
                    }

                    pipelineFlow.collect { pipelineState ->
                        when (pipelineState) {
                            is PipelineState.Progress -> {
                                _uiState.update { current ->
                                    val updatedList = current.pages.toMutableList()
                                    updatedList[i] = updatedList[i].copy(
                                        currentStage = pipelineState.stage,
                                        progress = pipelineState.progress,
                                        stageMessage = pipelineState.message
                                    )
                                    val pageProgressContribution = pipelineState.progress / total.toFloat()
                                    val currentOverall = (i.toFloat() / total.toFloat()) + pageProgressContribution
                                    current.copy(
                                        pages = updatedList,
                                        overallProgress = currentOverall.coerceIn(0f, 1f)
                                    )
                                }
                            }
                            is PipelineState.Completed -> {
                                val result = pipelineState.result
                                val historyId = withContext(Dispatchers.IO) {
                                    saveResultToHistory(result, currentBatchId, currentBatchName, i)
                                }

                                _uiState.update { current ->
                                    val updatedList = current.pages.toMutableList()
                                    val updatedCompleted = current.completedCount + 1
                                    updatedList[i] = updatedList[i].copy(
                                        status = BatchPageStatus.COMPLETED,
                                        progress = 1.0f,
                                        stageMessage = "",
                                        historyId = historyId
                                    )
                                    current.copy(
                                        pages = updatedList,
                                        completedCount = updatedCompleted,
                                        overallProgress = (updatedCompleted.toFloat() / total.toFloat())
                                    )
                                }
                            }
                            is PipelineState.Error -> {
                                throw Exception(pipelineState.message)
                            }
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        val updatedList = current.pages.toMutableList()
                        updatedList[i] = updatedList[i].copy(
                            status = BatchPageStatus.FAILED,
                            progress = 0f,
                            errorMessage = e.message ?: "Translation error"
                        )
                        current.copy(
                            pages = updatedList,
                            failedCount = current.failedCount + 1
                        )
                    }
                } finally {
                    try {
                        currentReviewRawMask?.recycle()
                        currentReviewRawMask = null
                        originalBitmap?.recycle()
                        originalBitmap = null
                    } catch (_: Throwable) {}
                }
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    currentProcessingIndex = -1,
                    overallProgress = 1.0f
                )
            }
        }
    }

    fun cancelBatchTranslation() {
        batchJob?.cancel()
        dismissDetectionEditor()
        _uiState.update { current ->
            val updatedList = current.pages.map { page ->
                if (page.status == BatchPageStatus.PROCESSING || page.status == BatchPageStatus.QUEUED) {
                    page.copy(status = BatchPageStatus.CANCELLED, stageMessage = "Cancelled")
                } else page
            }
            current.copy(
                isProcessing = false,
                currentProcessingIndex = -1,
                pages = updatedList
            )
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        try {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
            if (name == null) {
                name = uri.lastPathSegment
            }
        } catch (_: Exception) {}
        return name
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.isMutableRequired = true
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
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

    private suspend fun saveResultToHistory(
        result: TranslationResult,
        batchId: String,
        batchName: String,
        pageIndex: Int
    ): Long {
        return historyRepository.saveTranslation(
            result = result,
            batchId = batchId,
            batchName = batchName,
            pageIndex = pageIndex
        )
    }

    /**
     * Natural alphanumeric comparator (e.g. page_1.png, page_2.png, page_10.png)
     */
    private class NaturalOrderComparator<T>(private val selector: (T) -> String) : Comparator<T> {
        override fun compare(o1: T, o2: T): Int {
            val s1 = selector(o1)
            val s2 = selector(o2)
            var i = 0
            var j = 0
            while (i < s1.length && j < s2.length) {
                val c1 = s1[i]
                val c2 = s2[j]
                if (c1.isDigit() && c2.isDigit()) {
                    var start1 = i
                    while (i < s1.length && s1[i].isDigit()) i++
                    var start2 = j
                    while (j < s2.length && s2[j].isDigit()) j++
                    val num1 = s1.substring(start1, i).toLongOrNull() ?: 0L
                    val num2 = s2.substring(start2, j).toLongOrNull() ?: 0L
                    val diff = num1.compareTo(num2)
                    if (diff != 0) return diff
                } else {
                    val diff = c1.lowercaseChar().compareTo(c2.lowercaseChar())
                    if (diff != 0) return diff
                    i++
                    j++
                }
            }
            return s1.length - s2.length
        }
    }
}

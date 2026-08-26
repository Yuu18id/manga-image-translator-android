package com.yuu18id.mangatranslator.ui.batch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuu18id.mangatranslator.domain.model.*
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import com.yuu18id.mangatranslator.domain.repository.SettingsRepository
import com.yuu18id.mangatranslator.domain.usecase.TranslateImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BatchViewModel @Inject constructor(
    private val translateImageUseCase: TranslateImageUseCase,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchUiState())
    val uiState: StateFlow<BatchUiState> = _uiState.asStateFlow()

    private var batchJob: Job? = null

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
                error = null
            )
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
                        stageMessage = "Loading image..."
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

                    translateImageUseCase(originalBitmap, config).collect { pipelineState ->
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
                                    saveResultToHistory(result, Uri.parse(page.uriString))
                                }

                                _uiState.update { current ->
                                    val updatedList = current.pages.toMutableList()
                                    val updatedCompleted = current.completedCount + 1
                                    updatedList[i] = updatedList[i].copy(
                                        status = BatchPageStatus.COMPLETED,
                                        progress = 1.0f,
                                        stageMessage = "Completed",
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
                    // Memory safety: Recycle bitmap and invoke GC hint between pages
                    try {
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

    private suspend fun saveResultToHistory(result: TranslationResult, uri: Uri): Long {
        return historyRepository.saveTranslation(result)
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

package com.yuu18id.mangatranslator.ui.reader

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuu18id.mangatranslator.data.storage.MediaExporter
import com.yuu18id.mangatranslator.domain.model.TranslationHistoryItem
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface ReaderExportEvent {
    data class Success(val count: Int) : ReaderExportEvent
    data class Error(val message: String) : ReaderExportEvent
}

@Immutable
data class ReaderUiState(
    val pages: List<TranslationHistoryItem> = emptyList(),
    val currentPageIndex: Int = 0,
    val originalImagePath: String? = null,
    val translatedImagePath: String? = null,
    val totalPages: Int = 1,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
    private val mediaExporter: MediaExporter
) : ViewModel() {

    private val _exportEvents = MutableSharedFlow<ReaderExportEvent>()
    val exportEvents: SharedFlow<ReaderExportEvent> = _exportEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        val rawId = savedStateHandle.get<Any>("translationId") ?: savedStateHandle.get<Any>("historyId")
        if (rawId != null) {
            val str = rawId.toString()
            if (str.contains(",")) {
                val ids = str.split(",").mapNotNull { it.trim().toLongOrNull() }
                loadHistoryItems(ids)
            } else {
                val id = str.toLongOrNull()
                if (id != null && id > 0) {
                    loadHistoryItems(listOf(id))
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Invalid history ID") }
                }
            }
        }
    }

    fun loadHistoryItems(ids: List<Long>) {
        if (ids.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, error = "No pages to display") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                historyRepository.observeTranslationsByIds(ids).collect { loadedItems ->
                    if (loadedItems.isNotEmpty()) {
                        _uiState.update { current ->
                            val currentIdx = current.currentPageIndex.coerceIn(0, loadedItems.size - 1)
                            val currentItem = loadedItems[currentIdx]
                            current.copy(
                                pages = loadedItems,
                                currentPageIndex = currentIdx,
                                originalImagePath = currentItem.thumbnailPath,
                                translatedImagePath = currentItem.resultPath,
                                totalPages = loadedItems.size,
                                isLoading = false,
                                error = null
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Translation pages not found") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load translation") }
            }
        }
    }

    fun goToPage(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.pages.size) return
        val item = state.pages[index]
        _uiState.update {
            it.copy(
                currentPageIndex = index,
                originalImagePath = item.thumbnailPath,
                translatedImagePath = item.resultPath
            )
        }
    }

    fun nextPage() {
        val state = _uiState.value
        if (state.currentPageIndex < state.pages.size - 1) {
            goToPage(state.currentPageIndex + 1)
        }
    }

    fun previousPage() {
        val state = _uiState.value
        if (state.currentPageIndex > 0) {
            goToPage(state.currentPageIndex - 1)
        }
    }

    fun saveCurrentPage() {
        val state = _uiState.value
        if (state.pages.isEmpty()) return
        val currentItem = state.pages[state.currentPageIndex]

        viewModelScope.launch {
            val sourcePath = if (currentItem.resultPath.isNotBlank()) currentItem.resultPath else currentItem.thumbnailPath
            val subFolder = currentItem.batchName
            val fileName = if (!subFolder.isNullOrBlank()) {
                val pageIndexStr = String.format(Locale.getDefault(), "%03d", currentItem.pageIndex + 1)
                "${subFolder}_page_$pageIndexStr"
            } else {
                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(currentItem.timestamp))
                "MangaTranslator_$dateStr"
            }

            val result = mediaExporter.exportImage(sourcePath, fileName, subFolder)
            if (result.isSuccess) {
                _exportEvents.emit(ReaderExportEvent.Success(1))
            } else {
                _exportEvents.emit(ReaderExportEvent.Error(result.exceptionOrNull()?.message ?: "Failed to save image"))
            }
        }
    }

    fun saveEntireChapter() {
        val state = _uiState.value
        if (state.pages.isEmpty()) return

        viewModelScope.launch {
            val firstItem = state.pages.first()
            val subFolder = firstItem.batchName ?: "Chapter"
            val pairs = state.pages.map { page ->
                val sourcePath = if (page.resultPath.isNotBlank()) page.resultPath else page.thumbnailPath
                val pageIndexStr = String.format(Locale.getDefault(), "%03d", page.pageIndex + 1)
                val fileName = "${subFolder}_page_$pageIndexStr"
                Pair(sourcePath, fileName)
            }
            val count = mediaExporter.exportBatch(pairs, subFolder)
            if (count > 0) {
                _exportEvents.emit(ReaderExportEvent.Success(count))
            } else {
                _exportEvents.emit(ReaderExportEvent.Error("Failed to save chapter images"))
            }
        }
    }

    fun deleteCurrentItem(onDeleted: () -> Unit) {
        val state = _uiState.value
        if (state.pages.isEmpty()) return
        val currentItem = state.pages[state.currentPageIndex]

        viewModelScope.launch {
            historyRepository.deleteTranslation(currentItem.id)
            val updatedPages = state.pages.filter { it.id != currentItem.id }
            if (updatedPages.isEmpty()) {
                onDeleted()
            } else {
                val newIndex = state.currentPageIndex.coerceAtMost(updatedPages.size - 1)
                val newItem = updatedPages[newIndex]
                _uiState.update {
                    it.copy(
                        pages = updatedPages,
                        currentPageIndex = newIndex,
                        originalImagePath = newItem.thumbnailPath,
                        translatedImagePath = newItem.resultPath,
                        totalPages = updatedPages.size
                    )
                }
            }
        }
    }
}

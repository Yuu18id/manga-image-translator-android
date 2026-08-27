package com.yuu18id.mangatranslator.ui.gallery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuu18id.mangatranslator.data.storage.MediaExporter
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TranslationHistoryItem
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface GalleryUiItem {
    val key: String
    val timestamp: Long
    val coverPath: String
    val sourceLang: Language
    val targetLang: Language
    val allIds: List<Long>

    @Immutable
    data class Single(
        val item: TranslationHistoryItem
    ) : GalleryUiItem {
        override val key: String get() = "single_${item.id}"
        override val timestamp: Long get() = item.timestamp
        override val coverPath: String get() = if (item.resultPath.isNotBlank()) item.resultPath else item.thumbnailPath
        override val sourceLang: Language get() = item.sourceLang
        override val targetLang: Language get() = item.targetLang
        override val allIds: List<Long> get() = listOf(item.id)
    }

    @Immutable
    data class Album(
        val batchId: String,
        val title: String,
        val coverItem: TranslationHistoryItem,
        val pageItems: List<TranslationHistoryItem>,
        val pageCount: Int
    ) : GalleryUiItem {
        override val key: String get() = "album_${batchId}"
        override val timestamp: Long get() = pageItems.maxOfOrNull { it.timestamp } ?: coverItem.timestamp
        override val coverPath: String get() = if (coverItem.resultPath.isNotBlank()) coverItem.resultPath else coverItem.thumbnailPath
        override val sourceLang: Language get() = coverItem.sourceLang
        override val targetLang: Language get() = coverItem.targetLang
        override val allIds: List<Long> get() = pageItems.map { it.id }
    }
}

sealed interface ExportEvent {
    data class Success(val count: Int) : ExportEvent
    data class Error(val message: String) : ExportEvent
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val mediaExporter: MediaExporter
) : ViewModel() {

    private val _exportEvents = MutableSharedFlow<ExportEvent>()
    val exportEvents: SharedFlow<ExportEvent> = _exportEvents.asSharedFlow()

    private val _exportingKeys = MutableStateFlow<Set<String>>(emptySet())
    val exportingKeys: StateFlow<Set<String>> = _exportingKeys.asStateFlow()

    val galleryItems: StateFlow<List<GalleryUiItem>> = historyRepository.getRecentTranslations(300)
        .map { items ->
            groupHistoryItems(items)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun groupHistoryItems(items: List<TranslationHistoryItem>): List<GalleryUiItem> {
        val result = mutableListOf<GalleryUiItem>()
        val processedBatchIds = mutableSetOf<String>()

        for (item in items) {
            val bId = item.batchId
            if (!bId.isNullOrBlank()) {
                if (bId in processedBatchIds) continue
                processedBatchIds.add(bId)

                val batchPages = items.filter { it.batchId == bId }.sortedBy { it.pageIndex }
                if (batchPages.size > 1) {
                    val cover = batchPages.first()
                    result.add(
                        GalleryUiItem.Album(
                            batchId = bId,
                            title = cover.batchName ?: "Chapter (${batchPages.size} Halaman)",
                            coverItem = cover,
                            pageItems = batchPages,
                            pageCount = batchPages.size
                        )
                    )
                } else {
                    result.add(GalleryUiItem.Single(item))
                }
            } else {
                result.add(GalleryUiItem.Single(item))
            }
        }
        return result.sortedByDescending { it.timestamp }
    }

    fun saveSingleItem(item: TranslationHistoryItem) {
        val key = "single_${item.id}"
        if (_exportingKeys.value.contains(key)) return
        _exportingKeys.value = _exportingKeys.value + key
        viewModelScope.launch {
            try {
                val sourcePath = if (item.resultPath.isNotBlank()) item.resultPath else item.thumbnailPath
                val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))
                val fileName = "MangaTranslator_$dateStr"
                val result = mediaExporter.exportImage(sourcePath, fileName)
                if (result.isSuccess) {
                    _exportEvents.emit(ExportEvent.Success(1))
                } else {
                    _exportEvents.emit(ExportEvent.Error(result.exceptionOrNull()?.message ?: "Failed to save image"))
                }
            } finally {
                _exportingKeys.value = _exportingKeys.value - key
            }
        }
    }

    fun saveAlbum(album: GalleryUiItem.Album) {
        val key = album.key
        if (_exportingKeys.value.contains(key)) return
        _exportingKeys.value = _exportingKeys.value + key
        viewModelScope.launch {
            try {
                val pairs = album.pageItems.map { page ->
                    val sourcePath = if (page.resultPath.isNotBlank()) page.resultPath else page.thumbnailPath
                    val pageIndexStr = String.format(java.util.Locale.getDefault(), "%03d", page.pageIndex + 1)
                    val fileName = "${album.title}_page_$pageIndexStr"
                    Pair(sourcePath, fileName)
                }
                val count = mediaExporter.exportBatch(pairs, album.title)
                if (count > 0) {
                    _exportEvents.emit(ExportEvent.Success(count))
                } else {
                    _exportEvents.emit(ExportEvent.Error("Failed to save album images"))
                }
            } finally {
                _exportingKeys.value = _exportingKeys.value - key
            }
        }
    }

    fun saveSelectedItems(selectedKeys: Set<String>) {
        if (selectedKeys.isEmpty()) return
        if (_exportingKeys.value.any { it in selectedKeys }) return
        _exportingKeys.value = _exportingKeys.value + selectedKeys
        viewModelScope.launch {
            try {
                val currentItems = galleryItems.value.filter { it.key in selectedKeys }
                var totalSaved = 0
                for (uiItem in currentItems) {
                    when (uiItem) {
                        is GalleryUiItem.Single -> {
                            val item = uiItem.item
                            val sourcePath = if (item.resultPath.isNotBlank()) item.resultPath else item.thumbnailPath
                            val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))
                            val fileName = "MangaTranslator_$dateStr"
                            val res = mediaExporter.exportImage(sourcePath, fileName)
                            if (res.isSuccess) totalSaved++
                        }
                        is GalleryUiItem.Album -> {
                            val pairs = uiItem.pageItems.map { page ->
                                val sourcePath = if (page.resultPath.isNotBlank()) page.resultPath else page.thumbnailPath
                                val pageIndexStr = String.format(java.util.Locale.getDefault(), "%03d", page.pageIndex + 1)
                                val fileName = "${uiItem.title}_page_$pageIndexStr"
                                Pair(sourcePath, fileName)
                            }
                            totalSaved += mediaExporter.exportBatch(pairs, uiItem.title)
                        }
                    }
                }
                if (totalSaved > 0) {
                    _exportEvents.emit(ExportEvent.Success(totalSaved))
                } else {
                    _exportEvents.emit(ExportEvent.Error("Failed to save selected images"))
                }
            } finally {
                _exportingKeys.value = _exportingKeys.value - selectedKeys
            }
        }
    }

    fun renameAlbum(batchId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            historyRepository.updateBatchName(batchId, trimmed)
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteTranslation(id)
        }
    }

    fun deleteSelectedItems(ids: Collection<Long>) {
        viewModelScope.launch {
            historyRepository.deleteTranslations(ids)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }
}

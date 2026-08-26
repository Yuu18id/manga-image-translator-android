package com.yuu18id.mangatranslator.ui.gallery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.TranslationHistoryItem
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository
) : ViewModel() {

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

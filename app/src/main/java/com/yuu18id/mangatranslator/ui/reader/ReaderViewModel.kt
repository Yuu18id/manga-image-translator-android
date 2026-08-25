package com.yuu18id.mangatranslator.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val originalImagePath: String? = null,
    val translatedImagePath: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentHistoryId: Long? = null

    init {
        val rawId = savedStateHandle.get<Any>("translationId") ?: savedStateHandle.get<Any>("historyId")
        val historyId: Long? = when (rawId) {
            is Long -> rawId
            is Int -> rawId.toLong()
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull()
            else -> null
        }

        if (historyId != null && historyId > 0) {
            currentHistoryId = historyId
            loadHistoryItem(historyId)
        } else {
            _uiState.update { it.copy(isLoading = false, error = "Invalid history ID") }
        }
    }

    fun loadHistoryItem(id: Long) {
        currentHistoryId = id
        viewModelScope.launch {
            try {
                val item = historyRepository.getTranslationById(id)
                    ?: historyRepository.getRecentTranslations(100).first().find { it.id == id }
                
                if (item != null) {
                    _uiState.update {
                        it.copy(
                            originalImagePath = item.thumbnailPath,
                            translatedImagePath = item.resultPath,
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Translation not found"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load image"
                    )
                }
            }
        }
    }

    fun deleteCurrentItem(onDeleted: () -> Unit) {
        val id = currentHistoryId ?: return
        viewModelScope.launch {
            historyRepository.deleteTranslation(id)
            onDeleted()
        }
    }
}

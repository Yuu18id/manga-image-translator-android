package com.yuu18id.mangatranslator.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuu18id.mangatranslator.domain.model.TranslationConfig
import com.yuu18id.mangatranslator.domain.model.TranslationHistoryItem
import com.yuu18id.mangatranslator.domain.repository.HistoryRepository
import com.yuu18id.mangatranslator.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class HomeUiState(
    val recentTranslations: List<TranslationHistoryItem> = emptyList(),
    val activeConfig: TranslationConfig = TranslationConfig(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        historyRepository.getRecentTranslations(),
        settingsRepository.getTranslationConfig()
    ) { history, config ->
        HomeUiState(
            recentTranslations = history.take(8),
            activeConfig = config,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )
}

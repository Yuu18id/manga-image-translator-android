package com.yuu18id.mangatranslator.domain.model

import androidx.compose.runtime.Immutable

enum class BatchPageStatus {
    IDLE,
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Immutable
data class BatchPageItem(
    val id: String,
    val uriString: String,
    val fileName: String,
    val orderIndex: Int,
    val status: BatchPageStatus = BatchPageStatus.IDLE,
    val currentStage: PipelineStage = PipelineStage.DETECTION,
    val progress: Float = 0f,
    val stageMessage: String = "",
    val errorMessage: String? = null,
    val thumbnailPath: String? = null,
    val resultPath: String? = null,
    val historyId: Long? = null
)

@Immutable
data class BatchUiState(
    val pages: List<BatchPageItem> = emptyList(),
    val sourceLang: Language? = Language.JPN,
    val targetLang: Language = Language.ENG,
    val translatorType: TranslatorType = TranslatorType.DEEPL,
    val isProcessing: Boolean = false,
    val currentProcessingIndex: Int = -1,
    val overallProgress: Float = 0f,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val error: String? = null
)

package com.yuu18id.mangatranslator.domain.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

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
    val isReviewModeEnabled: Boolean = false,
    val isProcessing: Boolean = false,
    val currentProcessingIndex: Int = -1,
    val overallProgress: Float = 0f,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val error: String? = null,

    // Detection Review Dialog State (when review mode is enabled)
    val isShowingDetectionEditor: Boolean = false,
    val reviewImageBitmap: ImageBitmap? = null,
    val pendingDetections: List<Quadrilateral> = emptyList(),
    val reviewPageIndex: Int = -1,

    // Render Typeset Editor Dialog State (for editing a completed page)
    val isShowingRenderEditor: Boolean = false,
    val renderEditorInpaintedBitmap: ImageBitmap? = null,
    val renderEditorBlocks: List<TextBlock> = emptyList(),
    val editingHistoryId: Long? = null,
    val editingPageIndex: Int = -1
)

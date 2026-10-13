package com.yuu18id.mangatranslator.ui.batch

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.yuu18id.mangatranslator.domain.model.BatchUiState
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.Quadrilateral
import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import com.yuu18id.mangatranslator.service.BatchTranslationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class BatchViewModel @Inject constructor(
    private val batchTranslationManager: BatchTranslationManager
) : ViewModel() {

    val uiState: StateFlow<BatchUiState> = batchTranslationManager.uiState

    fun addImages(uris: List<Uri>) = batchTranslationManager.addImages(uris)

    fun movePageUp(index: Int) = batchTranslationManager.movePageUp(index)

    fun movePageDown(index: Int) = batchTranslationManager.movePageDown(index)

    fun removePage(pageId: String) = batchTranslationManager.removePage(pageId)

    fun sortByFileName() = batchTranslationManager.sortByFileName()

    fun clearAll() = batchTranslationManager.clearAll()

    fun updateAlbumName(newName: String) = batchTranslationManager.updateAlbumName(newName)

    fun setSourceLang(lang: Language?) = batchTranslationManager.setSourceLang(lang)

    fun setTargetLang(lang: Language) = batchTranslationManager.setTargetLang(lang)

    fun setTranslatorType(type: TranslatorType) = batchTranslationManager.setTranslatorType(type)

    fun toggleReviewMode() = batchTranslationManager.toggleReviewMode()

    fun confirmDetections(curatedQuads: List<Quadrilateral>) =
        batchTranslationManager.confirmDetections(curatedQuads)

    fun dismissDetectionEditor() = batchTranslationManager.dismissDetectionEditor()

    fun openRenderEditor(pageIndex: Int) = batchTranslationManager.openRenderEditor(pageIndex)

    fun dismissRenderEditor() = batchTranslationManager.dismissRenderEditor()

    fun applyEditedRender(updatedBlocks: List<TextBlock>) =
        batchTranslationManager.applyEditedRender(updatedBlocks)

    fun startBatchTranslation(singlePageIndex: Int? = null) =
        batchTranslationManager.startBatchTranslation(singlePageIndex)

    fun cancelBatchTranslation() = batchTranslationManager.cancelBatchTranslation()

    // Note: onCleared does NOT cancel the batch translation so that the background process
    // persists even when navigating away from the BatchScreen.
}

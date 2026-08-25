package com.yuu18id.mangatranslator.domain.model

sealed class PipelineState {
    data class Progress(
        val stage: PipelineStage, 
        val progress: Float, 
        val message: String = ""
    ) : PipelineState()
    
    data class Completed(
        val result: TranslationResult
    ) : PipelineState()
    
    data class Error(
        val stage: PipelineStage, 
        val message: String, 
        val cause: Throwable? = null
    ) : PipelineState()
}

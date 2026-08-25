package com.yuu18id.mangatranslator.data.ml

import com.yuu18id.mangatranslator.domain.model.TextBlock
import com.yuu18id.mangatranslator.domain.model.TranslatorConfig

interface CloudTranslator {
    suspend fun translate(textBlocks: List<TextBlock>, config: TranslatorConfig): List<TextBlock>
    fun isAvailable(): Boolean
}

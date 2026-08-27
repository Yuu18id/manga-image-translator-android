package com.yuu18id.mangatranslator.domain.repository

import com.yuu18id.mangatranslator.data.translation.model.AiModelInfo
import com.yuu18id.mangatranslator.domain.model.TranslationConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getTranslationConfig(): Flow<TranslationConfig>
    suspend fun saveTranslationConfig(config: TranslationConfig)
    
    fun getApiKey(translatorType: TranslatorType): Flow<String>
    suspend fun saveApiKey(translatorType: TranslatorType, key: String)
    
    fun getModel(translatorType: TranslatorType): Flow<String>
    suspend fun saveModel(translatorType: TranslatorType, modelId: String)
    
    fun getCachedModels(translatorType: TranslatorType): Flow<List<AiModelInfo>>
    suspend fun saveCachedModels(translatorType: TranslatorType, models: List<AiModelInfo>)
    
    fun getCustomBaseUrl(): Flow<String>
    suspend fun saveCustomBaseUrl(url: String)

    fun getOpenRouterModel(): Flow<String>
    suspend fun saveOpenRouterModel(modelId: String)
}

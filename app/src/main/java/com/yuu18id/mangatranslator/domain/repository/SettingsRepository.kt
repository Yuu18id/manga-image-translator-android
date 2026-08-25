package com.yuu18id.mangatranslator.domain.repository

import com.yuu18id.mangatranslator.domain.model.TranslationConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getTranslationConfig(): Flow<TranslationConfig>
    suspend fun saveTranslationConfig(config: TranslationConfig)
    fun getApiKey(translatorType: TranslatorType): Flow<String>
    suspend fun saveApiKey(translatorType: TranslatorType, key: String)
}

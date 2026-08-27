package com.yuu18id.mangatranslator.data.repository

import com.yuu18id.mangatranslator.data.local.SettingsDataStore
import com.yuu18id.mangatranslator.data.translation.model.AiModelInfo
import com.yuu18id.mangatranslator.data.translation.model.ModelFetcherService
import com.yuu18id.mangatranslator.domain.model.DetectorConfig
import com.yuu18id.mangatranslator.domain.model.InpaintConfig
import com.yuu18id.mangatranslator.domain.model.Language
import com.yuu18id.mangatranslator.domain.model.RenderConfig
import com.yuu18id.mangatranslator.domain.model.TranslationConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorConfig
import com.yuu18id.mangatranslator.domain.model.TranslatorType
import com.yuu18id.mangatranslator.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_TRANSLATOR_TYPE = "translator_type"
        private const val KEY_OCR_TYPE = "ocr_type"
        private const val KEY_SOURCE_LANG = "source_lang"
        private const val KEY_TARGET_LANG = "target_lang"
        private const val KEY_DETECTION_SIZE = "detection_size"
        private const val KEY_INPAINTING_SIZE = "inpainting_size"
        private const val KEY_FONT_SIZE_OFFSET = "font_size_offset"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
    }

    override fun getTranslationConfig(): Flow<TranslationConfig> {
        return settingsDataStore.preferencesFlow.map { prefs ->
            val translatorTypeName = prefs[androidx.datastore.preferences.core.stringPreferencesKey(KEY_TRANSLATOR_TYPE)] ?: TranslatorType.NONE.name
            val ocrTypeName = prefs[androidx.datastore.preferences.core.stringPreferencesKey(KEY_OCR_TYPE)] ?: com.yuu18id.mangatranslator.domain.model.OcrType.OCR_48PX_CTC.name
            val sourceLangName = prefs[androidx.datastore.preferences.core.stringPreferencesKey(KEY_SOURCE_LANG)] ?: Language.JPN.name
            val targetLangName = prefs[androidx.datastore.preferences.core.stringPreferencesKey(KEY_TARGET_LANG)] ?: Language.ENG.name
            val detectionSize = prefs[androidx.datastore.preferences.core.intPreferencesKey(KEY_DETECTION_SIZE)] ?: 1024
            val inpaintingSize = prefs[androidx.datastore.preferences.core.intPreferencesKey(KEY_INPAINTING_SIZE)] ?: 512
            val fontSizeOffset = prefs[androidx.datastore.preferences.core.intPreferencesKey(KEY_FONT_SIZE_OFFSET)] ?: 0

            val translatorType = runCatching { TranslatorType.valueOf(translatorTypeName) }.getOrDefault(TranslatorType.NONE)
            val ocrType = runCatching { com.yuu18id.mangatranslator.domain.model.OcrType.valueOf(ocrTypeName) }.getOrDefault(com.yuu18id.mangatranslator.domain.model.OcrType.OCR_48PX_CTC)
            val sourceLang = if (sourceLangName.isNotBlank()) {
                runCatching { Language.valueOf(sourceLangName) }.getOrDefault(Language.JPN)
            } else Language.JPN
            val targetLang = runCatching { Language.valueOf(targetLangName) }.getOrDefault(Language.ENG)

            TranslationConfig(
                detector = DetectorConfig(
                    detectionSize = detectionSize
                ),
                ocr = com.yuu18id.mangatranslator.domain.model.OcrConfig(
                    ocrType = ocrType
                ),
                translator = TranslatorConfig(
                    translatorType = translatorType,
                    sourceLang = sourceLang,
                    targetLang = targetLang
                ),
                inpainter = InpaintConfig(
                    inpaintingSize = inpaintingSize
                ),
                render = RenderConfig(
                    fontSizeOffset = fontSizeOffset
                )
            )
        }
    }

    override suspend fun saveTranslationConfig(config: TranslationConfig) {
        settingsDataStore.saveConfigString(KEY_TRANSLATOR_TYPE, config.translator.translatorType.name)
        settingsDataStore.saveConfigString(KEY_OCR_TYPE, config.ocr.ocrType.name)
        settingsDataStore.saveConfigString(KEY_SOURCE_LANG, config.translator.sourceLang?.name ?: "")
        settingsDataStore.saveConfigString(KEY_TARGET_LANG, config.translator.targetLang.name)
        settingsDataStore.saveConfigInt(KEY_DETECTION_SIZE, config.detector.detectionSize)
        settingsDataStore.saveConfigInt(KEY_INPAINTING_SIZE, config.inpainter.inpaintingSize)
        settingsDataStore.saveConfigInt(KEY_FONT_SIZE_OFFSET, config.render.fontSizeOffset)
    }

    override fun getApiKey(translatorType: TranslatorType): Flow<String> {
        return settingsDataStore.getApiKey(translatorType.name)
    }

    override suspend fun saveApiKey(translatorType: TranslatorType, key: String) {
        settingsDataStore.saveApiKey(translatorType.name, key)
    }

    override fun getModel(translatorType: TranslatorType): Flow<String> {
        val defaultModel = translatorType.defaultModel
        return settingsDataStore.getConfigString("model_${translatorType.name}", defaultModel)
    }

    override suspend fun saveModel(translatorType: TranslatorType, modelId: String) {
        settingsDataStore.saveConfigString("model_${translatorType.name}", modelId.trim())
    }

    override fun getCachedModels(translatorType: TranslatorType): Flow<List<AiModelInfo>> {
        val defaultList = ModelFetcherService.FALLBACK_PRESETS[translatorType] ?: emptyList()
        return settingsDataStore.getConfigString("cached_models_${translatorType.name}", "").map { jsonStr ->
            if (jsonStr.isBlank()) {
                defaultList
            } else {
                runCatching {
                    json.decodeFromString<List<AiModelInfo>>(jsonStr)
                }.getOrDefault(defaultList)
            }
        }
    }

    override suspend fun saveCachedModels(translatorType: TranslatorType, models: List<AiModelInfo>) {
        val jsonStr = json.encodeToString(models)
        settingsDataStore.saveConfigString("cached_models_${translatorType.name}", jsonStr)
    }

    override fun getCustomBaseUrl(): Flow<String> {
        return settingsDataStore.getConfigString(KEY_CUSTOM_BASE_URL, "http://localhost:11434/v1")
    }

    override suspend fun saveCustomBaseUrl(url: String) {
        settingsDataStore.saveConfigString(KEY_CUSTOM_BASE_URL, url.trim())
    }

    override fun getOpenRouterModel(): Flow<String> {
        return getModel(TranslatorType.OPENROUTER)
    }

    override suspend fun saveOpenRouterModel(modelId: String) {
        saveModel(TranslatorType.OPENROUTER, modelId)
    }
}

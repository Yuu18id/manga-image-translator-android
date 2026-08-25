package com.yuu18id.mangatranslator.data.repository

import com.yuu18id.mangatranslator.data.local.SettingsDataStore
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
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    companion object {
        private const val KEY_TRANSLATOR_TYPE = "translator_type"
        private const val KEY_SOURCE_LANG = "source_lang"
        private const val KEY_TARGET_LANG = "target_lang"
        private const val KEY_DETECTION_SIZE = "detection_size"
        private const val KEY_INPAINTING_SIZE = "inpainting_size"
        private const val KEY_FONT_SIZE_OFFSET = "font_size_offset"
    }

    override fun getTranslationConfig(): Flow<TranslationConfig> {
        return settingsDataStore.preferencesFlow.map { prefs ->
            val translatorTypeName = prefs[androidx.datastore.preferences.core.stringPreferencesKey(KEY_TRANSLATOR_TYPE)] ?: TranslatorType.NONE.name
            val sourceLangName = prefs[androidx.datastore.preferences.core.stringPreferencesKey(KEY_SOURCE_LANG)] ?: ""
            val targetLangName = prefs[androidx.datastore.preferences.core.stringPreferencesKey(KEY_TARGET_LANG)] ?: Language.ENG.name
            val detectionSize = prefs[androidx.datastore.preferences.core.intPreferencesKey(KEY_DETECTION_SIZE)] ?: 1024
            val inpaintingSize = prefs[androidx.datastore.preferences.core.intPreferencesKey(KEY_INPAINTING_SIZE)] ?: 512
            val fontSizeOffset = prefs[androidx.datastore.preferences.core.intPreferencesKey(KEY_FONT_SIZE_OFFSET)] ?: 0

            val translatorType = runCatching { TranslatorType.valueOf(translatorTypeName) }.getOrDefault(TranslatorType.NONE)
            val sourceLang = if (sourceLangName.isNotBlank()) {
                runCatching { Language.valueOf(sourceLangName) }.getOrNull()
            } else null
            val targetLang = runCatching { Language.valueOf(targetLangName) }.getOrDefault(Language.ENG)

            TranslationConfig(
                detector = DetectorConfig(
                    detectionSize = detectionSize
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
}

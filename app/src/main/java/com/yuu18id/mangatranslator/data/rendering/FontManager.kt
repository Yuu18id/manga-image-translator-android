package com.yuu18id.mangatranslator.data.rendering

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import com.yuu18id.mangatranslator.domain.model.CustomFontFamily
import com.yuu18id.mangatranslator.domain.model.CustomFontStyle
import com.yuu18id.mangatranslator.domain.model.Language
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "FontManager"
    }

    private var customFont: Typeface? = null

    val wildWordsTypeface: Typeface? by lazy {
        try {
            Typeface.createFromAsset(context.assets, "fonts/cc-wild-words-roman.ttf").also {
                Log.i(TAG, "Loaded Wild Words font from assets/fonts/cc-wild-words-roman.ttf")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Wild Words font: ${e.message}", e)
            null
        }
    }

    val badaboomTypeface: Typeface? by lazy {
        try {
            Typeface.createFromAsset(context.assets, "fonts/BADABB__.TTF").also {
                Log.i(TAG, "Loaded Badabb font from assets/fonts/BADABB__.TTF")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Badabb font: ${e.message}", e)
            null
        }
    }

    fun setCustomFont(file: File) {
        if (file.exists()) {
            customFont = Typeface.createFromFile(file)
        }
    }

    fun clearCustomFont() {
        customFont = null
    }

    fun getTypefaceForLanguage(
        language: Language,
        fontStyle: CustomFontStyle = CustomFontStyle.NORMAL,
        fontFamily: CustomFontFamily = CustomFontFamily.WILD_WORDS
    ): Typeface {
        val baseTypeface = customFont ?: when (language) {
            Language.JPN, Language.CHS, Language.CHT, Language.KOR -> {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            else -> {
                when (fontFamily) {
                    CustomFontFamily.BADABOOM -> badaboomTypeface ?: wildWordsTypeface ?: Typeface.DEFAULT_BOLD
                    CustomFontFamily.WILD_WORDS -> wildWordsTypeface ?: Typeface.DEFAULT_BOLD
                }
            }
        }

        val styleFlag = when (fontStyle) {
            CustomFontStyle.NORMAL -> Typeface.NORMAL
            CustomFontStyle.BOLD -> Typeface.BOLD
            CustomFontStyle.ITALIC -> Typeface.ITALIC
            CustomFontStyle.BOLD_ITALIC -> Typeface.BOLD_ITALIC
        }

        return Typeface.create(baseTypeface, styleFlag)
    }
}

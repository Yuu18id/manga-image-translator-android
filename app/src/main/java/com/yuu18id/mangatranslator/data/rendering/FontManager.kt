package com.yuu18id.mangatranslator.data.rendering

import android.content.Context
import android.graphics.Typeface
import android.util.Log
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
        } catch (e1: Exception) {
            try {
                Typeface.createFromAsset(context.assets, "font/cc-wild-words-roman.ttf").also {
                    Log.i(TAG, "Loaded Wild Words font from assets/font/cc-wild-words-roman.ttf")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to load Wild Words font: ${e2.message}", e2)
                null
            }
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

    fun getTypefaceForLanguage(language: Language): Typeface {
        customFont?.let { return it }

        return when (language) {
            Language.JPN, Language.CHS, Language.CHT, Language.KOR -> {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            else -> {
                wildWordsTypeface ?: Typeface.DEFAULT_BOLD
            }
        }
    }
}

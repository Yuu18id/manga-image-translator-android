package com.yuu18id.mangatranslator

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader

@HiltAndroidApp
class MangaTranslatorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initOpenCv()
    }

    private fun initOpenCv() {
        try {
            if (OpenCVLoader.initDebug()) {
                Log.d("MangaTranslatorApp", "OpenCV initialized successfully with OpenCVLoader.initDebug()")
            } else {
                Log.w("MangaTranslatorApp", "OpenCVLoader.initDebug() returned false, trying System.loadLibrary(\"opencv_java4\")...")
                System.loadLibrary("opencv_java4")
            }
        } catch (e: Throwable) {
            Log.e("MangaTranslatorApp", "Failed to initialize OpenCV", e)
            try {
                System.loadLibrary("opencv_java4")
            } catch (t: Throwable) {
                Log.e("MangaTranslatorApp", "System.loadLibrary(\"opencv_java4\") also failed", t)
            }
        }
    }
}

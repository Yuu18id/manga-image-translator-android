package com.yuu18id.mangatranslator

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

@HiltAndroidApp
class MangaTranslatorApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        initOpenCv()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024) // 128 MB disk cache
                    .build()
            }
            .crossfade(false)
            .allowHardware(false) // Disable hardware bitmaps to prevent HWUI destroyed mutex crashes in Compose
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .build()
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

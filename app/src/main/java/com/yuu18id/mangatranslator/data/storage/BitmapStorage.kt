package com.yuu18id.mangatranslator.data.storage

import android.graphics.Bitmap
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Centralized utility for persisting and compressing Bitmaps.
 * Handles platform-specific WebP format deprecations (Android 11+ WEBP_LOSSY vs legacy WEBP).
 */
object BitmapStorage {

    /**
     * Saves a [Bitmap] to [targetFile] as WebP with the specified [quality] (default 90).
     * Ensures parent directories exist prior to writing.
     */
    fun saveToWebp(bitmap: Bitmap, targetFile: File, quality: Int = 90): File {
        targetFile.parentFile?.let { dir ->
            if (!dir.exists()) dir.mkdirs()
        }

        FileOutputStream(targetFile).use { out ->
            val compressed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
            }
            if (!compressed) {
                throw IOException("Failed to compress bitmap to WebP at ${targetFile.absolutePath}")
            }
        }
        return targetFile
    }
}

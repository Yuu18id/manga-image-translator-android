package com.yuu18id.mangatranslator.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun exportImage(
        sourceFilePath: String,
        fileName: String,
        subFolder: String? = null
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val file = File(sourceFilePath)
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Source file does not exist: $sourceFilePath"))
        }

        try {
            val relativeSubPath = if (!subFolder.isNullOrBlank()) {
                val sanitized = sanitizeFolderName(subFolder)
                "Pictures/MangaTranslator/$sanitized"
            } else {
                "Pictures/MangaTranslator"
            }

            val sanitizedFileName = if (fileName.endsWith(".png", ignoreCase = true)) {
                fileName
            } else {
                "$fileName.png"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, sanitizedFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativeSubPath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(IllegalStateException("Failed to insert MediaStore record"))

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Failed to open output stream for Uri: $uri"))

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Result.success(uri)
            } else {
                // Legacy storage for Android 8.0 - 9.0 (API 26-28)
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, relativeSubPath.removePrefix("Pictures/")).apply {
                    if (!exists()) mkdirs()
                }
                val destFile = File(targetDir, sanitizedFileName)

                FileInputStream(file).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )

                Result.success(Uri.fromFile(destFile))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportBatch(
        images: List<Pair<String, String>>, // Pair<sourceFilePath, fileName>
        subFolder: String? = null
    ): Int = withContext(Dispatchers.IO) {
        var successCount = 0
        for ((sourcePath, fileName) in images) {
            val result = exportImage(sourcePath, fileName, subFolder)
            if (result.isSuccess) {
                successCount++
            }
        }
        successCount
    }

    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}

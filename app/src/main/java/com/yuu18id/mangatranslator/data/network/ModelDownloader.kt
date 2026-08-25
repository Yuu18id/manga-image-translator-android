package com.yuu18id.mangatranslator.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlin.math.pow

class ModelDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun download(
        url: String,
        targetFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
        var downloadedBytes = 0L

        if (tempFile.exists()) {
            downloadedBytes = tempFile.length()
        }

        val requestBuilder = Request.Builder().url(url)
        if (downloadedBytes > 0) {
            requestBuilder.header("Range", "bytes=$downloadedBytes-")
        }
        val request = requestBuilder.build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    return@withContext Result.failure(IOException("Unexpected code $response"))
                }

                val body = response.body ?: return@withContext Result.failure(IOException("Empty response body"))
                val totalBytes = downloadedBytes + body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(tempFile, downloadedBytes > 0).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            onProgress(downloadedBytes, totalBytes)
                        }
                        output.flush()
                    }
                }
            }

            if (tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                if (tempFile.renameTo(targetFile)) {
                    Result.success(targetFile)
                } else {
                    Result.failure(IOException("Failed to rename temp file to target file"))
                }
            } else {
                Result.failure(IOException("Downloaded file is empty"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadWithRetry(
        url: String,
        targetFile: File,
        maxRetries: Int = 3,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> {
        var currentAttempt = 0
        var lastError: Throwable? = null
        
        while (currentAttempt < maxRetries) {
            val result = download(url, targetFile, onProgress)
            if (result.isSuccess) {
                return result
            } else {
                lastError = result.exceptionOrNull()
                currentAttempt++
                if (currentAttempt < maxRetries) {
                    val backoff = 1000L * (2.0.pow(currentAttempt - 1)).toLong()
                    delay(backoff)
                }
            }
        }
        
        return Result.failure(lastError ?: IOException("Download failed after $maxRetries attempts"))
    }
}

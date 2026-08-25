package com.yuu18id.mangatranslator.data.ml

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.yuu18id.mangatranslator.data.network.ModelDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnnxModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val ortEnvironment: OrtEnvironment,
    private val modelDownloader: ModelDownloader
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "MangaTranslator"
    }

    enum class ModelType(val filename: String, val downloadUrl: String, val sizeBytes: Long) {
        CTD_DETECTOR("ctd_detector.onnx", "https://example.com/models/ctd_detector.onnx", 94_669_756),
        CTD_DETECTOR_INT8("ctd_detector_int8.onnx", "https://example.com/models/ctd_detector_int8.onnx", 53_352_863),
        OCR_CTC_48PX("ocr_ctc_48px.onnx", "https://example.com/models/ocr_ctc_48px.onnx", 163_336_212),
        OCR_CTC_48PX_INT8("ocr_ctc_48px_int8.onnx", "https://example.com/models/ocr_ctc_48px_int8.onnx", 31_208_947),
        AOT_INPAINTER("aot_inpainter.onnx", "https://example.com/models/aot_inpainter.onnx", 23_087_389),
        AOT_INPAINTER_INT8("aot_inpainter_int8.onnx", "https://example.com/models/aot_inpainter_int8.onnx", 12_000_000)
    }

    private val modelDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
    private val sessionCache = ConcurrentHashMap<ModelType, OrtSession>()

    init {
        context.registerComponentCallbacks(this)
    }

    suspend fun downloadModel(type: ModelType, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val targetFile = File(modelDir, type.filename)
        if (targetFile.exists() && targetFile.length() == type.sizeBytes) {
            onProgress(1f)
            return@withContext targetFile
        }
        val result = modelDownloader.downloadWithRetry(type.downloadUrl, targetFile, maxRetries = 3) { bytesRead, totalBytes ->
            if (totalBytes > 0) {
                onProgress(bytesRead.toFloat() / totalBytes.toFloat())
            }
        }
        result.getOrThrow()
    }

    fun isModelDownloaded(type: ModelType): Boolean {
        return getModelFile(type) != null
    }

    fun getModelFile(type: ModelType): File? {
        val file = File(modelDir, type.filename)

        // Check if bundled in app assets and extract if missing or size mismatch
        try {
            val assetFd = try { context.assets.openFd("models/${type.filename}") } catch (e: Exception) { null }
            val assetSize = assetFd?.length ?: -1L
            assetFd?.close()

            if (file.exists() && file.length() > 0) {
                if (assetSize > 0 && file.length() == assetSize) {
                    return file
                } else if (assetSize <= 0 && file.length() == type.sizeBytes) {
                    return file
                }
            }

            // Extract from assets
            android.util.Log.i(TAG, "Extracting asset model 'models/${type.filename}' to ${file.absolutePath}...")
            val tempFile = File(modelDir, "${type.filename}.tmp")
            context.assets.open("models/${type.filename}").use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                if (file.exists()) file.delete()
                tempFile.renameTo(file)
                android.util.Log.i(TAG, "Successfully extracted ${type.filename} (${file.length()} bytes)")
                return file
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not extract asset model ${type.filename}: ${e.message}")
            if (file.exists() && file.length() > 0) return file
        }

        return if (file.exists() && file.length() > 0) file else null
    }

    fun getModelSize(type: ModelType): Long {
        return getModelFile(type)?.length() ?: 0L
    }

    suspend fun deleteModel(type: ModelType) = withContext(Dispatchers.IO) {
        releaseSession(type)
        getModelFile(type)?.delete()
    }

    fun createSession(type: ModelType, useNnapi: Boolean = true): OrtSession {
        // If requesting without NNAPI, always create fresh session (don't reuse potentially NNAPI-enabled cached one)
        if (!useNnapi) {
            sessionCache.remove(type)?.close()
        } else {
            sessionCache[type]?.let { return it }
        }

        val file = getModelFile(type) ?: throw IllegalStateException("Model ${type.filename} not available in assets or storage")
        val options = configureSessionOptions(useNnapi)
        
        android.util.Log.i(TAG, "Creating ONNX Session for ${type.name} (file=${file.name}, size=${file.length() / (1024 * 1024)}MB, useNnapi=$useNnapi)")
        val session = ortEnvironment.createSession(file.absolutePath, options)
        sessionCache[type] = session
        return session
    }

    fun releaseSession(type: ModelType) {
        sessionCache.remove(type)?.close()
    }

    fun releaseAllSessions() {
        sessionCache.values.forEach { it.close() }
        sessionCache.clear()
    }

    private fun configureSessionOptions(useNnapi: Boolean): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(4)
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT)
        if (useNnapi) {
            try {
                options.addNnapi()
            } catch (e: Exception) {
                // NNAPI not available or error adding it
            }
        }
        return options
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            releaseAllSessions()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {
        releaseAllSessions()
    }
}

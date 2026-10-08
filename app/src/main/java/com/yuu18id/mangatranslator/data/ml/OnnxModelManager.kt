package com.yuu18id.mangatranslator.data.ml

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnnxModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val ortEnvironment: OrtEnvironment
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "MangaTranslator"
    }

    enum class ModelType(val filename: String) {
        CTD_DETECTOR("ctd_detector.onnx"),
        CTD_DETECTOR_INT8("ctd_detector_int8.onnx"),
        OCR_CTC_48PX("ocr_ctc_48px.onnx"),
        OCR_CTC_48PX_INT8("ocr_ctc_48px_int8.onnx"),
        AOT_INPAINTER("aot_inpainter.onnx"),
        AOT_INPAINTER_INT8("aot_inpainter_int8.onnx"),
        MANGA_OCR_ENCODER("manga_ocr_encoder.onnx"),
        MANGA_OCR_DECODER("manga_ocr_decoder.onnx")
    }

    private val modelDir = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
    private val sessionCache = ConcurrentHashMap<ModelType, OrtSession>()

    init {
        context.registerComponentCallbacks(this)
    }

    fun isModelAvailable(type: ModelType): Boolean {
        return getModelFile(type) != null
    }

    fun isModelDownloaded(type: ModelType): Boolean {
        return isModelAvailable(type)
    }

    fun getModelFile(type: ModelType): File? {
        val file = File(modelDir, type.filename)
        val extDir = context.getExternalFilesDir("models")
        val extFile = if (extDir != null) File(extDir, type.filename) else null

        if (extFile != null && extFile.exists() && extFile.length() > 0) {
            return extFile
        }

        // Check if bundled in app assets and extract if missing or size mismatch
        try {
            val assetFd = try { context.assets.openFd("models/${type.filename}") } catch (e: Exception) { null }
            val assetSize = assetFd?.length ?: -1L
            assetFd?.close()

            if (file.exists() && file.length() > 0) {
                if (assetSize > 0 && file.length() == assetSize) {
                    return file
                } else if (assetSize <= 0) {
                    return file
                }
            }

            // Extract from assets
            val assetStream = try { context.assets.open("models/${type.filename}") } catch (e: Exception) { null }
            if (assetStream != null) {
                android.util.Log.i(TAG, "Extracting asset model 'models/${type.filename}' to ${file.absolutePath}...")
                val tempFile = File(modelDir, "${type.filename}.tmp")
                assetStream.use { inputStream ->
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

    fun createSession(type: ModelType, useNnapi: Boolean = false): OrtSession {
        sessionCache[type]?.let { return it }

        synchronized(sessionCache) {
            sessionCache[type]?.let { return it }

            val file = getModelFile(type) ?: throw IllegalStateException("Model ${type.filename} not available in assets or storage")
            val options = configureSessionOptions(type, useNnapi)
            
            android.util.Log.i(TAG, "Creating ONNX Session for ${type.name} (file=${file.name}, size=${file.length() / (1024 * 1024)}MB, useNnapi=$useNnapi)")
            val session = ortEnvironment.createSession(file.absolutePath, options)
            sessionCache[type] = session
            return session
        }
    }

    fun releaseSession(type: ModelType) {
        synchronized(sessionCache) {
            sessionCache.remove(type)?.close()
        }
    }

    fun releaseAllSessions() {
        synchronized(sessionCache) {
            sessionCache.values.forEach { it.close() }
            sessionCache.clear()
        }
    }

    private fun configureSessionOptions(type: ModelType, useNnapi: Boolean): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        val availableCores = Runtime.getRuntime().availableProcessors()
        val numThreads = availableCores.coerceIn(2, 4)
        options.setIntraOpNumThreads(numThreads)
        options.setInterOpNumThreads(1)
        if (type == ModelType.MANGA_OCR_DECODER) {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            options.setMemoryPatternOptimization(false)
        } else {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
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

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        releaseAllSessions()
    }
}

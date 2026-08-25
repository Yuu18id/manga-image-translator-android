package com.yuu18id.mangatranslator.di

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import com.yuu18id.mangatranslator.data.ml.Inpainter
import com.yuu18id.mangatranslator.data.ml.OcrEngine
import com.yuu18id.mangatranslator.data.ml.OnnxModelManager
import com.yuu18id.mangatranslator.data.ml.TextDetector
import com.yuu18id.mangatranslator.data.ml.TextRenderer
import com.yuu18id.mangatranslator.data.ml.detection.CtdDetector
import com.yuu18id.mangatranslator.data.ml.inpainting.AotInpainter
import com.yuu18id.mangatranslator.data.ml.ocr.CtcOcrEngine
import com.yuu18id.mangatranslator.data.rendering.CanvasTextRenderer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MLModule {

    @Binds
    @Singleton
    abstract fun bindTextDetector(impl: CtdDetector): TextDetector

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: CtcOcrEngine): OcrEngine

    @Binds
    @Singleton
    abstract fun bindInpainter(impl: AotInpainter): Inpainter

    @Binds
    @Singleton
    abstract fun bindTextRenderer(impl: CanvasTextRenderer): TextRenderer

    companion object {
        @Provides
        @Singleton
        fun provideOrtEnvironment(): OrtEnvironment {
            return OrtEnvironment.getEnvironment()
        }

        @Provides
        @Singleton
        fun provideOnnxModelManager(
            @ApplicationContext context: Context,
            env: OrtEnvironment,
            modelDownloader: com.yuu18id.mangatranslator.data.network.ModelDownloader
        ): OnnxModelManager {
            return OnnxModelManager(context, env, modelDownloader)
        }
    }
}

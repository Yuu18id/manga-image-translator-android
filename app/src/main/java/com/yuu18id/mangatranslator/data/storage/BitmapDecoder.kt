package com.yuu18id.mangatranslator.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.IOException

object BitmapDecoder {

    /**
     * Decodes a Bitmap from the given URI with downsampling to fit within [maxDim] dimensions.
     * Backwards-compatible across all supported API levels (API 26+).
     * Uses [ImageDecoder] on API 28+ and robust [BitmapFactory] fallback on API 26-27.
     */
    fun decodeFromUri(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
                if (info.size.width > maxDim || info.size.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(info.size.width, info.size.height)
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt(),
                        (info.size.height * scale).toInt()
                    )
                }
            }
        } else {
            // Android 8.0 - 8.1 (API 26-27) fallback
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var inSampleSize = 1
            if (boundsOptions.outHeight > maxDim || boundsOptions.outWidth > maxDim) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDim || (halfWidth / inSampleSize) >= maxDim) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inMutable = true
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: throw IOException("Failed to open input stream for URI: $uri")

            if (decoded.width > maxDim || decoded.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
                val scaledW = (decoded.width * scale).toInt()
                val scaledH = (decoded.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
                if (scaled != decoded) {
                    decoded.recycle()
                }
                scaled
            } else {
                decoded
            }
        }
    }
}

package com.yuu18id.mangatranslator.di

import com.yuu18id.mangatranslator.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("x-api-key")
            redactHeader("x-goog-api-key")
            redactHeader("X-Naver-Client-Secret")
        }

        // Enforce HTTPS for all public internet traffic while permitting cleartext HTTP
        // strictly for local self-hosted models (Ollama, LM Studio on localhost / LAN IPs).
        val cleartextSecurityInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.scheme == "http" && !isLocalOrPrivateHost(url.host)) {
                throw java.io.IOException(
                    "Cleartext HTTP traffic to public host '${url.host}' is not permitted. Please use HTTPS."
                )
            }
            chain.proceed(request)
        }

        // Intelligent retry with exponential backoff for HTTP 429 (Too Many Requests),
        // 503 (Service Unavailable), and 504 (Gateway Timeout).
        val rateLimitRetryInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)
            var attempt = 0
            val maxRetries = 2

            while ((response.code == 429 || response.code == 503 || response.code == 504) && attempt < maxRetries) {
                attempt++
                val retryAfterHeader = response.header("Retry-After")
                val delayMs = if (retryAfterHeader != null) {
                    val seconds = retryAfterHeader.toLongOrNull()
                    if (seconds != null && seconds in 1..30) {
                        seconds * 1000L
                    } else {
                        1500L * attempt
                    }
                } else {
                    1500L * attempt
                }

                try {
                    android.util.Log.w(
                        "NetworkModule",
                        "HTTP ${response.code} from ${request.url.host}. Backing off for ${delayMs}ms (attempt $attempt/$maxRetries)..."
                    )
                } catch (_: Throwable) {}

                response.close()
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                response = chain.proceed(request)
            }
            response
        }

        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(cleartextSecurityInterceptor)
            .addInterceptor(rateLimitRetryInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    internal fun isLocalOrPrivateHost(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true) || host == "127.0.0.1" || host == "10.0.2.2" || host == "::1" || host == "[::1]") {
            return true
        }
        val parts = host.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val first = parts[0].toInt()
            val second = parts[1].toInt()
            if (first == 10) return true
            if (first == 192 && second == 168) return true
            if (first == 172 && second in 16..31) return true
            if (first == 127) return true
        }
        return false
    }
}

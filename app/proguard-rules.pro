# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# OpenCV
-keep class org.opencv.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.internal.** {
    *** get$serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Retrofit & OkHttp
-dontnote retrofit2.Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class com.yuu18id.mangatranslator.data.local.** { *; }
-dontwarn androidx.room.paging.**

# Domain & Local Models
-keep class com.yuu18id.mangatranslator.domain.model.** { *; }
-keep class com.yuu18id.mangatranslator.data.local.model.** { *; }
-keep class com.yuu18id.mangatranslator.data.translation.** { *; }

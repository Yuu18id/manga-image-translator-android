# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }
-keepclassmembernames class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ML Modules, Domain Models, and Engines
-keep class com.yuu18id.mangatranslator.data.ml.** { *; }
-keepclassmembers class com.yuu18id.mangatranslator.data.ml.** { *; }
-keep class com.yuu18id.mangatranslator.domain.model.** { *; }
-keepclassmembers class com.yuu18id.mangatranslator.domain.model.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.internal.** {
    *** get$serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-dontwarn kotlinx.serialization.**

# Retrofit
-dontnote retrofit2.Retrofit
-keep class retrofit2.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Native JNI Methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Strip debug and info logs in release builds to prevent user content / comic dialogue leakage
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

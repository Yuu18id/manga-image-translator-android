# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
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
-keepattributes Signature
-keepattributes Exceptions

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

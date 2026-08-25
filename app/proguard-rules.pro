# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,allowcompaction,allowobfuscation,allowshrinking class kotlinx.serialization.internal.**
-keepclassmembers class kotlinx.serialization.internal.** {
    *** get$serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Retrofit
-dontnote retrofit2.Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

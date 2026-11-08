# Keep JNI entry points and JNI-facing class names.
-keepclasseswithmembernames class * {
    native <methods>;
}

# JNI_OnLoad looks these exception classes up by their source names. Without an explicit keep
# R8 renames or merges them (for example NativeAudioException -> ch1), FindClass returns null and
# the typed native errors can no longer be thrown. Keep both names and their members; the native
# side also clears a failed lookup and falls back to a standard exception.
-keep class com.thomas.passiveeartraining.audio.NativeAudioException { *; }
-keep class com.thomas.passiveeartraining.audio.RendererClosedException { *; }

# kotlinx.serialization generates serializer companions and schema metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer {
    *** descriptor;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation 3 routes are serialized into the saved back stack.
-keepclassmembers class * implements androidx.navigation3.runtime.NavKey {
    *** Companion;
}

# -------------------------------------------------------------
# Miru Android ProGuard & R8 Configuration
# -------------------------------------------------------------

# JNI Native Methods & Rust Daemon Bridge
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class id.miru.app.NativeBridge { *; }
-keepclassmembers class id.miru.app.NativeBridge { *; }

# Data Models & Serialization
-keep class id.miru.app.data.** { *; }
-keepclassmembers class id.miru.app.data.** { *; }
# AndroidX Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Okio
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

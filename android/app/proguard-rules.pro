# Reglas de ProGuard/R8 para ParDL.
# La app no usa minificación, pero se documentan las clases críticas.

# Jackson (usado internamente por youtubedl-android)
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# youtubedl-android
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }
-keep class com.yausername.ffmpeg.** { *; }

-dontwarn org.apache.commons.**

# Coil y coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**

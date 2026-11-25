# youtubedl-android, FFmpeg JNI & reflection-based ZIP/JSON dependencies
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**

-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.io.**

-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.patodesoxigenado.nexusfetch.**$$serializer { *; }
-keepclassmembers class io.github.patodesoxigenado.nexusfetch.** {
    *** Companion;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Media3
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }

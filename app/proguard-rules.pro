# Mupa R8 / ProGuard Release Rules (Tahap 5)

# Keep Kotlinx Serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.xeno.mupa.**$$serializer { *; }
-keepclassmembers class com.xeno.mupa.** {
    *** Companion;
}
-keepclasseswithmembers class com.xeno.mupa.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Media3 Service & AudioProcessor entry points
-keep class com.xeno.mupa.playback.MupaPlaybackService { *; }
-keep class com.xeno.mupa.playback.MupaCrossfadeEngine { *; }
-keep class com.xeno.mupa.playback.eq.FiveBandEqualizerProcessor { *; }

# Keep NewPipeExtractor & Rhino JS engine reflection inside :streaming:innertube
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.schabi.newpipe.**
-dontwarn kotlin.annotations.jvm.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Optional RE2J regex engine referenced by Jsoup (transitive from NewPipeExtractor)
-dontwarn com.google.re2j.**

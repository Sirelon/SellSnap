# kotlinx.serialization — keep generated $$serializer + Companion.serializer() for our @Serializable classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.sirelon.sellsnap.**$$serializer { *; }
-keepclassmembers class com.sirelon.sellsnap.** { *** Companion; }
-keepclasseswithmembers class com.sirelon.sellsnap.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OpenAI client (com.aallam.openai) — request/response data classes are @Serializable and not covered by a consumer-rules file
-keep,includedescriptorclasses class com.aallam.openai.**$$serializer { *; }
-keepclassmembers class com.aallam.openai.** { *** Companion; }
-keepclasseswithmembers class com.aallam.openai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Noise from optional/transitive deps that R8 can't see
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# play-review-ktx's OnSuccessListener SAM adapter references this Play Services internal
# annotation, which isn't present in play-services-basement at our resolved version. It's
# annotation-only (CLASS retention) and unused at runtime, per R8's own generated suggestion.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

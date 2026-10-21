# Blink production R8 rules
# Keep useful line information so Crashlytics stack traces can be de-obfuscated.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Blink currently uses Moshi reflection for a number of Kotlin models. Keep those
# models until every adapter has been moved to generated @JsonClass adapters.
-keep class com.example.data.models.** { *; }
-keep class com.example.data.local.** { *; }

# WebRTC JNI_OnLoad resolves these Java classes by their original names.
# R8 shrinking renamed/removed them in the Android 15 release call crash.
-keep class org.webrtc.** { *; }


# Room generated implementations are discovered from the abstract database type.
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep enum members serialized by name.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Retrofit/OkHttp/Moshi rely on generic signatures and runtime annotations.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

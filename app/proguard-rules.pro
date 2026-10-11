# Blink production R8 rules
# Keep useful line information so Crashlytics stack traces can be de-obfuscated.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Blink currently uses Moshi reflection for a number of Kotlin models. Keep those
# models until every adapter has been moved to generated @JsonClass adapters.
-keep class com.example.data.models.** { *; }
-keep class com.example.data.local.** { *; }

# WebRTC's libjingle_peerconnection_so.so resolves Java classes and methods by
# their exact JNI names during PeerConnectionFactory.initialize(). R8 renamed
# these in the release build, causing a fatal GetStaticMethodID(java_class=null)
# abort on Android 15 when a voice/video call starts. Keep both classes and
# members (including WebRTC audio helpers and nested classes) for JNI safety.
-keep class org.webrtc.** { *; }

# Room generated implementations are discovered from the abstract database type.
-keep class * extends androidx.room.RoomDatabase { *; }

# Moshi's EnumJsonAdapter reflects on public enum constants using
# enumType.getField(constant.name). Keeping only values()/valueOf() is not enough:
# R8 can rename the PUBLIC field while Enum.name stays "PUBLIC", causing
# java.lang.AssertionError: Missing field in <minified enum> during app startup.
# Preserve the names of the enum constant fields that Moshi discovers.
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# A cached UserProfile contains ProfileVisibilityScope (PUBLIC/FOLLOWERS/PRIVATE).
# Keep its complete identity for JSON compatibility with previously cached data.
-keep class com.blinkng.shared.ProfileVisibilityScope { *; }

# Retrofit/OkHttp/Moshi rely on generic signatures and runtime annotations.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

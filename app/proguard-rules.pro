# Blink production R8 rules
# Keep useful line information so Crashlytics stack traces can be de-obfuscated.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Blink currently uses Moshi reflection for a number of Kotlin models. Keep those
# models until every adapter has been moved to generated @JsonClass adapters.
-keep class com.example.data.models.** { *; }
-keep class com.example.data.local.** { *; }

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

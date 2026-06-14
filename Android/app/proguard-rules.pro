# =============================================================================
# ProGuard / R8 Rules for SRCardiocare
# =============================================================================
# Production-grade obfuscation and minification rules.
# Last updated: April 2026
#
# Stack: Firebase, Kotlin Coroutines, Media3/ExoPlayer, Retrofit, OkHttp,
#        Google APIs, Jetpack Compose, EncryptedSharedPreferences
# =============================================================================


# =============================================================================
# GENERAL ANDROID RULES
# =============================================================================

# Preserve line numbers for stack traces (essential for crash reporting)
-keepattributes SourceFile,LineNumberTable

# Hide original source file names in stack traces (optional obfuscation)
-renamesourcefileattribute SourceFile

# Preserve annotation metadata (required by many libraries)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep generic signatures for Kotlin and reflection
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations


# =============================================================================
# APPLICATION DATA MODELS
# =============================================================================

# Keep all data model classes (used with Firestore, Retrofit, Gson)
-keep class com.srcardiocare.data.model.** { *; }
-keep class com.srcardiocare.data.api.** { *; }

# Keep any class with @Serializable annotation (Kotlin Serialization)
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}


# =============================================================================
# KOTLIN CORE
# =============================================================================

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.flow.**

# Kotlin Reflect (if used)
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# Kotlin intrinsics
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
    public static void throwUninitializedPropertyAccessException(...);
}


# =============================================================================
# FIREBASE SDK
# =============================================================================

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# Firebase Firestore
-keep class com.google.firebase.firestore.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
-keepclassmembers class * {
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
}
-keep class * extends com.google.firebase.firestore.DocumentReference { *; }
-dontwarn com.google.firebase.firestore.**

# Firebase Storage
-keep class com.google.firebase.storage.** { *; }
-dontwarn com.google.firebase.storage.**

# Firebase App Check
-keep class com.google.firebase.appcheck.** { *; }
-dontwarn com.google.firebase.appcheck.**

# Firebase Common
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase Crashlytics (if added later)
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception


# =============================================================================
# GOOGLE PLAY SERVICES & GOOGLE APIs
# =============================================================================

# Google Sign-In
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# Google API Client (for YouTube Data API v3)
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
-keep class com.google.http.** { *; }
-dontwarn com.google.api.**
-dontwarn com.google.api.client.**

# Preserve generic type info for Google APIs (required for JSON parsing)
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}


# =============================================================================
# RETROFIT & OKHTTP & GSON
# =============================================================================

# Retrofit
-keep class retrofit2.** { *; }
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# OkHttp platform adapters
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Okio
-keep class okio.** { *; }
-dontwarn okio.**

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}


# =============================================================================
# JETPACK COMPOSE
# =============================================================================

# Compose compiler generates classes that need to be kept
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Composable functions metadata
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}


# =============================================================================
# MEDIA3 / EXOPLAYER
# =============================================================================

# Media3 core
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ExoPlayer extensions
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}


# =============================================================================
# ANDROIDX SECURITY (EncryptedSharedPreferences)
# =============================================================================

-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn javax.annotation.**


# =============================================================================
# WEBKIT (for YouTube embed)
# =============================================================================

-keep class androidx.webkit.** { *; }
-keep class android.webkit.** { *; }


# =============================================================================
# NAVIGATION COMPOSE
# =============================================================================

-keep class * extends androidx.navigation.Navigator { *; }
-keepclassmembers class * {
    @androidx.navigation.NavDestination <fields>;
}


# =============================================================================
# LIFECYCLE & VIEWMODEL
# =============================================================================

-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
    <init>(androidx.lifecycle.SavedStateHandle);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
    <init>(android.app.Application, androidx.lifecycle.SavedStateHandle);
}


# =============================================================================
# ENUM SAFETY
# =============================================================================

# Preserve enum values (required for serialization)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# =============================================================================
# PARCELABLE & SERIALIZABLE
# =============================================================================

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}


# =============================================================================
# SUPPRESS WARNINGS
# =============================================================================

# Missing classes that are OK to ignore
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.naming.**
-dontwarn sun.misc.Unsafe
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# Apache HTTP legacy (some Google libs reference it)
-dontwarn org.apache.http.**
-dontwarn android.net.http.**


# =============================================================================
# OPTIMIZATION FLAGS
# =============================================================================

# Remove verbose/debug/info logging in release builds (keeps warnings/errors)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep R class fields (needed for resource lookup)
-keepclassmembers class **.R$* {
    public static <fields>;
}

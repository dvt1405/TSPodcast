# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# This project contains no WebView with a JavaScript interface.

# --- Crash reporting readability -------------------------------------------
# Without these, every release stack trace reaching Crashlytics is missing line
# numbers, which makes production crashes effectively undiagnosable.
# proguard-android-optimize.txt keeps these by default, but relying on that
# default is what left release traces unverifiable; state it explicitly.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Reflection-dependent attributes ---------------------------------------
# Gson resolves generics through anonymous TypeToken subclasses in
# core/storage/SharedPref.kt, core/storage/converters/PodcastTypeConverters.kt
# and sharedLibrary/utils/JsoupExt.kt. Losing Signature turns those into
# LinkedTreeMap/ClassCastException at runtime instead of a build error.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*,AnnotationDefault

-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# --- Model classes deserialized by Gson ------------------------------------
-keep class tss.t.coreradio.models.** { *; }
-keep class tss.t.coreapi.models.** { *; }
# TODO(P2): narrow to tss.t.core.storage.** + tss.t.core.models.** once the
# Signature attribute above is confirmed sufficient; this currently also keeps
# the network layer, DI modules and use cases from being shrunk.
-keep class tss.t.core.** { *; }

# AppLovin bundles the IAB OMID SDK, which optionally calls into Amazon's PrivacyPass
# attestation library. That library is not a dependency of this app, so R8 only needs to
# stop warning about the unresolved references.
-dontwarn com.amazon.privacypass.**

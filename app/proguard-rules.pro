# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class tss.t.coreradio.models.** { *; }
-keep class tss.t.coreapi.models.** { *; }
-keep class tss.t.core.** { *; }
# AppLovin bundles the IAB OMID SDK, which optionally calls into Amazon's PrivacyPass
# attestation library. That library is not a dependency of this app, so R8 only needs to
# stop warning about the unresolved references.
-dontwarn com.amazon.privacypass.**

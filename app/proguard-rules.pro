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

# Keep all classes, methods, and fields that contain "shizuku" or "rikka"
-keep class *.*shizuku** { *; }
-keep class *.*rikka** { *; }

# Keep any packages that contain "shizuku" or "rikka"
-keep class *shizuku.** { *; }
-keep class *rikka.** { *; }

# Keep all methods that reference "shizuku" or "rikka"
-keepclassmembers class * {
    * shizuku*;
    * rikka*;
}

# Keep annotations with "shizuku" or "rikka"
-keepattributes *Annotation*

# Keep native methods if any exist
-keepclasseswithmembernames class * {
    native * shizuku* (...);
    native * rikka* (...);
}

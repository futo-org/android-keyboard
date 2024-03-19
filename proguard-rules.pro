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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclasseswithmembers class * {
    native <methods>;
}
# Keep classes and methods that have the @UsedForTesting annotation
-keep @org.futo.inputmethod.annotations.UsedForTesting class *
-keepclassmembers class * {
    @org.futo.inputmethod.annotations.UsedForTesting *;
}

# Keep classes and methods that have the @ExternallyReferenced annotation
-keep @org.futo.inputmethod.annotations.ExternallyReferenced class *
-keepclassmembers class * {
    @org.futo.inputmethod.annotations.ExternallyReferenced *;
}

# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class org.futo.inputmethod.latin.AssetFileAddress
-keep class org.futo.inputmethod.latin.Dictionary
-keep class org.futo.inputmethod.latin.NgramContext
-keep class org.futo.inputmethod.latin.makedict.ProbabilityInfo
-keep class org.futo.inputmethod.keyboard.KeyboardLayout { *; }

-keep class org.tensorflow.lite.Interpreter** { *; }
-keep class org.futo.pocketfft.** { *; }

-dontobfuscate
-optimizations !code/allocation/variable
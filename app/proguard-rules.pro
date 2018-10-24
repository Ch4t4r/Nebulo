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


# Some pcap4j related proguard rules
-dontwarn org.slf4j.impl.*
-keep class org.slf4j.** {
    *;
}
-keep class org.pcap4j.** {
    *;
}
-dontwarn java.awt.*
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Removes debug prints of packet4j
-assumenosideeffects class org.slf4j.Logger {
    public void debug(...);
    public void trace(...);
}

# The AppCompatViewInflater is nowhere directly referenced and thus would be removed if it wasn't explicitly kept here
-keep class androidx.appcompat.app.AppCompatViewInflater {
    *;
}

# okhttp related, https://github.com/square/okhttp/blob/5fe3cc2d089810032671d6135ad137af6f491d28/README.md#proguard
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# retrofit related, https://github.com/square/retrofit/blob/master/retrofit/src/main/resources/META-INF/proguard/retrofit2.pro
-keepattributes Signature, InnerClasses
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn kotlin.Unit
-dontwarn retrofit2.-KotlinExtensions

# Keep all of our own classes
-keep class com.frostnerd.smokescreen.** { *; }

# asyncawait related, we are not using retrofit or rxjava
-dontwarn rx.Observable
-dontwarn rx.observables.BlockingObservable
-dontwarn retrofit2.Call
-dontwarn retrofit2.Response
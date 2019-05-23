-optimizationpasses 5
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn org.slf4j.impl.*
-dontwarn javax.naming.*
-dontwarn javax.servlet.http.*
-dontwarn javax.servlet.*
-dontwarn java.awt.*
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Removes debug prints of packet4j
-assumenosideeffects class org.slf4j.Logger {
    public void debug(...);
    public void trace(...);
}

-assumenosideeffects class java.io.PrintStream {
     public void println(%);
     public void println(**);
 }

-assumenosideeffects class android.util.Log {
     public static *** d(...);
     public static *** v(...);
}

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
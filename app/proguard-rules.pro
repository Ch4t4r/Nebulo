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

-assumenosideeffects class java.io.PrintStream {
     public void println(%);
     public void println(**);
 }

-assumenosideeffects class android.util.Log {
     public static *** d(...);
     public static *** v(...);
}

-assumenosideeffects class com.frostnerd.vpntunnelproxy.Logger {
        public final void finer(...);
        public final void finest(...);
}

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
# Ensure the custom, fast service loader implementation is removed.
-assumevalues class kotlinx.coroutines.internal.MainDispatcherLoader {
  boolean FAST_SERVICE_LOADER_ENABLED return false;
}
-checkdiscard class kotlinx.coroutines.internal.FastServiceLoader
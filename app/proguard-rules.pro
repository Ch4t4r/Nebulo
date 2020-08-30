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
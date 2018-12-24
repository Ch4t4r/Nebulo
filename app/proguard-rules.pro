-optimizationpasses 5
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

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
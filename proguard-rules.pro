# Kern R8/ProGuard rules.
# Baseline only - tightened during 0.1.6.0 (alpha integration + polish).

# Apache POI relies on reflection and pulls in optional classes that are not
# present on Android. Keep its public API and silence missing-class warnings.
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.w3c.dom.**
-dontwarn javax.xml.**
-dontwarn java.awt.**

# OpenCSV uses reflection for bean binding.
-keep class com.opencsv.** { *; }
-dontwarn com.opencsv.**

# Kotlin metadata.
-keepattributes *Annotation*, InnerClasses, Signature

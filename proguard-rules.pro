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
-dontwarn com.microsoft.schemas.**
-dontwarn org.w3c.dom.**
-dontwarn javax.xml.**
-dontwarn java.awt.**

# POI reaches these through optional transitive dependencies that have no
# implementation on the Android runtime: no slf4j binding, no OSGi container and
# no XZ or Zstandard codec are on the classpath, and the FindBugs and bnd
# annotations are compile time only. A -keep does not silence a dangling
# reference, only -dontwarn does, so these are the rules that let R8 finish.
-dontwarn org.slf4j.**
-dontwarn org.osgi.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn org.tukaani.xz.**
-dontwarn aQute.bnd.annotation.**
-dontwarn com.github.luben.zstd.**

# OpenCSV uses reflection for bean binding.
-keep class com.opencsv.** { *; }
-dontwarn com.opencsv.**

# Kotlin metadata.
-keepattributes *Annotation*, InnerClasses, Signature

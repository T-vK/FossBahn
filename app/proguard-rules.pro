-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.json.** { *; }
-dontwarn org.osmdroid.**
# Cronet (Chromium TLS for Bahn/Akamai)
-keep class org.chromium.net.** { *; }
-keep class com.google.net.cronet.** { *; }
-dontwarn org.chromium.net.**

# Compose
-keepclassmembers class * extends androidx.compose.runtime.RecomposeScope { *; }

# OsmDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Health Connect
-keep class androidx.health.connect.** { *; }
-dontwarn androidx.health.connect.**

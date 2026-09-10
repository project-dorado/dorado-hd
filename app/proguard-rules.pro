# Keep Media3 session service wiring.
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
# Room entities are accessed reflectively by generated code.
-keep class com.heretek.dorado_hd.data.db.** { *; }
-dontwarn org.checkerframework.**

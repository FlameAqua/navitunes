# ============================================================
# Kotlinx Serialization
# The library ships consumer rules, but we pin our own model
# package explicitly so R8 never strips the generated serializers.
# ============================================================
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod

# Keep `serializer()` lookups, companions, and generated $$serializer classes.
-keepclassmembers class ie.adrianszydlo.navitunes.** {
    *** Companion;
}
-keepclasseswithmembers class ie.adrianszydlo.navitunes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ie.adrianszydlo.navitunes.**$$serializer { *; }

# Keep every @Serializable model and its fields outright — these are tiny DTOs
# and losing a field name to obfuscation would silently break JSON parsing.
-keep @kotlinx.serialization.Serializable class ie.adrianszydlo.navitunes.** { *; }
-dontnote kotlinx.serialization.**

# ============================================================
# Retrofit / OkHttp / Okio
# ============================================================
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ============================================================
# Media3 / ExoPlayer
# ============================================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================================
# Room
# ============================================================
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ============================================================
# WorkManager — DownloadWorker is instantiated reflectively.
# A stripped worker means downloads silently never run in release.
# ============================================================
-keep class ie.adrianszydlo.navitunes.data.offline.DownloadWorker { <init>(...); }

# ============================================================
# Coroutines
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================================
# Compose — tooling already provides rules; keep runtime safe.
# ============================================================
-dontwarn androidx.compose.**

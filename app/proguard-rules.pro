# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializersKt

-keep,includedescriptorclasses class ie.adrianszydlo.navitunes.**$$serializer { *; }
-keepclassmembers class ie.adrianszydlo.navitunes.** {
    *** Companion;
}
-keepclasseswithmembers class ie.adrianszydlo.navitunes.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Signature, Exceptions

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-keep class androidx.compose.** { *; }

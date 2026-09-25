# Default Android ProGuard rules apply via proguard-android-optimize.txt.

# Keep Compose runtime metadata.
-keepclassmembers class androidx.compose.** { *; }

# Telephony / SMS framework classes referenced by reflection in some OEM ROMs.
-keep class android.provider.Telephony** { *; }

# Keep our manifest-registered components (receivers, services, activities).
-keep class com.liquidglass.messages.data.receiver.** { *; }
-keep class com.liquidglass.messages.data.service.** { *; }

# Kotlin coroutines.
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# osmdroid uses reflection only for optional providers.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Play Billing is accessed through the SDK's own public API.
-keep class com.android.billingclient.** { *; }

# Kotlin serialization generated serializers for the scoring configuration.
-keepclassmembers class app.ecorota.core.scoring.** {
    *** Companion;
}
-keepclasseswithmembers class app.ecorota.core.scoring.** {
    kotlinx.serialization.KSerializer serializer(...);
}

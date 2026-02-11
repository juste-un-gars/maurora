# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard configuration.

# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.franck.aurorawidget.**$$serializer { *; }
-keepclassmembers class com.franck.aurorawidget.** {
    *** Companion;
}
-keepclasseswithmembers class com.franck.aurorawidget.** {
    kotlinx.serialization.KSerializer serializer(...);
}

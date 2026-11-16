# kotlinx.serialization: keep generated serializers for the protocol models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.itguyvv.codecockpitremote.**$$serializer { *; }
-keepclassmembers class io.github.itguyvv.codecockpitremote.** { *** Companion; }
-keepclasseswithmembers class io.github.itguyvv.codecockpitremote.** { kotlinx.serialization.KSerializer serializer(...); }

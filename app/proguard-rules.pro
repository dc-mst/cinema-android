# kotlinx.serialization ships its own consumer rules; keep our DTOs' serializers explicit anyway.
-keep,includedescriptorclasses class com.devcrumbs.cinema.data.**$$serializer { *; }
-keepclassmembers class com.devcrumbs.cinema.data.** {
    *** Companion;
}

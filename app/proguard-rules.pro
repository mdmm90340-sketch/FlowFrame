# yt-dlp's model objects are populated by Gson inside the library.
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Apache Commons Compress registers ZIP extra fields by Class.newInstance(). R8 cannot
# see those constructor calls and otherwise removes them from minified release builds.
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}

# kotlinx.serialization generates serializers referenced by name.
-keep,includedescriptorclasses class com.flowframe.app.**$$serializer { *; }
-keepclassmembers class com.flowframe.app.** {
    *** Companion;
}

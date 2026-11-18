-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class com.kitchensync360.app.model.** { *; }

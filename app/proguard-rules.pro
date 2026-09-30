# 保持 kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.dsh.burnalarm.** {
    *** Companion;
}
-keepclasseswithmembers class com.dsh.burnalarm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

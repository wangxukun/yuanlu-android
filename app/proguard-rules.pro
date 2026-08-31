# yuanlu-android R8 混淆规则基线（当前 minifyEnabled=false，开启混淆前必须保留）

# ---- kotlinx.serialization ----
# @Serializable DTO 的生成的 serializer 不能被混淆/移除，否则运行时反序列化失败
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.wxkzd.yuanlu.**$$serializer { *; }
-keepclassmembers class com.wxkzd.yuanlu.** {
    *** Companion;
}
-keepclasseswithmembers class com.wxkzd.yuanlu.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Retrofit（反射读取方法签名与注解）----
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# ---- OkHttp / OkIO（可选依赖的告警抑制）----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

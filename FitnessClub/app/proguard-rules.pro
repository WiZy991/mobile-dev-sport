# R8 / ProGuard — release minify (Google Play: DEX obfuscation / optimization)

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ——— Retrofit ———
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ——— Gson (поля с @SerializedName; имена классов можно обфусцировать) ———
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# DTO для API — reflection Gson + Retrofit
-keep class com.fitnessclub.app.data.model.** { <fields>; }
-keep class com.fitnessclub.app.data.api.** { <fields>; }
-keepclassmembers class com.fitnessclub.app.data.repository.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Дневник тренировок лежит на телефоне JSON-ом с именами полей без @SerializedName:
# переименование полей R8 = старые записи не читаются и затираются.
-keep class com.fitnessclub.app.data.local.DiaryExercise { <fields>; <init>(...); }
-keep class com.fitnessclub.app.data.local.TrainingDiaryEntry { <fields>; <init>(...); }
-keepclassmembers class com.fitnessclub.app.ui.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson TypeToken (R8 full mode срезает generic-сигнатуру анонимного подкласса)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep,allowobfuscation class * extends com.google.gson.reflect.TypeToken

# Retrofit 2.9 + suspend + R8 full mode (правила из retrofit2.pro 2.11)
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ——— Hilt / Dagger (правила из библиотеки; Application нужен multidex) ———
-keep class com.fitnessclub.app.FitnessClubApp { *; }
-keep class com.fitnessclub.app.Hilt_FitnessClubApp { *; }

# ——— Firebase Messaging ———
-keep class com.fitnessclub.app.push.FitnessMessagingService { *; }
-dontwarn com.google.firebase.**

# ——— osmdroid ———
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ——— ZXing ———
-keep class com.google.zxing.** { *; }

# ——— OkHttp / platform ———
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Enums used in JSON
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# kotlinx.serialization の生成クラスを残す
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class jp.miyavi.android.sumahoclaw.** { kotlinx.serialization.KSerializer serializer(...); }
# Tink は内部でリフレクションを使う
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.errorprone.annotations.**

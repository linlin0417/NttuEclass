# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\...\AppData\Local\Android\Sdk/tools/proguard/proguard-android-optimize.txt

# Keep Kotlin metadata and annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class tw.edu.irika.nttueclass.data.local.db.entity.** { *; }
-keep interface tw.edu.irika.nttueclass.data.local.db.dao.** { *; }

# Keep WorkManager Workers
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# Keep Jsoup
-keep public class org.jsoup.** { public *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Keep Google Play Billing & Integrity
-keep class com.android.billingclient.api.** { *; }
-keep class com.google.android.play.core.** { *; }

# Keep Pass Data Models & Crypto
-keep class tw.edu.irika.nttueclass.pass.crypto.** { *; }

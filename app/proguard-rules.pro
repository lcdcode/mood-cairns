-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.slf4j.**

# Tink (via androidx.security.crypto) references compile-time-only annotations
# from error-prone and j2objc that aren't on the runtime classpath. Safe to drop.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**

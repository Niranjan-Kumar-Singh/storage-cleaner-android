# Default & Release R8 Optimization Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class androidx.compose.** { *; }
-keep class com.example.storagecleaner.data.ScanReminderReceiver { *; }
-keep class com.example.storagecleaner.data.BootReminderReceiver { *; }
-dontwarn kotlin.**
-dontwarn javax.annotation.**

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class id.rwi.scanpdfin.data.** { *; }
# PdfBox-Android memakai refleksi untuk beberapa filter/font
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
# OpenCV memanggil kelas Java dari kode native
-keep class org.opencv.** { *; }

# Page One keeps its own classes intact (small, and avoids any reflection surprises).
-keep class com.amit.pageone.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
# Firestore / Google Play services ship their own consumer rules.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# PdfBox-Android: optional JPEG2000 codec isn't bundled
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }

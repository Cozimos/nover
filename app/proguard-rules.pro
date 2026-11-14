# TextForge release shrinker rules.
# Keep runtime annotations and generic signatures used by Android/Kotlin libraries.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Preserve methods exposed to Android WebView JavaScript, if any are added later.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

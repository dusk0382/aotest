# AO3 Lector - proguard rules
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# The reader's WebView JS bridge (toggleChrome / onJsFindResult) is injected
# with @JavascriptInterface; R8 must keep those methods or release builds
# silently lose the reader search counter and tap-to-hide-chrome.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

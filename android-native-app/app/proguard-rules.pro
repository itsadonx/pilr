# Keep JavascriptInterface for WebView
-keepclassmembers class com.pilr.entrancepass.PrintJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

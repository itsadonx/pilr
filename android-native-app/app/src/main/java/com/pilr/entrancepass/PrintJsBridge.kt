package com.pilr.entrancepass

import android.webkit.JavascriptInterface
import androidx.appcompat.app.AppCompatActivity

/**
 * Exposed to JavaScript as [AndroidPrint].printHtml(html) — direct path to [PrintManager], no Capacitor.
 */
class PrintJsBridge(private val activity: AppCompatActivity) {

    @JavascriptInterface
    fun printHtml(html: String) {
        if (html.isBlank()) return
        HtmlPrintHelper.printHtml(activity, html, "Entrance Pass")
    }
}

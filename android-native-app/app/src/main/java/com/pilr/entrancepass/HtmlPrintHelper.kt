package com.pilr.entrancepass

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Renders HTML in an attached [WebView] and opens Android [PrintManager] — same approach as system browser print.
 */
object HtmlPrintHelper {

    /** ISO A6 portrait (105×148 mm) in mils — use explicit ctor; [MediaSize.ISO_A6] is not on all SDK stubs. */
    private val MEDIA_A6_PORTRAIT: PrintAttributes.MediaSize =
        PrintAttributes.MediaSize("ISO_A6", "ISO A6", 4134, 5827)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingWebView: WebView? = null
    private var pendingParent: ViewGroup? = null

    private fun removePending() {
        val w = pendingWebView
        val p = pendingParent
        pendingWebView = null
        pendingParent = null
        if (w != null && p != null) {
            try {
                p.removeView(w)
                w.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun printHtml(activity: Activity, html: String, jobName: String) {
        activity.runOnUiThread {
            removePending()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                WebView.enableSlowWholeDocumentDraw()
            }

            val webView = WebView(activity)
            val ws = webView.settings
            ws.javaScriptEnabled = false
            ws.loadsImagesAutomatically = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            webView.visibility = View.INVISIBLE
            root.addView(
                webView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            pendingWebView = webView
            pendingParent = root

            val started = booleanArrayOf(false)

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    mainHandler.postDelayed({
                        if (started[0]) return@postDelayed
                        started[0] = true
                        try {
                            val wMeasure = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
                            val hMeasure = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                            webView.measure(wMeasure, hMeasure)
                            webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)

                            val pm = activity.getSystemService(Activity.PRINT_SERVICE) as? PrintManager
                            if (pm == null) {
                                removePending()
                                return@postDelayed
                            }
                            val docName = "${jobName}_document"
                            val adapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(docName)
                            val attrs = try {
                                PrintAttributes.Builder()
                                    .setMediaSize(MEDIA_A6_PORTRAIT)
                                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                    .build()
                            } catch (_: Exception) {
                                PrintAttributes.Builder().build()
                            }
                            pm.print(jobName, adapter, attrs)
                        } catch (_: Exception) {
                            removePending()
                        }
                    }, 800)
                }
            }

            webView.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
        }
    }
}

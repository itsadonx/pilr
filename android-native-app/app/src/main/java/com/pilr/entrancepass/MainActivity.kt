package com.pilr.entrancepass

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_BT_PRINT = 10042
    }

    private lateinit var webView: WebView

    /**
     * @return true if Bluetooth permissions for direct thermal print are already granted (or not required on this API).
     * On API 31+ requests **BLUETOOTH_CONNECT** and **BLUETOOTH_SCAN** (needed for cancelDiscovery + RFCOMM).
     */
    fun ensureBluetoothConnectPermission(): Boolean {
        return ensureBluetoothPrintPermissions()
    }

    fun ensureBluetoothPrintPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (need.isEmpty()) {
            return true
        }
        ActivityCompat.requestPermissions(this, need.toTypedArray(), REQ_BT_PRINT)
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_BT_PRINT || grantResults.isEmpty()) {
            return
        }
        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (allGranted) {
            Toast.makeText(this, "Bluetooth allowed. Tap Refresh printer list.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Allow Nearby devices / Bluetooth permissions for direct print.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.allowFileAccessFromFileURLs = true
        s.allowUniversalAccessFromFileURLs = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.setSupportZoom(true)
        s.builtInZoomControls = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        webView.addJavascriptInterface(PrintJsBridge(this), "AndroidPrint")

        webView.loadUrl("file:///android_asset/www/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}

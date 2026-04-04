package com.pilr.entrancepass

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exposed to JavaScript as [AndroidPrint].
 * [printHtml] uses Default (system PrintManager) or Direct (Bluetooth ESC/POS) based on Settings → Printer.
 */
class PrintJsBridge(private val activity: AppCompatActivity) {

    @JavascriptInterface
    fun printHtml(html: String, jobName: String?) {
        printHtml(html, jobName, "")
    }

    /**
     * [thermalPlain] preformatted receipt lines (same layout as modal). Pass "" when unused.
     * Direct Bluetooth print uses this so output matches the on-screen receipt (no stray HTML title text).
     */
    @JavascriptInterface
    fun printHtml(html: String, jobName: String?, thermalPlain: String) {
        if (html.isBlank()) return
        val job = jobName?.trim()?.takeIf { it.isNotEmpty() && it != "undefined" } ?: "Entrance Pass"
        val tp = thermalPlain.trim().takeIf { it.isNotEmpty() && it != "undefined" }
        activity.runOnUiThread {
            if (PrinterPreferences.useDirectPrint(activity)) {
                val mac = PrinterPreferences.getPrinterMac(activity)
                if (mac.isBlank()) {
                    Toast.makeText(
                        activity,
                        "Direct print is on but no printer is selected. Open Settings → Printer.",
                        Toast.LENGTH_LONG
                    ).show()
                    HtmlPrintHelper.printHtml(activity, html, job)
                    return@runOnUiThread
                }
                if (!BluetoothPermissionHelper.hasBluetoothPrintPermission(activity)) {
                    if (activity is MainActivity) {
                        activity.ensureBluetoothPrintPermissions()
                    }
                    Toast.makeText(
                        activity,
                        "Allow Nearby devices / Bluetooth, then print again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }
                ThermalPrintHelper.printToThermal(activity, mac, html, tp)
            } else {
                HtmlPrintHelper.printHtml(activity, html, job)
            }
        }
    }

    @JavascriptInterface
    fun getPrinterSettingsJson(): String {
        return try {
            JSONObject().apply {
                put("directPrint", PrinterPreferences.useDirectPrint(activity))
                put("printerAddress", PrinterPreferences.getPrinterMac(activity))
                put("printerName", PrinterPreferences.getPrinterName(activity))
            }.toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    @JavascriptInterface
    fun setPrinterSettings(directPrint: String, printerAddress: String, printerName: String) {
        val direct = directPrint == "true" || directPrint == "1"
        PrinterPreferences.setDirectPrint(activity, direct)
        val mac = printerAddress.trim().takeIf { it.isNotEmpty() && it != "undefined" } ?: ""
        val name = printerName.trim().takeIf { it.isNotEmpty() && it != "undefined" } ?: ""
        if (mac.isNotEmpty()) {
            PrinterPreferences.setPrinter(activity, mac, name)
        }
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun getBondedPrintersJson(): String {
        return try {
            if (!BluetoothPermissionHelper.hasBluetoothPrintPermission(activity)) {
                return JSONObject().put("error", "bluetooth_permission").toString()
            }
            val adapter = (activity.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: return JSONObject().put("error", "no_bluetooth").toString()
            if (!adapter.isEnabled) {
                return JSONObject().put("error", "bluetooth_off").toString()
            }
            val arr = JSONArray()
            adapter.bondedDevices
                ?.sortedWith(compareBy({ it.name ?: "" }, { it.address }))
                ?.forEach { d ->
                    arr.put(
                        JSONObject().apply {
                            put("name", d.name ?: "Unknown device")
                            put("address", d.address)
                        }
                    )
                }
            JSONObject().put("printers", arr).toString()
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: "unknown").toString()
        }
    }

    @JavascriptInterface
    fun requestBluetoothConnectPermission() {
        activity.runOnUiThread {
            if (activity is MainActivity) {
                activity.ensureBluetoothPrintPermissions()
            }
        }
    }

    /** Stable per-device id (survives app reinstall). Used with Firebase to restore device number. */
    @JavascriptInterface
    fun getAndroidStableDeviceId(): String {
        return try {
            Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID)?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}

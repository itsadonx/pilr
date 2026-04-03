package com.pilr.entrancepass

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.util.UUID

/**
 * Exposed to JavaScript as [AndroidPrint].printHtml(html) — direct path to [PrintManager], no Capacitor.
 */
class PrintJsBridge(private val activity: AppCompatActivity) {

    companion object {
        // Standard SerialPortService ID used by most ESC/POS bluetooth printers.
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @JavascriptInterface
    fun printHtml(html: String) {
        if (html.isBlank()) return
        HtmlPrintHelper.printHtml(activity, html, "Entrance Pass")
    }

    @JavascriptInterface
    fun listPairedBluetoothPrinters(): String {
        val out = JSONObject()
        try {
            if (!hasBluetoothPermission()) {
                out.put("ok", false)
                out.put("error", "permission_required")
                return out.toString()
            }

            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                out.put("ok", false)
                out.put("error", "bluetooth_not_supported")
                return out.toString()
            }

            val arr = JSONArray()
            val bonded: Set<BluetoothDevice> = adapter.bondedDevices ?: emptySet()
            bonded.forEach { d ->
                if (!d.address.isNullOrBlank()) {
                    val item = JSONObject()
                    item.put("name", d.name ?: "Bluetooth Printer")
                    item.put("address", d.address)
                    arr.put(item)
                }
            }
            out.put("ok", true)
            out.put("printers", arr)
        } catch (e: Exception) {
            out.put("ok", false)
            out.put("error", e.message ?: "unknown_error")
        }
        return out.toString()
    }

    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun printEscPosText(address: String, text: String): String {
        val out = JSONObject()
        var socket: BluetoothSocket? = null
        var stream: OutputStream? = null
        try {
            if (address.isBlank() || text.isBlank()) {
                out.put("ok", false)
                out.put("error", "invalid_arguments")
                return out.toString()
            }
            if (!hasBluetoothPermission()) {
                out.put("ok", false)
                out.put("error", "permission_required")
                return out.toString()
            }

            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                out.put("ok", false)
                out.put("error", "bluetooth_not_supported")
                return out.toString()
            }
            val device = adapter.getRemoteDevice(address)
            adapter.cancelDiscovery()

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            stream = socket.outputStream

            // ESC/POS init
            stream.write(byteArrayOf(0x1B, 0x40))
            stream.write(text.toByteArray(Charsets.UTF_8))
            // Feed and cut (many printers support this)
            stream.write(byteArrayOf(0x0A, 0x0A, 0x0A))
            stream.write(byteArrayOf(0x1D, 0x56, 0x00))
            stream.flush()

            out.put("ok", true)
            return out.toString()
        } catch (e: Exception) {
            out.put("ok", false)
            out.put("error", e.message ?: "print_failed")
            return out.toString()
        } finally {
            try { stream?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

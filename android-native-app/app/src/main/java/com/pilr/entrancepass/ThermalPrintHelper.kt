package com.pilr.entrancepass

import android.app.Activity
import android.content.Context
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.widget.Toast
import androidx.core.text.HtmlCompat
import java.io.BufferedOutputStream
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.min

/**
 * Sends receipt/report content to a paired Bluetooth thermal printer as plain text over SPP (ESC/POS).
 * Many 58mm/80mm ESC/POS printers work with UUID 1101; layout is simplified vs HTML (no images/barcode graphics).
 */
object ThermalPrintHelper {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** ~42 columns for 58mm; adjust if text wraps too early on 80mm. */
    private const val LINE_WIDTH = 42

    private fun bluetoothAdapter(activity: Activity): BluetoothAdapter? {
        val bm = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter
    }

    fun printHtmlToThermal(activity: Activity, html: String, macAddress: String) {
        if (macAddress.isBlank()) {
            activity.runOnUiThread {
                Toast.makeText(
                    activity,
                    "No default Bluetooth printer selected. Choose one in Settings → Printer.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        thread(name = "pilr-thermal-print") {
            var socket: BluetoothSocket? = null
            try {
                val adapter = bluetoothAdapter(activity)
                if (adapter == null || !adapter.isEnabled) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Bluetooth is off. Turn it on or switch to Default print in Settings.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@thread
                }

                val device = adapter.bondedDevices?.firstOrNull {
                    it.address.equals(macAddress, ignoreCase = true)
                }
                if (device == null) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Printer not found among paired devices. Pair it in Android Bluetooth settings.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@thread
                }

                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                try {
                    adapter.cancelDiscovery()
                } catch (_: SecurityException) {
                    // Should not happen if BLUETOOTH_SCAN is granted; continue to connect()
                }
                socket.connect()

                val out = BufferedOutputStream(socket.outputStream)
                out.write(byteArrayOf(0x1B, 0x40)) // ESC @ init
                out.write(byteArrayOf(0x1B, 0x61, 0x00)) // left align

                val plain = htmlToPlainText(html)
                val lines = plain.split('\n').flatMap { wrapLine(it, LINE_WIDTH) }
                for (line in lines) {
                    out.write((line + "\n").toByteArray(Charsets.UTF_8))
                }
                out.write("\n\n".toByteArray(Charsets.UTF_8))
                out.write(byteArrayOf(0x1D, 0x56, 0x00)) // full cut (common ESC/POS)
                out.flush()

                activity.runOnUiThread {
                    Toast.makeText(activity, "Sent to printer", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Bluetooth print failed: ${e.message ?: "unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun htmlToPlainText(html: String): String {
        val noScript = html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        val spanned = HtmlCompat.fromHtml(noScript, HtmlCompat.FROM_HTML_MODE_LEGACY)
        return spanned.toString()
            .replace('\u00A0', ' ')
            .replace(Regex("\r\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun wrapLine(line: String, width: Int): List<String> {
        val t = line.trimEnd()
        if (t.isEmpty()) return listOf("")
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < t.length) {
            chunks.add(t.substring(i, min(i + width, t.length)))
            i += width
        }
        return chunks
    }
}

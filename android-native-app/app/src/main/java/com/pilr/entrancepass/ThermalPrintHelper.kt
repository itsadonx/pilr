package com.pilr.entrancepass

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.widget.Toast
import androidx.core.text.HtmlCompat
import java.io.BufferedOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.min

/**
 * Bluetooth thermal ESC/POS over SPP. Preformatted plain text (from JS) matches the on-screen receipt;
 * HTML fallback uses word wrap. Output uses IBM437 so amounts/currency print reliably (UTF-8 ₱ garbles on many printers).
 */
object ThermalPrintHelper {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val CP437: Charset = Charset.forName("IBM437")

    /** ~42 columns for 58mm; HTML fallback wrap only. */
    private const val HTML_FALLBACK_WRAP_WIDTH = 42

    private fun bluetoothAdapter(activity: Activity): BluetoothAdapter? {
        val bm = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter
    }

    /**
     * @param html full print document (used if [preformattedPlain] is null/blank)
     * @param preformattedPlain lines built in JS to mirror the receipt modal (no HTML title noise)
     */
    fun printToThermal(
        activity: Activity,
        macAddress: String,
        html: String,
        preformattedPlain: String?
    ) {
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

        val plainBody = preformattedPlain?.trim()?.takeIf { it.isNotEmpty() }
        val sourceText = plainBody ?: htmlToPlainTextForThermal(html)
        val wrapWidth = if (plainBody != null) null else HTML_FALLBACK_WRAP_WIDTH

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

                val device = adapter.bondedDevices?.firstOrNull { dev ->
                    dev.address.equals(macAddress, ignoreCase = true)
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
                socket.connect()

                val out = BufferedOutputStream(socket.outputStream)
                out.write(byteArrayOf(0x1B, 0x40)) // ESC @ init
                out.write(byteArrayOf(0x1B, 0x74, 0x00)) // ESC t 0 — PC437 / CP437
                out.write(byteArrayOf(0x1B, 0x61, 0x00)) // left align

                for (line in sourceText.split('\n')) {
                    val sanitized = prepareLineForThermal(line)
                    val chunks = if (wrapWidth != null) wordWrapLine(sanitized, wrapWidth) else listOf(sanitized)
                    for (chunk in chunks) {
                        out.write(encodeCp437(chunk + "\n"))
                    }
                }
                out.write(encodeCp437("\n\n"))
                out.write(byteArrayOf(0x1D, 0x56, 0x00)) // full cut
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

    /** @deprecated path — use [printToThermal] with preformatted plain from JS. */
    fun printHtmlToThermal(activity: Activity, html: String, macAddress: String) {
        printToThermal(activity, macAddress, html, null)
    }

    private fun encodeCp437(s: String): ByteArray {
        val enc = CP437.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val bb: ByteBuffer = enc.encode(CharBuffer.wrap(s))
        val arr = ByteArray(bb.remaining())
        bb.get(arr)
        return arr
    }

    private fun prepareLineForThermal(line: String): String {
        var s = line.replace('\u00A0', ' ').replace('\u202F', ' ')
        s = s.replace('\u20B1', "Php").replace("₱", "Php")
        s = s.replace("$", "Php")
        return s
    }

    private fun htmlToPlainTextForThermal(html: String): String {
        val stripped = html
            .replace(Regex("<title[^>]*>[\\s\\S]*?</title>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        val spanned = HtmlCompat.fromHtml(stripped, HtmlCompat.FROM_HTML_MODE_LEGACY)
        return spanned.toString()
            .replace('\u00A0', ' ')
            .replace(Regex("\r\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun wordWrapLine(line: String, width: Int): List<String> {
        val t = line.trimEnd()
        if (t.isEmpty()) return listOf("")
        if (t.length <= width) return listOf(t)
        val words = t.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (w in words) {
            if (w.isEmpty()) continue
            if (cur.isEmpty()) {
                if (w.length > width) {
                    var i = 0
                    while (i < w.length) {
                        lines.add(w.substring(i, min(i + width, w.length)))
                        i += width
                    }
                } else {
                    cur.append(w)
                }
            } else {
                val candidate = "${cur} $w"
                if (candidate.length <= width) {
                    cur.clear()
                    cur.append(candidate)
                } else {
                    lines.add(cur.toString())
                    cur.clear()
                    if (w.length > width) {
                        var i = 0
                        while (i < w.length) {
                            lines.add(w.substring(i, min(i + width, w.length)))
                            i += width
                        }
                    } else {
                        cur.append(w)
                    }
                }
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }
}

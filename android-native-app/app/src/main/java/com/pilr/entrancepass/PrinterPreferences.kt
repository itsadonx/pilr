package com.pilr.entrancepass

import android.content.Context

object PrinterPreferences {
    private const val PREF = "pilr_printer"
    private const val KEY_DIRECT = "direct_print"
    private const val KEY_MAC = "printer_mac"
    private const val KEY_NAME = "printer_name"

    fun useDirectPrint(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_DIRECT, false)
    }

    fun setDirectPrint(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DIRECT, value)
            .apply()
    }

    fun getPrinterMac(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_MAC, "") ?: ""
    }

    fun getPrinterName(context: Context): String {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""
    }

    fun setPrinter(context: Context, mac: String, name: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_MAC, mac)
            .putString(KEY_NAME, name)
            .apply()
    }
}

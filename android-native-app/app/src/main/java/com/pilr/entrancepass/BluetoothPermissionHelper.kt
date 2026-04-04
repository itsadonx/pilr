package com.pilr.entrancepass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothPermissionHelper {

    /**
     * Direct print only uses paired devices + RFCOMM (no BLE scan / no discovery).
     * On API 31+ that requires [Manifest.permission.BLUETOOTH_CONNECT] only — not BLUETOOTH_SCAN.
     */
    fun hasBluetoothPrintPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}

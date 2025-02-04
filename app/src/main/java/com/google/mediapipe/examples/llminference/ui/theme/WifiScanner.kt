package com.google.mediapipe.examples.llminference

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat

class WifiScanner(private val context: Context) {
    companion object {
        private const val TAG = "WifiScanner"
    }

    fun getWifiNetworks(): List<String> {
        // Check for location permission
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted")
            return emptyList()
        }

        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

        return try {
            // Log all scan results including empty SSIDs
            Log.d(TAG, "Raw scan results:")
            wifiManager.scanResults.forEach { result ->
                Log.d(TAG, "SSID: '${result.SSID}', BSSID: ${result.BSSID}, Signal Strength: ${result.level}dBm")
            }

            val filteredNetworks = wifiManager.scanResults
                .mapNotNull { result ->
                    result.SSID.takeIf { it.isNotEmpty() }
                }
                .distinct()

            // Log filtered networks
            Log.d(TAG, "Filtered networks:")
            filteredNetworks.forEach { ssid ->
                Log.d(TAG, "Network SSID: '$ssid'")
            }

            Log.d(TAG, "Total networks found: ${filteredNetworks.size}")

            filteredNetworks
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while scanning WiFi", e)
            emptyList()
        }
    }
}
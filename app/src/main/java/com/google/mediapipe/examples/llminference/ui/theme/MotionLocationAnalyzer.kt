package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.Closeable
import java.lang.ref.WeakReference

class MotionLocationAnalyzer(context: Context) : Closeable {
    companion object {
        private const val TAG = "MotionLocationAnalyzer"
        private const val MOTION_DETECTION_DURATION = 10000L // 10 seconds
    }

    private val contextRef = WeakReference(context)
    private val analyzerScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentJob: Job? = null
    private var isAnalyzing = false

    fun startAnalysis(callback: (String) -> Unit) {
        if (isAnalyzing) {
            Log.w(TAG, "Analysis already in progress")
            return
        }

        val context = contextRef.get() ?: run {
            callback("Error: Context no longer available")
            return
        }

        isAnalyzing = true
        currentJob = analyzerScope.launch {
            try {
                // Step 1: Motion Detection with cleanup
                callback("Starting motion detection phase...")
                val motionSuccess = runMotionDetection(context, callback)

                // Force garbage collection between phases
                System.gc()
                delay(8000) // Give time for cleanup

                // Step 2: Location Analysis with cleanup
                if (motionSuccess) {
                    callback("Starting location analysis phase...")
                    val locationSuccess = runLocationAnalysis(context, callback)

                    // Force garbage collection between phases
                    System.gc()
                    delay(1000) // Give time for cleanup

                    // Step 3: Retrieve and combine results
                    if (locationSuccess) {
                        callback("Retrieving combined results...")
                        val results = retrieveCombinedResults(context)
                        callback(results)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during analysis", e)
                callback("Error during analysis: ${e.message}")
            } finally {
                isAnalyzing = false
            }
        }
    }

    private suspend fun runMotionDetection(context: Context, callback: (String) -> Unit): Boolean {
        var motionDetector: MotionDetector? = null
        return try {
            withContext(Dispatchers.Main) {
                motionDetector = MotionDetector(context)

                var success = false
                motionDetector?.startDetection { motions ->
                    callback("Current motions: ${motions.joinToString(", ")}")
                }

                delay(MOTION_DETECTION_DURATION)
                success = true
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Motion detection error", e)
            false
        } finally {
            motionDetector?.stopDetection()
            motionDetector = null
            System.gc() // Request garbage collection
        }
    }

    private suspend fun runLocationAnalysis(context: Context, callback: (String) -> Unit): Boolean {
        var locationAnalyzer: LocationAnalyzer? = null
        return try {
            withContext(Dispatchers.IO) {
                locationAnalyzer = LocationAnalyzer(context)
                val wifiScanner = WifiScanner(context)
                val networks = wifiScanner.getWifiNetworks()
                locationAnalyzer?.analyzeLocation(networks)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location analysis error", e)
            false
        } finally {
            locationAnalyzer?.close()
            locationAnalyzer = null
            System.gc() // Request garbage collection
        }
    }

    private fun retrieveCombinedResults(context: Context): String {
        val resultBuilder = StringBuilder()

        try {
            // Get motion results
            resultBuilder.append("=== Motion Analysis Results ===\n")
            val motionStorage = MotionStorage(context)
            val motionHistory = motionStorage.getMotionHistory()
            resultBuilder.append(motionHistory ?: "No motion data available")
            resultBuilder.append("\n\n")

            // Get location results
            resultBuilder.append("=== Location Analysis Results ===\n")
            val fileStorage = FileStorage(context)
            val locationHistory = fileStorage.getLastResponse()
            resultBuilder.append(locationHistory ?: "No location data available")
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving results", e)
            resultBuilder.append("Error retrieving results: ${e.message}")
        }

        return resultBuilder.toString()
    }

    fun stopAnalysis() {
        currentJob?.cancel()
        currentJob = null
        isAnalyzing = false
        System.gc() // Request final garbage collection
    }

    override fun close() {
        stopAnalysis()
        analyzerScope.cancel()
    }
}
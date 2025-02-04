package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class MotionStorage(private val context: Context) {
    companion object {
        private const val TAG = "MotionStorage"
        private const val FILENAME = "motion_detections.json"
        private const val MAX_DETECTIONS = 10
    }

    private val file: File by lazy {
        File(context.filesDir, FILENAME)
    }

    /**
     * Saves a new motion detection, maintaining only the last 10 detections
     * @param motions List of detected motions
     * @return true if save was successful, false otherwise
     */
    fun saveMotionDetection(motions: List<String>): Boolean {
        return try {
            // Create timestamp
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())

            // Read existing detections
            var detections = readDetections()

            // Add new detection
            val newDetection = JSONObject().apply {
                put("timestamp", timestamp)
                put("motions", JSONArray(motions))
            }

            // Add to list and keep only last 10
            detections.put(newDetection)
            while (detections.length() > MAX_DETECTIONS) {
                // Remove oldest (first) detection
                val newArray = JSONArray()
                for (i in 1 until detections.length()) {
                    newArray.put(detections.get(i))
                }
                detections = newArray
            }

            // Write back to file
            file.writeText(detections.toString(2))

            Log.d(TAG, "Successfully saved motion detection")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving motion detection", e)
            false
        }
    }

    /**
     * Gets all saved motion detections
     * @return JSONArray of detections or empty JSONArray if none exist
     */
    private fun readDetections(): JSONArray {
        return try {
            if (!file.exists()) {
                return JSONArray()
            }

            val content = file.readText()
            if (content.isEmpty()) {
                return JSONArray()
            }

            JSONArray(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading motion detections", e)
            JSONArray()
        }
    }

    /**
     * Gets the last 10 motion detections as a formatted string
     * @return String containing the detections or null if error occurs
     */
    fun getMotionHistory(): String? {
        return try {
            val detections = readDetections()
            if (detections.length() == 0) {
                return "No motion detections recorded"
            }

            val builder = StringBuilder()
            for (i in 0 until detections.length()) {
                val detection = detections.getJSONObject(i)
                val timestamp = detection.getString("timestamp")
                val motions = detection.getJSONArray("motions")

                builder.append("[$timestamp]\n")
                builder.append("Detected motions: ")
                val motionList = mutableListOf<String>()
                for (j in 0 until motions.length()) {
                    motionList.add(motions.getString(j))
                }
                builder.append(motionList.joinToString(", "))
                builder.append("\n\n")
            }

            builder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting motion history", e)
            null
        }
    }

    /**
     * Clears all saved motion detections
     * @return true if clear was successful, false otherwise
     */
    fun clearMotionHistory(): Boolean {
        return try {
            if (file.exists()) {
                file.writeText("[]")
                Log.d(TAG, "Successfully cleared motion history")
                true
            } else {
                Log.d(TAG, "No file exists to clear")
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error clearing motion history", e)
            false
        }
    }
}
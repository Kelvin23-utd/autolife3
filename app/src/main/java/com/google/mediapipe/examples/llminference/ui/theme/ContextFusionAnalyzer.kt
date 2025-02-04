package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

class ContextFusionAnalyzer(private val context: Context) : Closeable {
    companion object {
        private const val TAG = "ContextFusionAnalyzer"
    }

    private val llmInference: LlmInference = LlmManager.getInstance(context)
    private var motionStorage: MotionStorage? = null
    private var fileStorage: FileStorage? = null

    suspend fun performFusion(): String = withContext(Dispatchers.IO) {
        try {
            motionStorage = MotionStorage(context)
            fileStorage = FileStorage(context)

            val motionHistory = motionStorage?.getMotionHistory() ?: "No motion data"
            val locationHistory = fileStorage?.getLastResponse() ?: "No location data"

            val truncatedMotion = motionHistory.takeLast(200)
            val truncatedLocation = locationHistory.takeLast(200)

            val prompt = """
                Given the following data, describe the most likely activity in exactly 20 words:
                Motion: $truncatedMotion
                Location: $truncatedLocation
            """.trimIndent()

            Log.d(TAG, "Sending fusion prompt: $prompt")

            val response = try {
                llmInference.generateResponse(prompt)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating LLM response", e)
                "Fusion analysis failed: ${e.message}"
            }

            Log.d(TAG, "Received fusion response: $response")
            response

        } catch (e: Exception) {
            Log.e(TAG, "Error during fusion analysis", e)
            "Error during fusion: ${e.message}"
        }
    }

    override fun close() {
        try {
            // No need to close LLM instance as it's managed by LlmManager
            motionStorage = null
            fileStorage = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
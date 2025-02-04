package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.llminference.server.OllamaClient
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ContextFusionAnalyzer(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : Closeable {
    companion object {
        private const val TAG = "ContextFusionAnalyzer"
        private const val OLLAMA_BASE_URL = "http://localhost:11434"
    }

    private val llmInference: LlmInference = LlmManager.getInstance(context)
    private val ollamaClient = OllamaClient(OLLAMA_BASE_URL)
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

    suspend fun performOllamaFusion(
        model: String = "llama3.2:latest",
        onResult: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
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

                Log.d(TAG, "Sending Ollama fusion prompt: $prompt")

                val response = ollamaClient.chat(
                    model = model,
                    message = prompt
                )

                val result = when {
                    response.isSuccess -> response.getOrNull()?.message?.content ?: "Empty response"
                    else -> "Error: ${response.exceptionOrNull()?.message}"
                }

                Log.d(TAG, "Received Ollama fusion response: $result")
                onResult(result)

            } catch (e: Exception) {
                Log.e(TAG, "Error during Ollama fusion analysis", e)
                onResult("Error during Ollama fusion: ${e.message}")
            }
        }
    }

    fun testOllama(onResult: (String) -> Unit) {
        coroutineScope.launch {
            try {
                onResult("Loading...")
                val response = ollamaClient.chat(
                    model = "llama3.2:latest",
                    message = "Hello, how are you?"
                )

                val result = when {
                    response.isSuccess -> response.getOrNull()?.message?.content ?: "Empty response"
                    else -> "Error: ${response.exceptionOrNull()?.message}"
                }
                onResult(result)
            } catch (e: Exception) {
                onResult("Error: ${e.message}")
            }
        }
    }

    override fun close() {
        try {
            motionStorage = null
            fileStorage = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
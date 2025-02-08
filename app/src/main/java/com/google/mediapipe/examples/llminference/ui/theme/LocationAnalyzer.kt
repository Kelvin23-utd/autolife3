package com.google.mediapipe.examples.llminference

import UnifiedAIClient
import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.examples.llminference.ui.theme.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope


class LocationAnalyzer(private val context: Context) : Closeable {
    companion object {
        private const val TAG = "LocationAnalyzer"
    }

    private val fileStorage = FileStorage(context)
    private val llmInference: LlmInference = LlmManager.getInstance(context)
    private val openAIClient = UnifiedAIClient("") // Replace with your API key

    suspend fun analyzeLocation(ssids: List<String>): String {
        val prompt = """
            Based on the following WiFi network names, analyze where this location might be(response in summary within 50 words:
            ${ssids.joinToString("\n")}
            Provide a brief analysis of the likely location.
        """.trimIndent()

        Log.d(TAG, "Sending prompt to ${if (ModelConfig.USE_API == 1) "API" else "Local LLM"}:")
        Log.d(TAG, prompt)

        val response = if (ModelConfig.USE_API == 1) {
            // Use API
            withContext(Dispatchers.IO) {
                suspendCancellableCoroutine { continuation ->
                    openAIClient.easyCall(
                        prompt = prompt,
                        scope = CoroutineScope(continuation.context),
                        onSuccess = { response ->
                            continuation.resume(response)
                        },
                        onError = { error ->
                            Log.e(TAG, "API request failed: $error")
                            continuation.resumeWithException(
                                IllegalStateException("Failed to generate API response: $error")
                            )
                        }
                    )
                }
            }
        } else {
            // Use local LLM
            try {
                llmInference.generateResponse(prompt)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating LLM response", e)
                throw IllegalStateException("Failed to generate LLM response: ${e.message}")
            }
        }

        Log.d(TAG, "Location, Received response from ${if (ModelConfig.USE_API == 1) "API" else "Local LLM"}:")
        Log.d(TAG, response)

        // Save the response to file
        fileStorage.saveLLMResponse(response)

        return response
    }

    /**
     * Gets the last saved LLM analysis response
     * @return The last saved response or null if none exists
     */
    fun getLastAnalysis(): String? {
        return fileStorage.getLastResponse()
    }

    /**
     * Clears any previously saved analysis results
     * @return true if successfully cleared, false otherwise
     */
    fun clearAnalysisHistory(): Boolean {
        return fileStorage.clearResponses()
    }

    override fun close() {
        // No need to close LLM instance here as it's managed by LlmManager
    }
}

// LlmManager remains the same
object LlmManager {
    private var llmInstance: LlmInference? = null
    private val lock = Object()

    fun getInstance(context: Context): LlmInference {
        synchronized(lock) {
            if (llmInstance == null) {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(ModelConfig.LOCAL_MODEL_PATH)
                    .setMaxTokens(1024)
                    .build()

                llmInstance = LlmInference.createFromOptions(context, options)
            }
            return llmInstance!!
        }
    }
}
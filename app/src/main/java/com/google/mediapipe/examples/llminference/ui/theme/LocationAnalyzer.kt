package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.examples.llminference.ui.theme.ModelConfig
import java.io.Closeable

class LocationAnalyzer(private val context: Context) : Closeable {
    companion object {
        private const val TAG = "LocationAnalyzer"
    }

    private val fileStorage = FileStorage(context)
    private val llmInference: LlmInference = LlmManager.getInstance(context)

    fun analyzeLocation(ssids: List<String>): String {
        val prompt = """
            Based on the following WiFi network names, analyze where this location might be(response in summary within 50 words:
            ${ssids.joinToString("\n")}
            Provide a brief analysis of the likely location.
        """.trimIndent()

        Log.d(TAG, "Sending prompt to LLM:")
        Log.d(TAG, prompt)

        val response = try {
            llmInference.generateResponse(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating LLM response", e)
            throw IllegalStateException("Failed to generate LLM response: ${e.message}")
        }

        Log.d(TAG, "Location, Received response from LLM:")
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

// Add this singleton object in the same package
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
       
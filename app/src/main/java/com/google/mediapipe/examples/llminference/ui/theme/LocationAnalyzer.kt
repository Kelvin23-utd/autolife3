package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
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


//    private val g2 = "/data/local/tmp/llm/gemma-2b-it-gpu-int4.bin"
//    private val g22b = "/data/local/tmp/llm/gemma2-2b-gpu.bin"
//
//    private val fac = "/data/local/tmp/llm/falcon_gpu.bin"
//    private val stb = "/data/local/tmp/llm/stablelm_gpu.bin"
//    private val stb = "/data/local/tmp/llm/phi2_gpu.bin"
//    private val g7b = "/data/local/tmp/llm/gemma-1.1-7b-it-gpu-int8.bin"


    fun getInstance(context: Context): LlmInference {
        synchronized(lock) {
            if (llmInstance == null) {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath("/data/local/tmp/llm/gemma-2b-it-gpu-int4.bin")
                    .setMaxTokens(1024)
                    .setMaxTopK(20)  // Changed from setTopK to setMaxTopK
                    .build()

                llmInstance = LlmInference.createFromOptions(context, options)
            }
            return llmInstance!!
        }
    }

}
       
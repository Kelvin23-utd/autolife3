package com.google.mediapipe.examples.llminference


import UnifiedAIClient
import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.llminference.ui.theme.ApiConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.examples.llminference.ui.theme.ModelConfig
import com.google.mediapipe.examples.llminference.ui.theme.cloudAI
import com.google.mediapipe.examples.llminference.ui.theme.server.AIProvider
import com.google.mediapipe.examples.llminference.ui.theme.server.UnifiedAIManager
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
    // Initialize LLM only if not using API
    private val llmInference: LlmInference? by lazy {
        if (ModelConfig.USE_API != 1) LlmManager.getInstance(context) else null
    }
//    val openAIClient = UnifiedAIManager(
//        claudeKey = ApiConfig.CLAUDE_KEY,
//        defaultProvider = AIProvider.CLAUDE
//    )

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
                    cloudAI.easyCall(
                        prompt = prompt,
                        scope = CoroutineScope(continuation.context),
                        onSuccess = { response -> continuation.resume(response) },
                        onError = { error ->
                            Log.e(TAG, "API request failed: $error")
                            continuation.resumeWithException(IllegalStateException("Failed to generate API response: $error"))
                        }
                    )
                }
            }
        } else {
            // Use local LLM
            llmInference?.generateResponse(prompt) ?: throw IllegalStateException("LLM not initialized")
        }

        Log.d(TAG, "Location, Received response from ${if (ModelConfig.USE_API == 1) "API" else "Local LLM"}:")
        Log.d(TAG, response)
        fileStorage.saveLLMResponse(response)
        return response
    }

    fun getLastAnalysis(): String? = fileStorage.getLastResponse()
    fun clearAnalysisHistory(): Boolean = fileStorage.clearResponses()
    override fun close() {}
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
                    .setMaxTokens(6000)
                    .build()

                llmInstance = LlmInference.createFromOptions(context, options)
            }
            return llmInstance!!
        }
    }
}
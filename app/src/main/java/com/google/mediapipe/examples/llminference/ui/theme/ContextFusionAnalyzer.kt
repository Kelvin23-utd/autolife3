package com.google.mediapipe.examples.llminference

import UnifiedAIClient
import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.llminference.server.OllamaClient
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.google.mediapipe.examples.llminference.ui.theme.ModelConfig
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    private val unified = UnifiedAIClient("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")

    suspend fun performFusion(): String = withContext(Dispatchers.IO) {
        try {
            motionStorage = MotionStorage(context)
            fileStorage = FileStorage(context)

            val motionHistory = motionStorage?.getMotionHistory() ?: "No motion data"
            val locationHistory = fileStorage?.getLastResponse() ?: "No location data"

            val truncatedMotion = motionHistory.takeLast(200)
            val truncatedLocation = locationHistory.takeLast(200)

            val prompt = """
                Select the most probable motion with location and motion context within 50 words:
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
        model: String =ModelConfig.OLLAMA_MODEL,
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
                    Select the most probable motion with location and motion context within 50 words:
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

    suspend fun performOllamaGeneration(
        model: String = ModelConfig.OLLAMA_MODEL,
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
                Select the most probable motion with location and motion context within 50 words:
                Motion: $truncatedMotion
                Location: $truncatedLocation
            """.trimIndent()

                Log.d(TAG, "Sending Ollama generation prompt: $prompt")

                val response = ollamaClient.generate(
                    model = model,
                    prompt = prompt
                )

                val result = when {
                    response.isSuccess -> response.getOrNull()?.response ?: "Empty response"
                    else -> "Error: ${response.exceptionOrNull()?.message}"
                }

                Log.d(TAG, "Received Ollama generation response: $result")
                onResult(result)

            } catch (e: Exception) {
                Log.e(TAG, "Error during Ollama generation analysis", e)
                onResult("Error during Ollama generation: ${e.message}")
            }
        }
    }


    suspend fun performOpenAIFusion(): String = withContext(Dispatchers.IO) {
        try {
            motionStorage = MotionStorage(context)
            fileStorage = FileStorage(context)

            val motionHistory = motionStorage?.getMotionHistory() ?: "No motion data"
            val locationHistory = fileStorage?.getLastResponse() ?: "No location data"

            val truncatedMotion = motionHistory.takeLast(200)
            val truncatedLocation = locationHistory.takeLast(200)

            val prompt = """
            Select the most probable motion with location and motion context within 50 words:
            Motion: $truncatedMotion
            Location: $truncatedLocation
        """.trimIndent()

            Log.d(TAG, "Sending OpenAI fusion prompt: $prompt")

            // Use suspendCoroutine to convert callback-based API to suspend function
            suspendCoroutine<String> { continuation ->
                unified.easyCall(
                    prompt = prompt,
                    scope = coroutineScope,
                    onStart = { /* No need to handle start state for direct return */ },
                    onSuccess = { result ->
                        Log.d(TAG, "Received OpenAI fusion response: $result")
                        continuation.resume(result)
                    },
                    onError = { error ->
                        Log.e(TAG, "Error during OpenAI fusion analysis: $error")
                        continuation.resume("Error during OpenAI fusion: $error")
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during OpenAI fusion analysis", e)
            "Error during OpenAI fusion: ${e.message}"
        }
    }

    fun testOllama(onResult: (String) -> Unit) {
        coroutineScope.launch {
            try {
                onResult("Loading...")
                val response = ollamaClient.chat(
                    model = ModelConfig.OLLAMA_MODEL,
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
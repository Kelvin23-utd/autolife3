package com.google.mediapipe.examples.llminference.ui.theme.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Common data classes and interfaces
@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class Usage(
    val input_tokens: Int? = null,
    val output_tokens: Int? = null,
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)

interface AIClient {
    fun getCompletion(prompt: String, options: RequestOptions = RequestOptions()): Result<String>
    fun easyCall(
        prompt: String,
        scope: CoroutineScope,
        options: RequestOptions = RequestOptions(),
        onStart: () -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    )
}

data class RequestOptions(
    val model: String? = null,
    val maxTokens: Int = 1024,
    val temperature: Double = 0.7
)

// OpenAI Implementation
class OpenAIClient(private val apiKey: String) : AIClient {
    @Serializable
    data class OpenAIRequest(
        val model: String,
        val store: Boolean,
        val messages: List<Message>
    )

    @Serializable
    data class OpenAIChoice(
        val index: Int,
        val message: Message,
        val finish_reason: String
    )

    @Serializable
    data class OpenAIResponse(
        val id: String,
        val created: Long,
        val model: String,
        val choices: List<OpenAIChoice>,
        val usage: Usage
    )

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()
    private val defaultModel = "gpt-4-turbo-preview"
    private val baseUrl = "https://api.openai.com/v1/chat/completions"

    override fun getCompletion(prompt: String, options: RequestOptions): Result<String> {
        return try {
            val response = makeOpenAICall(prompt, options)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makeOpenAICall(prompt: String, options: RequestOptions): String {
        val request = OpenAIRequest(
            model = options.model ?: defaultModel,
            store = true,
            messages = listOf(Message("user", prompt))
        )

        val requestBody = json.encodeToString(request).toRequestBody(mediaType)
        val response = makeApiCall(requestBody)
        val parsedResponse = json.decodeFromString<OpenAIResponse>(response)
        return parsedResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("No response content")
    }

    private fun makeApiCall(requestBody: okhttp3.RequestBody): String {
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("API call failed with code: ${response.code}, error: $errorBody")
            }
            return response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    override fun easyCall(
        prompt: String,
        scope: CoroutineScope,
        options: RequestOptions,
        onStart: () -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { onStart() }
                val response = getCompletion(prompt, options)
                withContext(Dispatchers.Main) {
                    when {
                        response.isSuccess -> {
                            response.getOrNull()?.let { onSuccess(it) }
                                ?: onError("Empty response received")
                        }
                        else -> {
                            val error = response.exceptionOrNull()
                            onError("Error: ${error?.message ?: "Unknown error"}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message ?: "Unknown error"}")
                }
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }
}

// Claude (Anthropic) Implementation
class ClaudeClient(private val apiKey: String) : AIClient {
    @Serializable
    data class AnthropicRequest(
        val model: String,
        val max_tokens: Int,
        val messages: List<Message>,
        val temperature: Double? = null
    )

    @Serializable
    data class AnthropicContent(
        val type: String,
        val text: String
    )

    @Serializable
    data class AnthropicMessage(
        val role: String,
        val content: List<AnthropicContent>
    )

    @Serializable
    data class AnthropicResponse(
        val id: String,
        val type: String,
        val role: String,
        val content: List<AnthropicContent>,
        val model: String,
        val stop_reason: String? = null,
        val usage: Usage? = null
    )

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()
    private val defaultModel = "claude-3-sonnet-20240229"
    private val baseUrl = "https://api.anthropic.com/v1/messages"

    override fun getCompletion(prompt: String, options: RequestOptions): Result<String> {
        return try {
            val response = makeClaudeCall(prompt, options)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makeClaudeCall(prompt: String, options: RequestOptions): String {
        val request = AnthropicRequest(
            model = options.model ?: defaultModel,
            max_tokens = options.maxTokens,
            messages = listOf(Message("user", prompt)),
            temperature = options.temperature
        )

        val requestBody = json.encodeToString(request).toRequestBody(mediaType)
        val response = makeApiCall(requestBody)
        val parsedResponse = json.decodeFromString<AnthropicResponse>(response)
        return parsedResponse.content.firstOrNull()?.text
            ?: throw Exception("No response content")
    }

    private fun makeApiCall(requestBody: okhttp3.RequestBody): String {
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("API call failed with code: ${response.code}, error: $errorBody")
            }
            return response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    override fun easyCall(
        prompt: String,
        scope: CoroutineScope,
        options: RequestOptions,
        onStart: () -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { onStart() }
                val response = getCompletion(prompt, options)
                withContext(Dispatchers.Main) {
                    when {
                        response.isSuccess -> {
                            response.getOrNull()?.let { onSuccess(it) }
                                ?: onError("Empty response received")
                        }
                        else -> {
                            val error = response.exceptionOrNull()
                            onError("Error: ${error?.message ?: "Unknown error"}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message ?: "Unknown error"}")
                }
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }
}

class GeminiClient(private val apiKey: String) : AIClient {
    @Serializable
    data class GeminiContent(
        val parts: List<GeminiPart>
    )

    @Serializable
    data class GeminiPart(
        val text: String
    )

    @Serializable
    data class GeminiRequest(
        val contents: List<GeminiContent>
    )

    @Serializable
    data class GeminiCandidate(
        val content: GeminiContent,
        val finishReason: String? = null
    )

    @Serializable
    data class GeminiResponse(
        val candidates: List<GeminiCandidate>,
        val promptFeedback: Map<String, String>? = null
    )

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()
    private val defaultModel = "gemini-1.5-pro"
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    override fun getCompletion(prompt: String, options: RequestOptions): Result<String> {
        return try {
            val response = makeGeminiCall(prompt, options)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makeGeminiCall(prompt: String, options: RequestOptions): String {
        val model = options.model ?: defaultModel
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt)
                    )
                )
            )
        )

        val requestBody = json.encodeToString(request).toRequestBody(mediaType)
        val response = makeApiCall(requestBody, model)
        val parsedResponse = json.decodeFromString<GeminiResponse>(response)
        return parsedResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response content")
    }

    private fun makeApiCall(requestBody: okhttp3.RequestBody, model: String): String {
        val url = "$baseUrl/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("API call failed with code: ${response.code}, error: $errorBody")
            }
            return response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    override fun easyCall(
        prompt: String,
        scope: CoroutineScope,
        options: RequestOptions,
        onStart: () -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { onStart() }
                val response = getCompletion(prompt, options)
                withContext(Dispatchers.Main) {
                    when {
                        response.isSuccess -> {
                            response.getOrNull()?.let { onSuccess(it) }
                                ?: onError("Empty response received")
                        }
                        else -> {
                            val error = response.exceptionOrNull()
                            onError("Error: ${error?.message ?: "Unknown error"}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message ?: "Unknown error"}")
                }
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }
}


class QwenClient(private val apiKey: String) : AIClient {
    @Serializable
    data class QwenRequest(
        val model: String,
        val messages: List<Message>,
        val max_tokens: Int? = null,
        val temperature: Double? = null
    )

    @Serializable
    data class QwenChoice(
        val index: Int? = null,
        val message: Message,
        val finish_reason: String? = null
    )

    @Serializable
    data class QwenResponse(
        val id: String? = null,
        val created: Long? = null,
        val model: String,
        val choices: List<QwenChoice>,
        val usage: Usage? = null
    )

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()
    private val defaultModel = "qwen-plus"
    private val baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions"

    override fun getCompletion(prompt: String, options: RequestOptions): Result<String> {
        return try {
            val response = makeQwenCall(prompt, options)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makeQwenCall(prompt: String, options: RequestOptions): String {
        val request = QwenRequest(
            model = options.model ?: defaultModel,
            messages = listOf(Message("user", prompt)),
            max_tokens = options.maxTokens,
            temperature = options.temperature
        )

        val requestBody = json.encodeToString(request).toRequestBody(mediaType)
        val response = makeApiCall(requestBody)
        val parsedResponse = json.decodeFromString<QwenResponse>(response)
        return parsedResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("No response content")
    }

    private fun makeApiCall(requestBody: okhttp3.RequestBody): String {
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("API call failed with code: ${response.code}, error: $errorBody")
            }
            return response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    override fun easyCall(
        prompt: String,
        scope: CoroutineScope,
        options: RequestOptions,
        onStart: () -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { onStart() }
                val response = getCompletion(prompt, options)
                withContext(Dispatchers.Main) {
                    when {
                        response.isSuccess -> {
                            response.getOrNull()?.let { onSuccess(it) }
                                ?: onError("Empty response received")
                        }
                        else -> {
                            val error = response.exceptionOrNull()
                            onError("Error: ${error?.message ?: "Unknown error"}")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Error: ${e.message ?: "Unknown error"}")
                }
            } finally {
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }
}
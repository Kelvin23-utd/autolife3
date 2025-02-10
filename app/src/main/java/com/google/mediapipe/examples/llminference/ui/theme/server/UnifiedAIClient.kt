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

// Common message format
@Serializable
data class Message(
    val role: String,
    val content: String
)

// OpenAI specific models
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

// Anthropic specific models
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

@Serializable
data class Usage(
    val input_tokens: Int? = null,
    val output_tokens: Int? = null,
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)

enum class AIProvider {
    OPENAI,
    ANTHROPIC
}

class UnifiedAIClient(
    private val openAIKey: String? = null,
    private val anthropicKey: String? = null,
    private val defaultProvider: AIProvider = AIProvider.OPENAI
) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    private val openAIConfig = ProviderConfig(
        baseUrl = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
        authHeader = "Bearer"
    )

    private val anthropicConfig = ProviderConfig(
        baseUrl = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-3-5-sonnet-20241022",
        authHeader = "x-api-key"
    )

    data class ProviderConfig(
        val baseUrl: String,
        val defaultModel: String,
        val authHeader: String
    )

    data class RequestOptions(
        val provider: AIProvider? = null,
        val model: String? = null,
        val maxTokens: Int = 1024,
        val temperature: Double = 0.7
    )

    fun getCompletion(prompt: String, options: RequestOptions = RequestOptions()): Result<String> {
        return try {
            val provider = options.provider ?: defaultProvider
            val response = when (provider) {
                AIProvider.OPENAI -> makeOpenAICall(prompt, options)
                AIProvider.ANTHROPIC -> makeAnthropicCall(prompt, options)
            }
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makeOpenAICall(prompt: String, options: RequestOptions): String {
        val request = OpenAIRequest(
            model = options.model ?: openAIConfig.defaultModel,
            store = true,
            messages = listOf(Message("user", prompt))
        )

        val requestBody = json.encodeToString(request).toRequestBody(mediaType)
        val response = makeApiCall(
            openAIConfig.baseUrl,
            requestBody,
            openAIKey ?: throw Exception("OpenAI API key not provided"),
            openAIConfig.authHeader
        )
        val parsedResponse = json.decodeFromString<OpenAIResponse>(response)
        return parsedResponse.choices.firstOrNull()?.message?.content
            ?: throw Exception("No response content")
    }

    private fun makeAnthropicCall(prompt: String, options: RequestOptions): String {
        val request = AnthropicRequest(
            model = options.model ?: anthropicConfig.defaultModel,
            max_tokens = options.maxTokens,
            messages = listOf(Message("user", prompt)),
            temperature = options.temperature
        )

        val requestBody = json.encodeToString(request).toRequestBody(mediaType)
        val response = makeApiCall(
            anthropicConfig.baseUrl,
            requestBody,
            anthropicKey ?: throw Exception("Anthropic API key not provided"),
            anthropicConfig.authHeader,
            mapOf("anthropic-version" to "2023-06-01")
        )
        val parsedResponse = json.decodeFromString<AnthropicResponse>(response)
        return parsedResponse.content.firstOrNull()?.text
            ?: throw Exception("No response content")
    }

    private fun makeApiCall(
        url: String,
        requestBody: okhttp3.RequestBody,
        apiKey: String,
        authHeader: String,
        additionalHeaders: Map<String, String> = emptyMap()
    ): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader(if (authHeader == "Bearer") "Authorization" else authHeader,
                if (authHeader == "Bearer") "Bearer $apiKey" else apiKey)
            .post(requestBody)

        additionalHeaders.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                throw Exception("API call failed with code: ${response.code}, error: $errorBody")
            }
            return response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    fun easyCall(
        prompt: String,
        scope: CoroutineScope,
        options: RequestOptions = RequestOptions(),
        onStart: () -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    onStart()
                }

                val response = getCompletion(prompt, options)

                withContext(Dispatchers.Main) {
                    when {
                        response.isSuccess -> {
                            val content = response.getOrNull()
                            if (content != null) {
                                onSuccess(content)
                            } else {
                                onError("Empty response received")
                            }
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
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }
}
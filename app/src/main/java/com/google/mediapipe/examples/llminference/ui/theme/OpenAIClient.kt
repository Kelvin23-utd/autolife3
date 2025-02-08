
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val store: Boolean,
    val messages: List<Message>
)

@Serializable
data class Choice(
    val index: Int,
    val message: Message,
    val finish_reason: String
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

@Serializable
data class ChatResponse(
    val id: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

class OpenAIClient(private val apiKey: String) {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()
    private val baseUrl = "https://api.openai.com/v1/chat/completions"

    fun getCompletion(prompt: String): Result<ChatResponse> {
        return try {
            val request = createChatRequest(prompt)
            val response = makeApiCall(request)
            Result.success(parseChatResponse(response))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createChatRequest(prompt: String): ChatRequest {
        return ChatRequest(
            model = "gpt-4o-mini",
            store = true,
            messages = listOf(Message("user", prompt))
        )
    }

    private fun makeApiCall(chatRequest: ChatRequest): String {
        val requestBody = json.encodeToString(chatRequest)  // Changed this line
            .toRequestBody(mediaType)

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("API call failed with code: ${response.code}")
            }
            return response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    private fun parseChatResponse(responseJson: String): ChatResponse {
        return json.decodeFromString<ChatResponse>(responseJson)  // Changed this line
    }
}
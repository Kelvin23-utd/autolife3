package com.google.mediapipe.examples.llminference.server

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApiService {
    @POST("/api/chat")
    suspend fun chat(@Body request: ChatRequest): Response<ChatResponse>

    @POST("/api/generate")
    suspend fun generate(@Body request: GenerateRequest): Response<GenerateResponse>
}

// Existing chat-related data classes
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val model: String,
    val message: Message,
    val done: Boolean,
    val created_at: String? = null,
    val total_duration: Long? = null    // Server processing time in milliseconds
)

// New generate-related data classes
data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val raw: Boolean = false,
    val options: GenerateOptions? = null
)

data class GenerateOptions(
    val temperature: Float? = null,
    val top_p: Float? = null,
    val top_k: Int? = null,
    val repeat_penalty: Float? = null,
    val seed: Long? = null,
    val num_predict: Int? = null
)

data class GenerateResponse(
    val model: String,
    val response: String,
    val done: Boolean,
    val created_at: String? = null,
    val total_duration: Long? = null,    // Server processing time in milliseconds
    val context: List<Int>? = null
)
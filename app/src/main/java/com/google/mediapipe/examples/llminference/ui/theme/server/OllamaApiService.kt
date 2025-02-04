package com.google.mediapipe.examples.llminference.server

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApiService {
    @POST("/api/chat")
    suspend fun chat(@Body request: ChatRequest): Response<ChatResponse>
}

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
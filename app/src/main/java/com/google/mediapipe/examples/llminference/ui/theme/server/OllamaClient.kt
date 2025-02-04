package com.google.mediapipe.examples.llminference.server

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.Interceptor
import okhttp3.Response

class TimingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val startTime = System.currentTimeMillis()
        val request = chain.request()

        Log.d("Timing", "Request sent at: $startTime")

        val response = chain.proceed(request)
        val endTime = System.currentTimeMillis()

        Log.d("Timing", "Response received at: $endTime")
        Log.d("Timing", "Total time: ${endTime - startTime}ms")

        return response
    }
}

class OllamaClient(baseUrl: String) {
    companion object {
        private const val tag = "OllamaClient"
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(TimingInterceptor())
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service = retrofit.create(OllamaApiService::class.java)

    suspend fun chat(model: String, message: String): Result<ChatResponse> {
        return try {
            val requestStartTime = System.currentTimeMillis()
            Log.d(tag, "Starting chat request at: $requestStartTime")

            val request = ChatRequest(
                model = model,
                messages = listOf(Message("user", message)),
                stream = false
            )

            val response = service.chat(request)
            val requestEndTime = System.currentTimeMillis()

            Log.d(tag, "Request completed at: $requestEndTime")
            Log.d(tag, "Total processing time: ${requestEndTime - requestStartTime}ms")

            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response"))
            } else {
                val errorBody = response.errorBody()?.string()
                Result.failure(Exception("Error: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception in chat request", e)
            Result.failure(e)
        }
    }
}
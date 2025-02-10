package com.google.mediapipe.examples.llminference.ui.theme.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class AIProvider {
    OPENAI,
    CLAUDE,
    GEMINI,
    QWEN
}

class UnifiedAIManager(
    openAIKey: String? = null,
    claudeKey: String? = null,
    geminiKey: String? = null,
    qwenKey: String? = null,
    private val defaultProvider: AIProvider = AIProvider.OPENAI
) {
    private val openAIClient: OpenAIClient? = openAIKey?.let { OpenAIClient(it) }
    private val claudeClient: ClaudeClient? = claudeKey?.let { ClaudeClient(it) }
    private val geminiClient: GeminiClient? = geminiKey?.let { GeminiClient(it) }
    private val qwenClient: QwenClient? = qwenKey?.let { QwenClient(it) }

    data class UnifiedRequestOptions(
        val provider: AIProvider? = null,
        val model: String? = null,
        val maxTokens: Int = 1024,
        val temperature: Double = 0.7
    )

    fun getCompletion(
        prompt: String,
        options: UnifiedRequestOptions = UnifiedRequestOptions()
    ): Result<String> {
        val provider = options.provider ?: defaultProvider
        val client = when (provider) {
            AIProvider.OPENAI -> openAIClient
            AIProvider.CLAUDE -> claudeClient
            AIProvider.GEMINI -> geminiClient
            AIProvider.QWEN -> qwenClient
        } ?: return Result.failure(Exception("API key not provided for ${provider.name}"))

        val clientOptions = RequestOptions(
            model = options.model,
            maxTokens = options.maxTokens,
            temperature = options.temperature
        )

        return client.getCompletion(prompt, clientOptions)
    }

    fun easyCall(
        prompt: String,
        scope: CoroutineScope,
        options: UnifiedRequestOptions = UnifiedRequestOptions(),
        onStart: () -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        val provider = options.provider ?: defaultProvider
        val client = when (provider) {
            AIProvider.OPENAI -> openAIClient
            AIProvider.CLAUDE -> claudeClient
            AIProvider.GEMINI -> geminiClient
            AIProvider.QWEN -> qwenClient
        }

        if (client == null) {
            scope.launch(Dispatchers.Main) {
                onError("API key not provided for ${provider.name}")
                onComplete()
            }
            return
        }

        val clientOptions = RequestOptions(
            model = options.model,
            maxTokens = options.maxTokens,
            temperature = options.temperature
        )

        client.easyCall(
            prompt = prompt,
            scope = scope,
            options = clientOptions,
            onStart = onStart,
            onSuccess = onSuccess,
            onError = onError,
            onComplete = onComplete
        )
    }

    companion object {
        // Default model names for reference
        object Models {
            object OpenAI {
                const val GPT4_TURBO = "gpt-4-turbo-preview"
                const val GPT4 = "gpt-4"
                const val GPT35_TURBO = "gpt-3.5-turbo"
            }

            object Claude {
                const val CLAUDE_3_OPUS = "claude-3-opus-20240229"
                const val CLAUDE_3_SONNET = "claude-3-sonnet-20240229"
                const val CLAUDE_3_HAIKU = "claude-3-haiku-20240307"
            }

            object Gemini {
                const val GEMINI_PRO = "gemini-1.5-pro"
                const val GEMINI_FLASH = "gemini-1.5-flash"
            }

            object Qwen {
                const val QWEN_PLUS = "qwen-plus"
                const val QWEN_TURBO = "qwen-turbo"
            }
        }
    }
}

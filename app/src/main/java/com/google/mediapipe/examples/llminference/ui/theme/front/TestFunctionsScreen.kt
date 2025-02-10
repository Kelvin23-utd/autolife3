package com.google.mediapipe.examples.llminference.ui.theme.front


import UnifiedAIClient
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.examples.llminference.ContextFusionAnalyzer
import com.google.mediapipe.examples.llminference.server.OllamaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.mediapipe.examples.llminference.ui.theme.ModelConfig


import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.examples.llminference.ui.theme.ApiConfig
import com.google.mediapipe.examples.llminference.ui.theme.server.AIProvider
import com.google.mediapipe.examples.llminference.ui.theme.server.UnifiedAIManager

import kotlinx.coroutines.launch

// TestFunctionsViewModel.kt
class TestFunctionsViewModel : ViewModel() {
    private val _chatResponse = MutableStateFlow<String?>(null)
    val chatResponse = _chatResponse.asStateFlow()

    private val _fusionResult = MutableStateFlow<String?>(null)
    val fusionResult = _fusionResult.asStateFlow()

    private val _isFusing = MutableStateFlow(false)
    val isFusing = _isFusing.asStateFlow()

    private val ollamaClient = OllamaClient("http://localhost:11434")
    private var fusionAnalyzer: ContextFusionAnalyzer? = null


    private val _apiResponse = MutableStateFlow<String?>(null)
    val apiResponse = _apiResponse.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

//    private val openAIClient = UnifiedAIClient(defaultProvider = ApiConfig.LLM_model,
//        anthropicKey = ApiConfig.LLM_API_KEY)

    val manager = UnifiedAIManager(
        geminiKey = ApiConfig.LLM_API_KEY,
        defaultProvider = AIProvider.GEMINI
    )



    private companion object {
        private const val TAG = "ApiRequestVM" // Adjust tag name as needed
    }

//    fun testApiRequest1() {
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                withContext(Dispatchers.Main) {
//                    _isLoading.value = true
//                    _apiResponse.value = "Loading..."
//                }
//
//                val response = openAIClient.getCompletion("how was your busy day ? within 50 wrods")
//
//                withContext(Dispatchers.Main) {
//                    _apiResponse.value = when {
//                        response.isSuccess -> response.getOrNull()?.choices?.firstOrNull()?.message?.content
//                            ?: run {
//                                Log.e(TAG, "Empty response received from API")
//                                "Empty response"
//                            }
//                        else -> {
//                            val error = response.exceptionOrNull()
//                            Log.e(TAG, "API request failed", error)
//                            "Error: ${error?.message}"
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Exception during API request", e)
//                withContext(Dispatchers.Main) {
//                    _apiResponse.value = "Error: ${e.message}"
//                }
//            } finally {
//                withContext(Dispatchers.Main) {
//                    _isLoading.value = false
//                }
//            }
//        }
//    }

    fun testApiRequest() {
        manager.easyCall(
            prompt = "what is your name?",
            scope = viewModelScope,
            onStart = {
                _isLoading.value = true
                _apiResponse.value = "Loading..."
            },
            onSuccess = { response ->
                _apiResponse.value = response
            },
            onError = { error ->
                Log.e(TAG, "API request failed: $error")
                _apiResponse.value = error
            },
            onComplete = {
                _isLoading.value = false
            }
        )


    }


    fun initializeFusionAnalyzer(context: Context) {
        if (fusionAnalyzer == null) {
            fusionAnalyzer = ContextFusionAnalyzer(context, viewModelScope)
        }
    }

    fun testOllama() {
        viewModelScope.launch {
            try {
                _chatResponse.value = "Loading..."
                val response = ollamaClient.chat(
                    model = ModelConfig.OLLAMA_MODEL,
                    message = "Hello, how are you?"
                )

                _chatResponse.value = when {
                    response.isSuccess -> response.getOrNull()?.message?.content ?: "Empty response"
                    else -> "Error: ${response.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _chatResponse.value = "Error: ${e.message}"
            }
        }
    }

    fun testContextFusion() {
        viewModelScope.launch {
            try {
                _isFusing.value = true
                fusionAnalyzer?.let { analyzer ->
                    _fusionResult.value = analyzer.performFusion()
                } ?: run {
                    _fusionResult.value = "Error: Fusion analyzer not initialized"
                }
            } catch (e: Exception) {
                _fusionResult.value = "Error: ${e.message}"
            } finally {
                _isFusing.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fusionAnalyzer = null
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestFunctionsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = rememberTestFunctionsViewModel()
    val chatResponse = viewModel.chatResponse.collectAsStateWithLifecycle()
    val fusionResult = viewModel.fusionResult.collectAsStateWithLifecycle()
    val isFusing = viewModel.isFusing.collectAsStateWithLifecycle()
    val apiResponse = viewModel.apiResponse.collectAsStateWithLifecycle()
    val isLoading = viewModel.isLoading.collectAsStateWithLifecycle()

    // Initialize fusion analyzer
    LaunchedEffect(Unit) {
        viewModel.initializeFusionAnalyzer(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test Functions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // API Request Test Card
            TestFunctionCard(
                title = "Test API Request",
                description = "Send a request to get an AI-generated haiku",
                onTest = {
                    viewModel.testApiRequest()
                },
                isLoading = isLoading.value
            )

            // Ollama Test Card
            TestFunctionCard(
                title = "Test Ollama",
                description = "Test the Ollama API integration",
                onTest = {
                    viewModel.testOllama()
                }
            )

            // Context Fusion Test Card
            TestFunctionCard(
                title = "Test Context Fusion",
                description = "Test the fusion of motion and location contexts",
                onTest = {
                    viewModel.testContextFusion()
                },
                isLoading = isFusing.value
            )

            // Display Results
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // API Request Results
                apiResponse.value?.let { response ->
                    ResultCard(
                        title = "API Request Result",
                        content = response,
                        isLoading = isLoading.value
                    )
                }

                // Ollama Results
                chatResponse.value?.let { response ->
                    ResultCard(
                        title = "Ollama Test Result",
                        content = response,
                        isLoading = response == "Loading..."
                    )
                }

                // Fusion Results
                fusionResult.value?.let { result ->
                    ResultCard(
                        title = "Fusion Test Result",
                        content = result,
                        isLoading = false
                    )
                }
            }
        }
    }
}

@Composable
private fun TestFunctionCard(
    title: String,
    description: String,
    onTest: () -> Unit,
    isLoading: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onTest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Running..." else "Run Test")
            }
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    content: String,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun rememberTestFunctionsViewModel(): TestFunctionsViewModel {
    return viewModel<TestFunctionsViewModel>()
}



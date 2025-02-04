package com.google.mediapipe.examples.llminference.ui.theme.front


import android.content.Context
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


import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

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

    fun initializeFusionAnalyzer(context: Context) {
        if (fusionAnalyzer == null) {
            fusionAnalyzer = ContextFusionAnalyzer(context)
        }
    }

    fun testOllama() {
        viewModelScope.launch {
            try {
                _chatResponse.value = "Loading..."
                val response = ollamaClient.chat(
                    model = "deepseek-r1:1.5b",
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

// TestFunctionsScreen.kt
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
fun rememberTestFunctionsViewModel(): TestFunctionsViewModel {
    return viewModel<TestFunctionsViewModel>()
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
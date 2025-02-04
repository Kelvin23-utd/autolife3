package com.google.mediapipe.examples.llminference

import android.Manifest
import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mediapipe.examples.llminference.server.OllamaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ViewModel
class MainViewModel : ViewModel() {
    private val _chatResponse = MutableStateFlow<String?>(null)
    val chatResponse = _chatResponse.asStateFlow()

    // Initialize OllamaClient with your server's base URL
    private val ollamaClient = OllamaClient("http://localhost:11434")  // Use 10.0.2.2 for Android Emulator
    // For physical device, use your computer's local IP address, e.g.:
    // private val ollamaClient = OllamaClient("http://192.168.1.100:11434")

    fun sendMessage(message: String) {
        viewModelScope.launch {
            try {
                _chatResponse.value = "Loading..."
                val response = ollamaClient.chat(
                    model = "deepseek-r1:1.5b",  // or your preferred model
                    message = message
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
}

@Composable
fun rememberMainViewModel(): MainViewModel {
    return viewModel()
}

class MainActivity : ComponentActivity() {
    init {
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                System.gc()
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.INTERNET
            )
        )

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val viewModel = rememberMainViewModel()
    var currentAnalysis by remember { mutableStateOf<String?>(null) }
    var currentPhase by remember { mutableStateOf(SequentialMotionLocationAnalyzer.AnalysisPhase.NONE) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isFusing by remember { mutableStateOf(false) }
    var fusionResult by remember { mutableStateOf<String?>(null) }
    var memoryInfo by remember { mutableStateOf(MemoryMonitor.getMemoryInfo()) }

    val analyzer = remember { SequentialMotionLocationAnalyzer(context) }
    val fusionAnalyzer = remember { ContextFusionAnalyzer(context) }
    val scope = rememberCoroutineScope()
    val chatResponse = viewModel.chatResponse.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Fixed content section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Memory Monitor
            MemoryMonitorCard(memoryInfo = memoryInfo)

            // Sequential Analysis Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sequential Analysis", style = MaterialTheme.typography.titleMedium)

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (!isAnalyzing) {
                                isAnalyzing = true
                                scope.launch {
                                    memoryInfo = MemoryMonitor.getMemoryInfo()
                                    analyzer.startAnalysis { result, phase ->
                                        currentAnalysis = result
                                        currentPhase = phase
                                        scope.launch {
                                            memoryInfo = MemoryMonitor.getMemoryInfo()
                                        }
                                        if (phase == SequentialMotionLocationAnalyzer.AnalysisPhase.COMPLETE) {
                                            isAnalyzing = false
                                        }
                                    }
                                }
                            } else {
                                analyzer.stopAnalysis()
                                isAnalyzing = false
                            }
                        }
                    ) {
                        Text(
                            when(currentPhase) {
                                SequentialMotionLocationAnalyzer.AnalysisPhase.MOTION -> "Motion Detection in Progress..."
                                SequentialMotionLocationAnalyzer.AnalysisPhase.LOCATION -> "Location Analysis in Progress..."
                                SequentialMotionLocationAnalyzer.AnalysisPhase.COMPLETE -> "Analysis Complete"
                                else -> "Start Analysis"
                            }
                        )
                    }

                    if (isAnalyzing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = when(currentPhase) {
                                SequentialMotionLocationAnalyzer.AnalysisPhase.MOTION -> 0.3f
                                SequentialMotionLocationAnalyzer.AnalysisPhase.LOCATION -> 0.7f
                                SequentialMotionLocationAnalyzer.AnalysisPhase.COMPLETE -> 1.0f
                                else -> 0f
                            }
                        )
                    }
                }
            }

            // Fusion Test Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Test Context Fusion", style = MaterialTheme.typography.titleMedium)

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isFusing,
                        onClick = {
                            scope.launch {
                                isFusing = true
                                memoryInfo = MemoryMonitor.getMemoryInfo()
                                fusionResult = fusionAnalyzer.performFusion()
                                memoryInfo = MemoryMonitor.getMemoryInfo()
                                isFusing = false
                            }
                        }
                    ) {
                        Text(if (isFusing) "Fusing Contexts..." else "Test Fusion")
                    }

                    if (isFusing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = 0.5f
                        )
                    }
                }
            }

            // Ollama Test Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Test Ollama API", style = MaterialTheme.typography.titleMedium)

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.sendMessage("Hello, how are you?")
                        }
                    ) {
                        Text("Test Ollama")
                    }

                    chatResponse.value?.let { response ->
                        if (response == "Loading...") {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                progress = 0.5f
                            )
                        }
                    }
                }
            }
        }

        // Scrollable content section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        currentAnalysis?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        fusionResult?.let {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Fusion Result:\n$it",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        chatResponse.value?.let { response ->
                            if (response != "Loading...") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Ollama Response:\n$response",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryMonitorCard(memoryInfo: MemoryMonitor.MemoryInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Memory Monitor",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Java: ${memoryInfo.usedMemoryMB}MB / ${memoryInfo.maxHeapSizeMB}MB",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                text = "Native Heap: ${memoryInfo.nativeHeapMB}MB",
                style = MaterialTheme.typography.bodySmall
            )

            val rssProgress = (memoryInfo.totalRSSMB.toFloat() / 6000f).coerceIn(0f, 1f)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total RSS Memory",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${memoryInfo.totalRSSMB}MB",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                LinearProgressIndicator(
                    progress = rssProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        rssProgress > 0.8f -> MaterialTheme.colorScheme.error
                        rssProgress > 0.6f -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            Text(
                text = "Total PSS: ${memoryInfo.totalPSSMB}MB",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
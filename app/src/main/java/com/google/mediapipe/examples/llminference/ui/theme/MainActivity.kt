package com.google.mediapipe.examples.llminference

import MemoryMonitor
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mediapipe.examples.llminference.ui.theme.front.AppNavigation
import kotlinx.coroutines.launch

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
                AppNavigation()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToTests: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentAnalysis by remember { mutableStateOf<String?>(null) }
    var currentPhase by remember { mutableStateOf(SequentialMotionLocationAnalyzer.AnalysisPhase.NONE) }
    var isAnalyzing by remember { mutableStateOf(false) }
    // Create MemoryMonitor instance with context
    val memoryMonitor = remember { MemoryMonitor(context) }
    var memoryInfo by remember { mutableStateOf(memoryMonitor.getMemoryInfo()) }

    val analyzer = remember { SequentialMotionLocationAnalyzer(context) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Navigation section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = onNavigateToTests,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Go to Test Functions")
            }
        }

        // Memory Monitor
        MemoryMonitorCard(
            memoryInfo = memoryInfo,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sequential Analysis Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
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
                                memoryInfo = memoryMonitor.getMemoryInfo()
                                analyzer.startAnalysis { result, phase ->
                                    currentAnalysis = result
                                    currentPhase = phase
                                    scope.launch {
                                        memoryInfo = memoryMonitor.getMemoryInfo()
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

        // Results Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                currentAnalysis?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun MemoryMonitorCard(
    memoryInfo: MemoryMonitor.MemoryInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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

// Test Functions Screen
@Composable
fun TestFunctionsScreen(
    onNavigateBack: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Back to Main Screen")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add your test function buttons here
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Test Functions", style = MaterialTheme.typography.titleMedium)

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { /* Add test function 1 */ }
                ) {
                    Text("Test Function 1")
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { /* Add test function 2 */ }
                ) {
                    Text("Test Function 2")
                }

                // Add more test function buttons as needed
            }
        }
    }
}
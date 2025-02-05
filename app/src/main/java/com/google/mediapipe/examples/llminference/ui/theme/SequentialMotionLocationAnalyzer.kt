package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.Closeable
import java.lang.ref.WeakReference
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.examples.llminference.ui.theme.ModelConfig
import java.io.File

import android.os.Debug
import java.io.RandomAccessFile
import kotlin.math.roundToInt

class SequentialMotionLocationAnalyzer(context: Context) : Closeable {
    companion object {
        private const val TAG = "SequentialAnalyzer"
        private const val MOTION_DURATION = 10000L // 10 seconds
    }

    private val contextRef = WeakReference(context)
    private val analyzerScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentJob: Job? = null
    private val llmInference: LlmInference by lazy { LlmManager.getInstance(context) }

    private var isAnalyzing = false
    private var currentPhase = AnalysisPhase.NONE
    private var motionDetector: MotionDetector? = null
    private var locationAnalyzer: LocationAnalyzer? = null
    private var llmFusionResult = ""
    private var ollamaFusionResult = ""

    // Initialize timing variables
    private var llmStartTime = 0L
    private var llmEndTime = 0L
    private var ollamaStartTime = 0L
    private var ollamaEndTime = 0L

    // Add this near the top of the class, with other properties
    private val phaseTimestamps = mutableMapOf<AnalysisPhase, Long>()

    // Add memory monitoring properties
    private val phaseMemoryInfo = mutableMapOf<AnalysisPhase, MemoryInfo>()
    private data class MemoryInfo(
        val timestamp: Long,
        val memoryStats: MemoryMonitor.MemoryInfo
    )

    private fun recordPhaseMemory(phase: AnalysisPhase) {
        val timestamp = System.currentTimeMillis()
        val memoryStats = MemoryMonitor.getMemoryInfo()
        phaseMemoryInfo[phase] = MemoryInfo(timestamp, memoryStats)
        Log.d(TAG, "Memory usage at $phase:\n$memoryStats")
    }

    // Add this helper function to the class
    private fun recordPhaseStart(phase: AnalysisPhase) {
        phaseTimestamps[phase] = System.currentTimeMillis()
    }

    enum class AnalysisPhase {
        NONE,
        MOTION,
        LOCATION,
        FUSION,
        COMPLETE
    }

    fun startAnalysis(callback: (String, AnalysisPhase) -> Unit) {
        if (isAnalyzing) {
            callback("Analysis already in progress", currentPhase)
            return
        }

        // Reset analysis data from previous runs
        llmFusionResult = ""
        ollamaFusionResult = ""
        llmStartTime = 0L
        llmEndTime = 0L
        ollamaStartTime = 0L
        ollamaEndTime = 0L
        phaseTimestamps.clear()

        val context = contextRef.get() ?: run {
            callback("Context no longer available", AnalysisPhase.NONE)
            return
        }

        startMotionPhase(callback)
    }

    private suspend fun runMotionDetection(context: Context, callback: (String, AnalysisPhase) -> Unit): Boolean {
        return try {
            withContext(Dispatchers.Main) {
                motionDetector = MotionDetector(context)
                var success = false

                motionDetector?.startDetection { motions ->
                    callback("Current motions: ${motions.joinToString(", ")}", AnalysisPhase.MOTION)
                }

                delay(MOTION_DURATION)
                success = true
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Motion detection error", e)
            false
        } finally {
            motionDetector?.stopDetection()
            motionDetector = null
        }
    }

    private fun startMotionPhase(callback: (String, AnalysisPhase) -> Unit) {
        isAnalyzing = true
        currentPhase = AnalysisPhase.MOTION
        recordPhaseStart(AnalysisPhase.MOTION)
        recordPhaseMemory(AnalysisPhase.MOTION)


        val context = contextRef.get() ?: run {
            cleanup()
            callback("Context no longer available", AnalysisPhase.NONE)
            return
        }

        currentJob = analyzerScope.launch {
            try {
                val motionSuccess = runMotionDetection(context, callback)
                if (motionSuccess) {
                    finishMotionPhase(callback)
                } else {
                    cleanup()
                    callback("Motion detection failed", AnalysisPhase.MOTION)
                }
            } catch (e: CancellationException) {
                cleanup()
                throw e
            }
        }
    }

    private fun finishMotionPhase(callback: (String, AnalysisPhase) -> Unit) {
        val context = contextRef.get() ?: run {
            cleanup()
            callback("Context no longer available", AnalysisPhase.NONE)
            return
        }

        val motionStorage = MotionStorage(context)
        val motionHistory = motionStorage.getMotionHistory()

        motionDetector?.stopDetection()
        motionDetector = null

        startLocationPhase(callback)
    }

    private suspend fun runLocationAnalysis(context: Context, callback: (String, AnalysisPhase) -> Unit): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                locationAnalyzer = LocationAnalyzer(context)
                val wifiScanner = WifiScanner(context)
                val networks = wifiScanner.getWifiNetworks()
                val response = locationAnalyzer?.analyzeLocation(networks)
                callback("Location analysis complete: $response", AnalysisPhase.LOCATION)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location analysis error", e)
            false
        } finally {
            locationAnalyzer?.close()
            locationAnalyzer = null
        }
    }

    private fun startLocationPhase(callback: (String, AnalysisPhase) -> Unit) {
        currentPhase = AnalysisPhase.LOCATION
        recordPhaseStart(AnalysisPhase.LOCATION)
        recordPhaseMemory(AnalysisPhase.LOCATION)
        callback("Starting location analysis...", AnalysisPhase.LOCATION)

        val context = contextRef.get() ?: run {
            cleanup()
            callback("Context no longer available", AnalysisPhase.NONE)
            return
        }

        currentJob = analyzerScope.launch {
            try {
                val locationSuccess = runLocationAnalysis(context, callback)
                if (locationSuccess) {
                    startFusionPhase(callback)
                } else {
                    cleanup()
                    callback("Location analysis failed", AnalysisPhase.LOCATION)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in location analysis", e)
                callback("Error in location analysis: ${e.message}", AnalysisPhase.LOCATION)
                cleanup()
            }
        }
    }

    private suspend fun performContextFusion(context: Context): String {
        val motionStorage = MotionStorage(context)
        val fileStorage = FileStorage(context)

        val motionData = motionStorage.getMotionHistory() ?: "No motion data"
        val locationData = fileStorage.getLastResponse() ?: "No location data"

        var combinedAnalyzer: MotionLocationAnalyzer? = null
        var contextFusionAnalyzer: ContextFusionAnalyzer? = null
        var additionalAnalysis = ""



        try {
            // Create coroutine scope for our operations
            val analysisScope = CoroutineScope(Dispatchers.Main + Job())

            // Step 1: Run the MotionLocationAnalyzer
            combinedAnalyzer = MotionLocationAnalyzer(context)
            val analysisPromise = CompletableDeferred<String>()
            // Pre-define the Ollama promise
            val ollamaPromise = CompletableDeferred<String>()

            combinedAnalyzer.startAnalysis { result ->
                if (result.contains("Retrieving combined results")) {
                    analysisPromise.complete(result)
                }
            }

            additionalAnalysis = analysisPromise.await()

            // Step 2: Run both fusion analyzers if we have analysis results
            if (additionalAnalysis.isNotEmpty()) {
                contextFusionAnalyzer = ContextFusionAnalyzer(context, analysisScope)

                // Step 2a: Perform standard LLM fusion
                llmStartTime = System.currentTimeMillis()
                llmFusionResult = contextFusionAnalyzer.performFusion()
                llmEndTime = System.currentTimeMillis()

                // Step 2b: Perform Ollama fusion
                ollamaStartTime = System.currentTimeMillis()
                contextFusionAnalyzer.performOllamaGeneration { result ->
                    ollamaPromise.complete(result)
                }

                // Wait for Ollama fusion result
                ollamaFusionResult = ollamaPromise.await()
                ollamaEndTime = System.currentTimeMillis()

                // At this point we have both fusion results:
                // - llmFusionResult: contains the standard LLM fusion result
                // - ollamaFusionResult: contains the Ollama-based fusion result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during analysis sequence", e)
            // Handle the error appropriately
            llmFusionResult = "Error during analysis: ${e.message}"
            ollamaFusionResult = "Error during analysis: ${e.message}"
        } finally {
            combinedAnalyzer?.close()
            contextFusionAnalyzer?.close()
        }

        val dateFormat = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())

        return buildString {
            append("=== Initial Analysis ===\n")
            append("Motion: $motionData\n")
            append("Location: $locationData\n\n")
            append("=== Motion Location Analysis ===\n")
            append(additionalAnalysis)
            append("\n\n=== Context Fusion Analysis ===\n")

            // LLM Analysis with timing
            append("LLM Analysis:\n")
            append("--------------\n")
            if (llmStartTime > 0 && llmEndTime > 0) {
                append("Started: ${dateFormat.format(java.util.Date(llmStartTime))}\n")
                append("Completed: ${dateFormat.format(java.util.Date(llmEndTime))}\n")
                append("Duration: ${(llmEndTime - llmStartTime) / 1000.0} seconds\n")
                append("Results:\n")
            }
            append(llmFusionResult)

            // Ollama Analysis with timing
            append("\n\nOllama Analysis:\n")
            append("---------------\n")
            if (ollamaStartTime > 0 && ollamaEndTime > 0) {
                append("Started: ${dateFormat.format(java.util.Date(ollamaStartTime))}\n")
                append("Completed: ${dateFormat.format(java.util.Date(ollamaEndTime))}\n")
                append("Duration: ${(ollamaEndTime - ollamaStartTime) / 1000.0} seconds\n")
                append("Results:\n")
            }
            append(ollamaFusionResult)
        }
    }

    private fun startFusionPhase(callback: (String, AnalysisPhase) -> Unit) {
        recordPhaseStart(AnalysisPhase.FUSION)
        recordPhaseMemory(AnalysisPhase.FUSION)
        currentPhase = AnalysisPhase.FUSION
        callback("Starting context fusion...", AnalysisPhase.FUSION)

        locationAnalyzer?.close()
        locationAnalyzer = null

        currentJob = analyzerScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val context = contextRef.get() ?: run {
                        cleanup()
                        callback("Context no longer available", AnalysisPhase.NONE)
                        return@withContext
                    }

                    val result = performContextFusion(context)
                    withContext(Dispatchers.Main) {
                        callback(result, AnalysisPhase.FUSION)
                    }
                }
                finishAnalysis(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Error in fusion phase", e)
                callback("Error in fusion analysis: ${e.message}", AnalysisPhase.FUSION)
                cleanup()
            }
        }
    }

    private fun finishAnalysis(callback: (String, AnalysisPhase) -> Unit) {
        recordPhaseStart(AnalysisPhase.COMPLETE)
        recordPhaseMemory(AnalysisPhase.COMPLETE)
        currentPhase = AnalysisPhase.COMPLETE
        val results = getCombinedResults()
        saveAnalysisReport(results)
        callback(results, AnalysisPhase.COMPLETE)
        cleanup()
    }

    private fun getCombinedResults(): String {
        val context = contextRef.get() ?: return "Context no longer available"

        val resultBuilder = StringBuilder()
        val motionStorage = MotionStorage(context)
        val fileStorage = FileStorage(context)

        val dateFormat = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())

        resultBuilder.apply {
            append("Analysis Summary Report\n")
            append("=====================\n\n")

            // Phase Timing Information
            append("Phase Timing:\n")
            append("--------------\n")
            phaseTimestamps.forEach { (phase, timestamp) ->
                append("${phase.name}: ${dateFormat.format(java.util.Date(timestamp))}\n")
            }
            append("\n")

            // Motion Phase Results
            append("=== Motion Phase Results ===\n")
            append("Started at: ${dateFormat.format(java.util.Date(phaseTimestamps[AnalysisPhase.MOTION] ?: 0))}\n")
            append(motionStorage.getMotionHistory() ?: "No motion data available")

            // Location Phase Results
            append("\n\n=== Location Phase Results ===\n")
            append("Started at: ${dateFormat.format(java.util.Date(phaseTimestamps[AnalysisPhase.LOCATION] ?: 0))}\n")
            append(fileStorage.getLastResponse() ?: "No location data available")

            // Fusion Phase Results
            append("\n\n=== Fusion Phase Results ===\n")
            append("Started at: ${dateFormat.format(java.util.Date(phaseTimestamps[AnalysisPhase.FUSION] ?: 0))}\n")

            // LLM Analysis with timing
            append("\nLLM Analysis:\n")
            append("--------------\n")
            if (llmStartTime > 0 && llmEndTime > 0) {
                append("Started: ${dateFormat.format(java.util.Date(llmStartTime))}\n")
                append("Completed: ${dateFormat.format(java.util.Date(llmEndTime))}\n")
                append("Duration: ${(llmEndTime - llmStartTime) / 1000.0} seconds\n")
                append("Results:\n")
            }
            append(llmFusionResult.ifBlank { "No LLM fusion analysis available" })

            // Ollama Analysis with timing
            append("\n\nOllama Analysis:\n")
            append("---------------\n")
            if (ollamaStartTime > 0 && ollamaEndTime > 0) {
                append("Started: ${dateFormat.format(java.util.Date(ollamaStartTime))}\n")
                append("Completed: ${dateFormat.format(java.util.Date(ollamaEndTime))}\n")
                append("Duration: ${(ollamaEndTime - ollamaStartTime) / 1000.0} seconds\n")
                append("Results:\n")
            }
            append(ollamaFusionResult.ifBlank { "No Ollama analysis available" })

            // Analysis Completion
            phaseTimestamps[AnalysisPhase.COMPLETE]?.let { completeTime ->
                append("\n\nAnalysis Completed at: ${dateFormat.format(java.util.Date(completeTime))}\n")
                val totalTime = completeTime - (phaseTimestamps[AnalysisPhase.MOTION] ?: completeTime)
                append("Total Analysis Time: ${totalTime / 1000.0} seconds")
            }

            // Add Memory Usage Section
            append("\n\n=== Memory Usage Analysis ===\n")
            append("Memory statistics for each phase:\n\n")

            phaseMemoryInfo.forEach { (phase, memInfo) ->
                append("${phase.name} Phase Memory Stats:\n")
                append("Timestamp: ${dateFormat.format(java.util.Date(memInfo.timestamp))}\n")
                append("${memInfo.memoryStats}\n\n")
            }

            // Calculate memory differences between phases
            if (phaseMemoryInfo.size > 1) {
                append("Memory Usage Changes Between Phases:\n")
                phaseMemoryInfo.entries.zipWithNext().forEach { (first, second) ->
                    val firstStats = first.value.memoryStats
                    val secondStats = second.value.memoryStats

                    append("\n${first.key} → ${second.key}:\n")
                    append("Java Heap Change: ${secondStats.usedMemoryMB - firstStats.usedMemoryMB}MB\n")
                    append("Native Heap Change: ${secondStats.nativeHeapMB - firstStats.nativeHeapMB}MB\n")
                    append("PSS Change: ${secondStats.totalPSSMB - firstStats.totalPSSMB}MB\n")
                    append("RSS Change: ${secondStats.totalRSSMB - firstStats.totalRSSMB}MB\n")
                }
            }

            // Add existing model information
            append("\n\nModel Information:\n")
            append("The local model used:\n ${ModelConfig.LOCAL_MODEL_PATH}\n")
            append("Ollama model used: \n ${ModelConfig.OLLAMA_MODEL}\n")
        }

        return resultBuilder.toString()
    }

    private fun saveAnalysisReport(report: String) {
        val context = contextRef.get() ?: return

        try {
            // Create a timestamp for the filename
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                .format(java.util.Date())

            // Create filename with timestamp
            val filename = "analysis_report_$timestamp.txt"

            // Get the app's external files directory
            context.getExternalFilesDir(null)?.let { dir ->
                val reportFile = File(dir, filename)

                // Write the report to the file
                reportFile.writeText(report)

                // Log the file location (optional)
                Log.d("Analysis", "Report saved to: ${reportFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("Analysis", "Error saving report", e)
        }
    }

    fun getCurrentPhase(): AnalysisPhase = currentPhase

    fun stopAnalysis() {
        currentJob?.cancel()
        cleanup()
    }

    private fun cleanup() {
        currentJob?.cancel()
        currentJob = null
        motionDetector?.stopDetection()
        motionDetector = null
        locationAnalyzer?.close()
        locationAnalyzer = null
        isAnalyzing = false
        currentPhase = AnalysisPhase.NONE
    }

    override fun close() {
        stopAnalysis()
        analyzerScope.cancel()
    }
}
package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.Closeable
import java.lang.ref.WeakReference
import com.google.mediapipe.tasks.genai.llminference.LlmInference

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
            System.gc()
        }
    }

    private fun startMotionPhase(callback: (String, AnalysisPhase) -> Unit) {
        isAnalyzing = true
        currentPhase = AnalysisPhase.MOTION
        System.gc()

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
        System.gc()

        startLocationPhase(callback)
    }

    private fun startLocationPhase(callback: (String, AnalysisPhase) -> Unit) {
        currentPhase = AnalysisPhase.LOCATION
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
            System.gc()
        }
    }

    private fun startFusionPhase(callback: (String, AnalysisPhase) -> Unit) {
        currentPhase = AnalysisPhase.FUSION
        callback("Starting context fusion...", AnalysisPhase.FUSION)

        locationAnalyzer?.close()
        locationAnalyzer = null
        System.gc()

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

    private suspend fun performContextFusion(context: Context): String {
        val motionStorage = MotionStorage(context)
        val fileStorage = FileStorage(context)

        val motionData = motionStorage.getMotionHistory() ?: "No motion data"
        val locationData = fileStorage.getLastResponse() ?: "No location data"

        var combinedAnalyzer: MotionLocationAnalyzer? = null
        var additionalAnalysis = ""
        var contextFusionAnalyzer: ContextFusionAnalyzer? = null
        var llmFusionResult = ""

        try {
            // Step 1: Run the MotionLocationAnalyzer and wait for its completion
            combinedAnalyzer = MotionLocationAnalyzer(context)
            val analysisPromise = CompletableDeferred<String>()

            combinedAnalyzer.startAnalysis { result ->
                if (result.contains("Retrieving combined results")) {
                    analysisPromise.complete(result)
                }
            }

            // Wait for motion analysis to complete before proceeding
            additionalAnalysis = analysisPromise.await()

            // Step 2: Only after motion analysis is complete, run the ContextFusionAnalyzer
            if (additionalAnalysis.isNotEmpty()) {
                contextFusionAnalyzer = ContextFusionAnalyzer(context)
                llmFusionResult = contextFusionAnalyzer.performFusion()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in fusion analysis", e)
            if (additionalAnalysis.isEmpty()) {
                additionalAnalysis = "Additional analysis failed: ${e.message}"
            }
            if (llmFusionResult.isEmpty()) {
                llmFusionResult = "LLM fusion failed: ${e.message}"
            }
        } finally {
            combinedAnalyzer?.close()
            combinedAnalyzer = null
            contextFusionAnalyzer?.close()
            contextFusionAnalyzer = null
            System.gc()
        }

        return buildString {
            append("=== Initial Analysis ===\n")
            append("Motion: $motionData\n")
            append("Location: $locationData\n\n")
            append("=== Motion Location Analysis ===\n")
            append(additionalAnalysis)
            append("\n\n=== Context Fusion Analysis ===\n")
            append(llmFusionResult)
        }
    }

    private fun finishAnalysis(callback: (String, AnalysisPhase) -> Unit) {
        currentPhase = AnalysisPhase.COMPLETE
        val results = getCombinedResults()
        callback(results, AnalysisPhase.COMPLETE)
        cleanup()
    }

    private fun getCombinedResults(): String {
        val context = contextRef.get() ?: return "Context no longer available"

        val resultBuilder = StringBuilder()

        resultBuilder.append("=== Motion Analysis Results ===\n")
        val motionStorage = MotionStorage(context)
        val motionHistory = motionStorage.getMotionHistory()
        resultBuilder.append(motionHistory ?: "No motion data available")
        resultBuilder.append("\n\n")

        resultBuilder.append("=== Location Analysis Results ===\n")
        val fileStorage = FileStorage(context)
        val locationHistory = fileStorage.getLastResponse()
        resultBuilder.append(locationHistory ?: "No location data available")

        return resultBuilder.toString()
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
        System.gc()
    }

    override fun close() {
        stopAnalysis()
        analyzerScope.cancel()
        System.gc()
    }
}
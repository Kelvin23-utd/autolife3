# AutoLife Motion & Location Context Fusion Demo

## Overview
This Android sample layers motion sensing, local Wi-Fi fingerprinting, and on-device LLM inference to infer what a user is doing and where they might be. It extends the MediaPipe `LlmInference` API with:
- Sequential motion detection (accelerometer, step counter, pressure sensor, and GPS speed).
- Wi-Fi SSID analysis fed to an on-device LLM for location summarisation.
- A fusion pass that combines motion and location history into a single contextual summary.
- Optional interaction with a self-hosted Ollama server for remote model testing.
- A live memory monitor to help you gauge runtime footprint while the pipelines execute.

The app is built with Jetpack Compose and must run on a physical Android device because it relies on hardware sensors and on-device GPU acceleration for MediaPipe models.

## Requirements
- [Android Studio](https://developer.android.com/studio) Hedgehog (or newer) with the Android Gradle plugin that ships with it.
- Physical Android device running Android 7.0 (API 24) or newer, with:
  - Accelerometer, step counter, barometer (for altitude), GPS, and Wi-Fi radios enabled.
  - Developer mode and USB debugging enabled.
- A compatible [MediaPipe LLM Inference model](https://developers.google.com/mediapipe/solutions/genai/llm_inference#models) copied to the device (`/data/local/tmp/llm/...` by default).
- Optional: an Ollama server reachable from the device (default `http://localhost:11434`) with a supported chat model such as `deepseek-r1:1.5b`.

## Project Layout
- `app/src/main/java/com/google/mediapipe/examples/llminference/MainActivity.kt` – Compose UI, permission handling, and orchestration.
- `MotionDetector.kt`, `MotionStorage.kt` – Motion sensor ingestion, heuristics, and recent-history storage.
- `LocationAnalyzer.kt`, `WifiScanner.kt`, `FileStorage.kt`, `LlmManager` – Wi-Fi fingerprinting and local LLM analysis.
- `SequentialMotionLocationAnalyzer.kt`, `ContextFusionAnalyzer.kt`, `MotionLocationAnalyzer.kt` – Stepwise pipelines and LLM-based context fusion.
- `server/OllamaClient.kt`, `OllamaApiService.kt` – Retrofit client for interacting with a remote Ollama instance.
- `MemoryMonitor.kt` – Runtime memory usage sampling for display in the UI.
- `AndroidManifest.xml`, `res/xml/network_security_config.xml` – Permissions and cleartext allowlist for local servers.

## Getting Started
1. **Open the project**  
   In Android Studio, choose **Open**, then select this repository’s root (`autolife3`). Allow Gradle sync to finish.

2. **Install an on-device LLM model**  
   - Copy a compatible `.bin` file to the device (default path `/data/local/tmp/llm/gemma-2b-it-gpu-int4.bin`).  
   - If you use a different filename or location, adjust `setModelPath(...)` in `LlmManager.getInstance` (`app/src/main/java/com/google/mediapipe/examples/llminference/LocationAnalyzer.kt`).

3. **(Optional) Configure Ollama**  
   - Ensure the Ollama daemon is running on your development machine.  
   - For physical devices, update the base URL in `MainViewModel` (`MainActivity.kt`) to your machine’s IP (e.g. `http://<your-machine-ip>:11434`).  
   - Add that host to `res/xml/network_security_config.xml` if it is not already allow-listed.

4. **Build and run**  
   Connect the device via USB (or use Wi-Fi debugging), press **Run**, and accept the permissions prompt on first launch (fine location, activity recognition, internet).

## Using the Demo
- **Memory Monitor card** – Shows Java heap, native heap, PSS, and RSS usage; auto-updates when analyses run.
- **Sequential Analysis card** – Kicks off the combined pipeline: motion sensing → Wi-Fi scan + LLM location analysis → fusion summary. Progress text and a determinate indicator reflect the current phase.
- **Test Context Fusion card** – Runs only the fusion stage on the most recent motion and location artifacts. Useful for iterating on prompts or model parameters.
- **Test Ollama card** – Sends a sample chat request to the configured Ollama server and renders the LLM response once available.
- **Scrollable results panel** – Shows the raw motion history, saved LLM response, fusion output, and Ollama reply in chronological order.

Keep the device unlocked and stationary sensors enabled while tests run; the motion detector listens for roughly 10 seconds per pass before advancing.

## Customising the App
### On-device LLM configuration
- `LlmManager` (inside `LocationAnalyzer.kt`) is the single point of configuration for MediaPipe `LlmInference`. Change `setModelPath`, token limits, or sampling params (`setMaxTokens`, `setMaxTopK`) here.
- If you need to load multiple models, extend `LlmManager` to expose different named instances or switch paths based on runtime state.

### Prompt engineering and output shaping
- `LocationAnalyzer.analyzeLocation` builds the Wi-Fi prompt. Tweak wording, response length, or add structured output requirements to change the location summaries.
- `ContextFusionAnalyzer.performFusion` crafts the fusion prompt (currently “Describe the most likely activity in exactly 20 words”). Modify this string to alter tone, format, or word count.
- `SequentialMotionLocationAnalyzer.performContextFusion` combines historical motion/location logs with additional analysis; adjust concatenation or result formatting there if you want richer UI output.

### Motion detection heuristics
- Thresholds for classifying activities live in `MotionDetector.detectMotion`. Update step counts, speed windows, altitude deltas, or add new motion categories to better match your target environment.
- The capture window (`MOTION_DURATION`) is 10 seconds; change it in `SequentialMotionLocationAnalyzer` and `MotionLocationAnalyzer` if you need longer or shorter observation periods.
- Use `MotionStorage.MAX_DETECTIONS` to control how many historical entries are retained (default 10).

### Data persistence
- Motion history is stored in `filesDir/motion_detections.json`. Adjust saving logic or switch to another persistence layer in `MotionStorage`.
- LLM outputs are appended to `filesDir/llm_responses.txt`. Modify `FileStorage.saveLLMResponse` if you prefer incremental logging instead of overwriting the previous result.

### Networking and external LLMs
- Update the Ollama base URL or default model name (`deepseek-r1:1.5b`) in `MainViewModel`. You can expose these as UI settings by lifting them into Compose state if desired.
- Keep `res/xml/network_security_config.xml` aligned with any HTTP (non-HTTPS) endpoints you intend to hit from the device or emulator.
- All Retrofit plumbing for Ollama lives under `ui/theme/server/`; extend `OllamaApiService` with additional endpoints (e.g. `/api/generate`) if you need more operations.

### UI adjustments
- The entire UI is defined in `MainActivity.kt` using composables (`MainScreen`, `MemoryMonitorCard`, etc.). Modify layouts, typography, or add new controls here.
- Each analyzer exposes clear entry points (`startAnalysis`, `performFusion`, `sendMessage`) that can be wired to new UI events without touching lower-level implementation details.

## Troubleshooting
- **Model fails to load** – Confirm the model file is present, readable, and compiled for GPU execution. Update the path in `LlmManager` to match your deployment.
- **No Wi-Fi or motion data** – Verify permissions were granted, the device sensors are available, and Wi-Fi scanning is enabled (Android 13+ may require “Nearby devices” permission in system settings).
- **Ollama errors or timeouts** – Check the device reaches your server (use `adb shell curl http://host:11434`). Add the host to `network_security_config` and ensure the selected model is already pulled by Ollama.
- **Memory spikes** – Watch the in-app memory monitor and Logcat tags (`MotionDetector`, `LocationAnalyzer`, `ContextFusionAnalyzer`) to identify phases that need optimisation.

## Next Steps
- Swap in different MediaPipe models for specialised location reasoning.
- Integrate additional sensor streams (Bluetooth beacons, light sensor) by extending `MotionDetector` or adding new analyzers.
- Persist results to a backend or surface configuration controls in the UI for the parameters highlighted above.

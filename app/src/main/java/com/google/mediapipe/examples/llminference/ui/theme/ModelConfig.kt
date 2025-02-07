package com.google.mediapipe.examples.llminference.ui.theme

object ModelConfig {
    const val GEMMA = "/data/local/tmp/llm/gemma-2b-it-gpu-int4.bin"
    const val GEMMA2 = "/data/local/tmp/llm/gemma2-2b-gpu.bin"

    const val LLAMA3_2_3B = "llama3.2:latest"
    const val LLAMA3_2_1B = "llama3.2:1b"
    const val LLAMA3_1_8B = "llama3.1:8b"

    const val DEEP_1_5B = "deepseek-r1:1.5b"
    const val DEEP_8B = "deepseek-r1:8b"

    const val GEMMA2_2B_OLLAMA = "gemma2:2b"
    const val GEMMA_2B = "gemma:2b"
    const val GEMMA_7B = "gemma:7b"



    const val QWEN5B = "qwen:0.5b"
    const val QWEN1_8B ="qwen:1.8b"
    const val QWEN4B = "qwen:4b"
    const val QWEN7B = "qwen:7b"

    const val LOCAL_MODEL_PATH = GEMMA
    const val OLLAMA_MODEL = QWEN5B

    //    const private val fac = "/data/local/tmp/llm/falcon_gpu.bin"
    //    const private val stb = "/data/local/tmp/llm/stablelm_gpu.bin"
    //    const private val stb = "/data/local/tmp/llm/phi2_gpu.bin"
    //    const private val g7b = "/data/local/tmp/llm/gemma-1.1-7b-it-gpu-int8.bin"
}
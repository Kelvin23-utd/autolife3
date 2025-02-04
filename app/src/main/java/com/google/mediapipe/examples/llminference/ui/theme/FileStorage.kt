package com.google.mediapipe.examples.llminference

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileStorage(private val context: Context) {
    companion object {
        private const val TAG = "FileStorage"
        private const val FILENAME = "llm_responses.txt"
    }

    private val file: File by lazy {
        File(context.filesDir, FILENAME)
    }

    /**
     * Saves the LLM response to a file, clearing previous content
     * @param response The LLM response to save
     * @return true if save was successful, false otherwise
     */
    fun saveLLMResponse(response: String): Boolean {
        return try {
            // Create timestamp
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())

            // Format the content with timestamp
            val content = "[$timestamp]\n$response\n\n"

            // Write to file, overwriting previous content
            file.writeText(content)

            Log.d(TAG, "Successfully saved LLM response to file")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error saving LLM response to file", e)
            false
        }
    }

    /**
     * Reads the last saved LLM response from the file
     * @return The last saved response or null if no response exists or error occurs
     */
    fun getLastResponse(): String? {
        return try {
            if (!file.exists()) {
                Log.d(TAG, "No saved responses found")
                return null
            }

            val content = file.readText()
            if (content.isEmpty()) {
                Log.d(TAG, "File exists but is empty")
                return null
            }

            content
        } catch (e: IOException) {
            Log.e(TAG, "Error reading LLM response from file", e)
            null
        }
    }

    /**
     * Clears all saved responses from the file
     * @return true if clear was successful, false otherwise
     */
    fun clearResponses(): Boolean {
        return try {
            if (file.exists()) {
                file.writeText("")
                Log.d(TAG, "Successfully cleared saved responses")
                true
            } else {
                Log.d(TAG, "No file exists to clear")
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error clearing saved responses", e)
            false
        }
    }
}
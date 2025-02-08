import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import java.io.RandomAccessFile
import kotlin.math.roundToInt

class MemoryMonitor(private val context: Context) {
    companion object {
        private const val TAG = "MemoryMonitor"
        private const val MB = 1024 * 1024.0
        private const val KB = 1024.0
    }

    fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Get system memory info
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        // Java heap memory
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / MB
        val maxHeapSizeInMB = runtime.maxMemory() / MB

        // Native memory
        val nativeHeapInMB = Debug.getNativeHeapAllocatedSize() / MB

        // Total PSS (Proportional Set Size)
        val pssInMB = getPSSMemory() / KB  // getPSSMemory returns KB

        // RSS (Resident Set Size) - total memory actually held in RAM
        val rssInMB = getRSSMemory() / KB   // getRSSMemory returns KB

        return MemoryInfo(
            usedMemoryMB = usedMemInMB.roundToInt(),
            maxHeapSizeMB = maxHeapSizeInMB.roundToInt(),
            nativeHeapMB = nativeHeapInMB.roundToInt(),
            totalPSSMB = pssInMB.roundToInt(),
            totalRSSMB = rssInMB.roundToInt(),
            systemAvailableMemoryMB = (memoryInfo.availMem / MB).roundToInt(),
            systemTotalMemoryMB = (memoryInfo.totalMem / MB).roundToInt(),
            lowMemory = memoryInfo.lowMemory,
            lowMemoryThresholdMB = (memoryInfo.threshold / MB).roundToInt()
        )
    }

    private fun getPSSMemory(): Long {
        try {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            return info.totalPss.toLong()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting PSS memory", e)
            return 0
        }
    }

    private fun getRSSMemory(): Long {
        try {
            val pid = android.os.Process.myPid()
            val reader = RandomAccessFile("/proc/$pid/status", "r")
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (line?.startsWith("VmRSS:") == true) {
                    val values = line?.split("\\s+".toRegex())
                    reader.close()
                    return values?.get(1)?.toLongOrNull() ?: 0
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting RSS memory", e)
        }
        return 0
    }

    data class MemoryInfo(
        val usedMemoryMB: Int,
        val maxHeapSizeMB: Int,
        val nativeHeapMB: Int,
        val totalPSSMB: Int,
        val totalRSSMB: Int,
        val systemAvailableMemoryMB: Int,
        val systemTotalMemoryMB: Int,
        val lowMemory: Boolean,
        val lowMemoryThresholdMB: Int
    ) {
        override fun toString(): String {
            return "System Memory: ${systemAvailableMemoryMB}MB free of ${systemTotalMemoryMB}MB total\n" +
                    "Low Memory: $lowMemory (threshold: ${lowMemoryThresholdMB}MB)\n" +
                    "Java Heap: ${usedMemoryMB}MB / ${maxHeapSizeMB}MB\n" +
                    "Native Heap: ${nativeHeapMB}MB\n" +
                    "Total PSS: ${totalPSSMB}MB\n" +
                    "Total RSS: ${totalRSSMB}MB"
        }
    }
}
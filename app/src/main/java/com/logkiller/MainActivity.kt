package com.logkiller

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/**
 * LogKiller - Android TV utility to reduce persistent log buffer size.
 * 
 * This application addresses devices where persist.logd.logpersistd.size is configured
 * with values larger than necessary (commonly 512 MB), which can consume significant
 * storage on devices with limited internal memory.
 * 
 * IMPORTANT: This tool only modifies system properties if the device has root access
 * or if the user manually executes ADB commands. The app itself cannot bypass Android
 * security restrictions without proper authorization.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var card1Value: TextView
    private lateinit var card2Value: TextView
    private lateinit var card3Value: TextView
    private lateinit var statusText: TextView
    private lateinit var fixButton: Button
    private lateinit var restoreButton: Button
    private lateinit var copyAdbButton: Button
    private lateinit var retryButton: Button

    private lateinit var prefs: SharedPreferences

    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        // Common log buffer sizes found in Android devices
        private const val DEFAULT_LOG_SIZE = 512
        private const val TARGET_LOG_SIZE = 60
        private const val PREFS_NAME = "logkiller_prefs"
        private const val KEY_FIX_APPLIED = "fix_applied"
        private const val KEY_ORIGINAL_SIZE = "original_size"
        private const val KEY_NEW_SIZE = "new_size"
        private const val KEY_TIMESTAMP = "timestamp"
        
        // Valid log sizes per Android documentation
        private val VALID_LOG_SIZES = setOf(4, 8, 16, 32, 64, 128, 256, 512, 1024, 4096)
        
        // Command timeout in seconds
        private const val COMMAND_TIMEOUT_SECONDS = 30L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupClickListeners()
        refreshAllData()
    }

    private fun initViews() {
        card1Value = findViewById(R.id.card1_value)
        card2Value = findViewById(R.id.card2_value)
        card3Value = findViewById(R.id.card3_value)
        statusText = findViewById(R.id.status_text)
        fixButton = findViewById(R.id.fix_button)
        restoreButton = findViewById(R.id.restore_button)
        copyAdbButton = findViewById(R.id.copy_adb_button)
        retryButton = findViewById(R.id.retry_button)
    }

    private fun setupClickListeners() {
        fixButton.setOnClickListener {
            animateButton(fixButton)
            applyFix()
        }

        restoreButton.setOnClickListener {
            animateButton(restoreButton)
            restoreDefaults()
        }

        copyAdbButton.setOnClickListener {
            copyAdbCommandsToClipboard()
        }

        retryButton.setOnClickListener {
            animateButton(retryButton)
            refreshAllData()
        }
    }

    private fun animateButton(button: View) {
        val scaleAnimation = ScaleAnimation(
            1.0f, 0.95f, 1.0f, 0.95f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnimation.duration = 100
        scaleAnimation.repeatMode = Animation.REVERSE
        scaleAnimation.repeatCount = 1
        button.startAnimation(scaleAnimation)
    }

    private fun refreshAllData() {
        scope.launch {
            updateStatus("Reading system properties...")
            try {
                val currentLogSize = getCurrentLogLimit()
                val logFolderSize = calculateLogFolderSize()
                val storageInfo = getStorageInfo()

                withContext(Dispatchers.Main) {
                    updateCard1(currentLogSize)
                    updateCard2(logFolderSize)
                    updateCard3(storageInfo.first, storageInfo.second)

                    checkAlreadyFixed(currentLogSize)
                    checkPersistentLoggingDisabled()

                    val savings = calculatePotentialSavings(currentLogSize)
                    if (savings > 0) {
                        updateStatus("Analysis complete. Current: ${currentLogSize} MB. Potential savings: ~${savings} MB")
                    } else {
                        updateStatus("Analysis complete. Current limit: ${currentLogSize} MB")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Error reading properties: ${sanitizeMessage(e.message)}\nTap RETRY to attempt again.")
                    retryButton.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Sanitizes error messages to prevent information leakage.
     * Only allows safe characters and limits length.
     */
    private fun sanitizeMessage(message: String?): String {
        if (message.isNullOrEmpty()) return "Unknown error"
        // Remove potentially dangerous characters and limit length
        val sanitized = message.replace(Regex("[^a-zA-Z0-9 .,!?\\-_]"), "")
        return if (sanitized.length > 200) sanitized.take(197) + "..." else sanitized
    }

    /**
     * Calculates potential storage savings if fix is applied.
     * Returns the difference between current size and target size.
     */
    private fun calculatePotentialSavings(currentSize: Int): Long {
        return if (currentSize > TARGET_LOG_SIZE) (currentSize - TARGET_LOG_SIZE).toLong() else 0L
    }

    @SuppressLint("PrivateApi")
    private fun getCurrentLogLimit(): Int {
        var size = DEFAULT_LOG_SIZE

        // Try reflection first for persist.logd.logpersistd.size
        try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod: Method = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            
            val persistSizeStr = getMethod.invoke(null, "persist.logd.logpersistd.size", "") as? String
            if (!persistSizeStr.isNullOrEmpty()) {
                val parsedSize = persistSizeStr.toIntOrNull()
                if (parsedSize != null && parsedSize in VALID_LOG_SIZES) {
                    size = parsedSize
                }
            } else {
                // Fallback to logd.logpersistd.size
                val sizeStr = getMethod.invoke(null, "logd.logpersistd.size", "") as? String
                if (!sizeStr.isNullOrEmpty()) {
                    val parsedSize = sizeStr.toIntOrNull()
                    if (parsedSize != null && parsedSize in VALID_LOG_SIZES) {
                        size = parsedSize
                    }
                }
            }
        } catch (e: ClassNotFoundException) {
            // Reflection failed, fallback to Runtime.exec
            size = getPropViaRuntime("persist.logd.logpersistd.size")
            if (size == DEFAULT_LOG_SIZE || size !in VALID_LOG_SIZES) {
                size = getPropViaRuntime("logd.logpersistd.size")
            }
        } catch (e: NoSuchMethodException) {
            // Method not found, use default
            size = DEFAULT_LOG_SIZE
        } catch (e: SecurityException) {
            // Permission denied, use default
            size = DEFAULT_LOG_SIZE
        } catch (e: Exception) {
            // Any other exception, use default
            size = DEFAULT_LOG_SIZE
        }

        return size
    }

    /**
     * Executes getprop command via Runtime.exec with proper resource cleanup.
     * Returns parsed integer value or DEFAULT_LOG_SIZE on failure.
     */
    private fun getPropViaRuntime(propName: String): Int {
        var process: Process? = null
        var reader: BufferedReader? = null
        return try {
            process = Runtime.getRuntime().exec("getprop $propName")
            // Set timeout to prevent hanging
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                return DEFAULT_LOG_SIZE
            }
            reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            val value = line?.trim()?.toIntOrNull() ?: DEFAULT_LOG_SIZE
            value
        } catch (e: Exception) {
            DEFAULT_LOG_SIZE
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            process?.destroy()
        }
    }

    /**
     * Checks if persistent logging is enabled by reading the buffer property.
     * Returns true if the property exists and is non-empty, false otherwise.
     */
    private fun isPersistentLoggingEnabled(): Boolean {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod: Method = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            val bufferValue = getMethod.invoke(null, "persist.logd.logpersistd.buffer", "") as? String
            !bufferValue.isNullOrEmpty()
        } catch (e: ClassNotFoundException) {
            // Cannot determine, assume enabled to be safe
            true
        } catch (e: Exception) {
            // If we can't read the property, assume it might be enabled
            true
        }
    }

    private fun checkPersistentLoggingDisabled() {
        if (!isPersistentLoggingEnabled()) {
            updateStatus("Logging is off. Nothing to fix.")
            fixButton.isEnabled = false
            fixButton.alpha = 0.5f
        }
    }

    private fun checkAlreadyFixed(currentSize: Int) {
        if (currentSize <= TARGET_LOG_SIZE) {
            updateStatus("Device is already optimized. Log limit is ${currentSize} MB.")
            fixButton.isEnabled = false
            fixButton.alpha = 0.5f
        }
    }

    private fun calculateLogFolderSize(): Long {
        val logDir = File("/data/misc/logd")
        return if (logDir.exists() && logDir.isDirectory) {
            try {
                logDir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
            } catch (e: SecurityException) {
                -1L // Permission denied
            } catch (e: Exception) {
                -1L
            }
        } else {
            0L
        }
    }

    private fun getStorageInfo(): Pair<Long, Long> {
        return try {
            val statFs = StatFs("/data")
            val totalBytes = statFs.totalBytes
            val availableBytes = statFs.availableBytes
            val totalMB = totalBytes / (1024 * 1024)
            val availableMB = availableBytes / (1024 * 1024)
            Pair(availableMB, totalMB)
        } catch (e: Exception) {
            Pair(-1L, -1L)
        }
    }

    private fun updateCard1(size: Int) {
        card1Value.text = "$size MB"
        card1Value.setTextColor(if (size > TARGET_LOG_SIZE) getColorCompat(R.color.status_red) else getColorCompat(R.color.status_green))
    }

    private fun updateCard2(sizeBytes: Long) {
        if (sizeBytes < 0) {
            card2Value.text = "N/A (No Access)"
            card2Value.setTextColor(getColorCompat(R.color.status_yellow))
        } else {
            val sizeMB = sizeBytes / (1024 * 1024)
            card2Value.text = "$sizeMB MB"
            card2Value.setTextColor(if (sizeMB > 100) getColorCompat(R.color.status_red) else getColorCompat(R.color.status_green))
        }
    }

    private fun updateCard3(availableMB: Long, totalMB: Long) {
        if (availableMB < 0 || totalMB < 0) {
            card3Value.text = "N/A"
        } else {
            card3Value.text = "$availableMB MB / $totalMB MB"
        }
    }

    private fun getColorCompat(colorResId: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getColor(colorResId)
        } else {
            @Suppress("DEPRECATION")
            resources.getColor(colorResId)
        }
    }

    private fun applyFix() {
        scope.launch {
            withContext(Dispatchers.Main) {
                retryButton.visibility = View.GONE
                copyAdbButton.visibility = View.GONE
            }

            updateStatus("Checking for root access...")
            
            val isRooted = checkRootAccess()
            
            if (isRooted) {
                updateStatus("Root detected. Applying fix...")
                val success = applyRootFix()
                
                if (success) {
                    withContext(Dispatchers.Main) {
                        saveFixState(true, getCurrentLogLimit(), TARGET_LOG_SIZE)
                        restoreButton.isEnabled = true
                        restoreButton.alpha = 1.0f
                        updateStatus("Success! Fix applied. Log limit set to $TARGET_LOG_SIZE MB.\nReboot recommended for changes to take full effect.")
                        refreshAllData()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateStatus("Root command failed. See ADB method below.")
                        showAdbButton()
                    }
                }
            } else {
                updateStatus("No root access detected. Using ADB method...")
                showAdbButton()
            }
        }
    }

    /**
     * Checks for root access by looking for su binary in common locations
     * and attempting to execute it. Returns true only if su is found and executable.
     */
    private fun checkRootAccess(): Boolean {
        var process: Process? = null
        var reader: BufferedReader? = null
        return try {
            // Check for su binary in common locations with execute permission
            val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su")
            for (path in suPaths) {
                val suFile = File(path)
                if (suFile.exists() && suFile.canExecute()) {
                    return true
                }
            }
            
            // Try executing which su as fallback
            process = Runtime.getRuntime().exec("which su")
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                return false
            }
            reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            !result.isNullOrEmpty()
        } catch (e: Exception) {
            false
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            process?.destroy()
        }
    }

    /**
     * Applies the log size fix using root privileges.
     * Executes setprop commands via su and verifies the changes.
     * Returns true if all commands succeed and verification passes.
     */
    private fun applyRootFix(): Boolean {
        var process: Process? = null
        return try {
            val commands = listOf(
                "setprop persist.logd.logpersistd.size $TARGET_LOG_SIZE",
                "setprop logd.logpersistd.size $TARGET_LOG_SIZE",
                "rm -rf /data/misc/logd/*",
                "logcat -c"
            )

            var allSuccess = true
            for (cmd in commands) {
                process = null
                try {
                    process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                    if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        process.destroy()
                        allSuccess = false
                        updateStatus("Command timeout: $cmd")
                        continue
                    }
                    if (process.exitValue() != 0) {
                        allSuccess = false
                        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                        val errorText = errorReader.use { it.readText() }
                        val safeError = sanitizeMessage(errorText)
                        updateStatus("Command failed: $cmd\nError: $safeError")
                    }
                } finally {
                    process?.destroy()
                }
            }

            // Wait for property change to propagate
            Thread.sleep(2000)
            
            // Verify the change was applied
            val newSize = getCurrentLogLimit()
            if (newSize > TARGET_LOG_SIZE) {
                allSuccess = false
                updateStatus("Verification failed: Log size is still ${newSize} MB")
            }

            allSuccess
        } catch (e: Exception) {
            updateStatus("Root fix error: ${sanitizeMessage(e.message)}")
            false
        }
    }

    private fun showAdbButton() {
        copyAdbButton.visibility = View.VISIBLE
        updateStatus("Copy the ADB commands below and execute them from your computer:\n\n" +
            "adb shell setprop persist.logd.logpersistd.size $TARGET_LOG_SIZE\n" +
            "adb shell setprop logd.logpersistd.size $TARGET_LOG_SIZE\n" +
            "adb shell rm -rf /data/misc/logd/*\n" +
            "adb shell logcat -c\n\n" +
            "Then reboot your device.")
    }

    private fun copyAdbCommandsToClipboard() {
        val adbCommands = """# LogKiller ADB Commands
# Copy and paste these into your terminal

adb shell setprop persist.logd.logpersistd.size $TARGET_LOG_SIZE
adb shell setprop logd.logpersistd.size $TARGET_LOG_SIZE
adb shell rm -rf /data/misc/logd/*
adb shell logcat -c

# Reboot your device after running these commands
""".trimIndent()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ADB Commands", adbCommands)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, "ADB commands copied to clipboard!", Toast.LENGTH_LONG).show()
        updateStatus("ADB commands copied to clipboard!\nPaste them into your terminal and execute.\nThen reboot your device.")
    }

    /**
     * Restores the default log size (512 MB).
     * Uses root if available, otherwise provides ADB commands for manual execution.
     */
    private fun restoreDefaults() {
        var process: Process? = null
        scope.launch {
            updateStatus("Restoring default log size (512 MB)...")
            
            val isRooted = checkRootAccess()
            
            if (isRooted) {
                val success = try {
                    val commands = listOf(
                        "setprop persist.logd.logpersistd.size $DEFAULT_LOG_SIZE",
                        "setprop logd.logpersistd.size $DEFAULT_LOG_SIZE"
                    )

                    var allSuccess = true
                    for (cmd in commands) {
                        process = null
                        try {
                            process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                process.destroy()
                                allSuccess = false
                                continue
                            }
                            if (process.exitValue() != 0) {
                                allSuccess = false
                            }
                        } finally {
                            process?.destroy()
                        }
                    }
                    allSuccess
                } catch (e: Exception) {
                    false
                }

                if (success) {
                    saveFixState(false, DEFAULT_LOG_SIZE, DEFAULT_LOG_SIZE)
                    restoreButton.isEnabled = false
                    fixButton.isEnabled = true
                    fixButton.alpha = 1.0f
                    updateStatus("Defaults restored. Log limit set back to $DEFAULT_LOG_SIZE MB.")
                    refreshAllData()
                } else {
                    updateStatus("Failed to restore defaults. Try using ADB.")
                }
            } else {
                val adbRestoreCommands = """# Restore Default Log Size (512 MB)
# Note: This may not work on all devices as persist properties are often protected
adb shell setprop persist.logd.logpersistd.size $DEFAULT_LOG_SIZE
adb shell setprop logd.logpersistd.size $DEFAULT_LOG_SIZE
""".trimIndent()

                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ADB Restore Commands", adbRestoreCommands)
                clipboard.setPrimaryClip(clip)

                updateStatus("No root. ADB restore commands copied to clipboard.\nNote: Setting persist properties via ADB may require special permissions on some devices.")
            }
        }
    }

    private fun saveFixState(applied: Boolean, original: Int, new: Int) {
        prefs.edit().apply {
            putBoolean(KEY_FIX_APPLIED, applied)
            putInt(KEY_ORIGINAL_SIZE, original)
            putInt(KEY_NEW_SIZE, new)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            apply()
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

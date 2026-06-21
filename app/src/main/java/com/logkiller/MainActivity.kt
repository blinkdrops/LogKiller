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
        private const val DEFAULT_LOG_SIZE = 512
        private const val TARGET_LOG_SIZE = 60
        private const val PREFS_NAME = "logkiller_prefs"
        private const val KEY_FIX_APPLIED = "fix_applied"
        private const val KEY_ORIGINAL_SIZE = "original_size"
        private const val KEY_NEW_SIZE = "new_size"
        private const val KEY_TIMESTAMP = "timestamp"
        
        private val VALID_LOG_SIZES = setOf(16, 32, 60, 128, 256, 512, 1024, 4096)
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

                    updateStatus("Analysis complete. Current limit: ${currentLogSize} MB")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus("Error reading properties: ${e.message}\nTap RETRY to attempt again.")
                    retryButton.visibility = View.VISIBLE
                }
            }
        }
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
                size = persistSizeStr.toIntOrNull() ?: DEFAULT_LOG_SIZE
            } else {
                // Fallback to logd.logpersistd.size
                val sizeStr = getMethod.invoke(null, "logd.logpersistd.size", "") as? String
                if (!sizeStr.isNullOrEmpty()) {
                    size = sizeStr.toIntOrNull() ?: DEFAULT_LOG_SIZE
                }
            }
        } catch (e: ClassNotFoundException) {
            // Reflection failed, fallback to Runtime.exec
            size = getPropViaRuntime("persist.logd.logpersistd.size")
            if (size == DEFAULT_LOG_SIZE) {
                size = getPropViaRuntime("logd.logpersistd.size")
            }
        } catch (e: Exception) {
            // Any other exception, use default
            size = DEFAULT_LOG_SIZE
        }

        return size
    }

    private fun getPropViaRuntime(propName: String): Int {
        return try {
            val process = Runtime.getRuntime().exec("getprop $propName")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val value = reader.readLine()?.trim()?.toIntOrNull() ?: DEFAULT_LOG_SIZE
            reader.close()
            process.waitFor()
            value
        } catch (e: Exception) {
            DEFAULT_LOG_SIZE
        }
    }

    private fun isPersistentLoggingEnabled(): Boolean {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod: Method = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            val bufferValue = getMethod.invoke(null, "persist.logd.logpersistd.buffer", "") as? String
            !bufferValue.isNullOrEmpty()
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

    private fun checkRootAccess(): Boolean {
        return try {
            // Check for su binary in common locations
            val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su")
            for (path in suPaths) {
                if (File(path).exists()) {
                    return true
                }
            }
            
            // Try executing which su
            val process = Runtime.getRuntime().exec("which su")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            reader.close()
            process.waitFor()
            
            !result.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun applyRootFix(): Boolean {
        return try {
            val commands = listOf(
                "setprop persist.logd.logpersistd.size $TARGET_LOG_SIZE",
                "setprop logd.logpersistd.size $TARGET_LOG_SIZE",
                "rm -rf /data/misc/logd/*",
                "logcat -c"
            )

            var allSuccess = true
            for (cmd in commands) {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                process.waitFor()
                if (process.exitValue() != 0) {
                    allSuccess = false
                    val errorStream = BufferedReader(InputStreamReader(process.errorStream)).readText()
                    updateStatus("Command failed: $cmd\nError: $errorStream")
                }
            }

            // Wait and verify
            Thread.sleep(2000)
            val newSize = getCurrentLogLimit()
            
            if (newSize > TARGET_LOG_SIZE) {
                allSuccess = false
            }

            allSuccess
        } catch (e: Exception) {
            updateStatus("Root fix error: ${e.message}")
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

    private fun restoreDefaults() {
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
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                        process.waitFor()
                        if (process.exitValue() != 0) {
                            allSuccess = false
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
adb shell setprop persist.logd.logpersistd.size $DEFAULT_LOG_SIZE
adb shell setprop logd.logpersistd.size $DEFAULT_LOG_SIZE
""".trimIndent()

                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ADB Restore Commands", adbRestoreCommands)
                clipboard.setPrimaryClip(clip)

                updateStatus("No root. ADB restore commands copied to clipboard:\n$adbRestoreCommands")
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

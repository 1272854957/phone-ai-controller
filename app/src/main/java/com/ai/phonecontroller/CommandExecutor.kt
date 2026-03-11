package com.ai.phonecontroller

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.FileOutputStream

class CommandExecutor(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * Launch an app by package name
     */
    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                // Try to open app settings
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Click at coordinates using accessibility service
     * Note: For full functionality, you need to implement an AccessibilityService
     */
    fun click(x: Int, y: Int): Boolean {
        return try {
            // Method 1: Using shell input tap (requires root or ADB)
            val process = Runtime.getRuntime().exec("input tap $x $y")
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Swipe on screen
     */
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 300): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("input swipe $x1 $y1 $x2 $y2 $duration")
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Input text (uses clipboard as workaround)
     */
    fun inputText(text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("input", text)
            clipboard.setPrimaryClip(clip)
            
            // Then simulate paste
            Runtime.getRuntime().exec("input keyevent 279") // PASTE
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Press back button
     */
    fun pressBack(): Boolean {
        return try {
            Runtime.getRuntime().exec("input keyevent 4").waitFor()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Press home button
     */
    fun pressHome(): Boolean {
        return try {
            Runtime.getRuntime().exec("input keyevent 3").waitFor()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Press recent apps button
     */
    fun pressRecent(): Boolean {
        return try {
            Runtime.getRuntime().exec("input keyevent 187").waitFor()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Take screenshot using MediaProjection (Android 5.0+)
     */
    fun screenshot(): File? {
        return try {
            // Check if we have required permissions
            if (Settings.canDrawOverlays(context)) {
                takeScreenshot()
            } else {
                // Fallback to screencap command
                takeScreenshotCmd()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            takeScreenshotCmd()
        }
    }
    
    private fun takeScreenshotCmd(): File? {
        return try {
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "screenshot_${System.currentTimeMillis()}.png"
            )
            
            val process = Runtime.getRuntime().exec("screencap -p ${file.absolutePath}")
            process.waitFor()
            
            if (file.exists() && file.length() > 0) file else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun takeScreenshot(): File? {
        // This is a simplified version - full implementation would need MediaProjection
        // For now, use the command-line approach
        return takeScreenshotCmd()
    }
    
    /**
     * Open URL in browser
     */
    fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Send SMS (requires SMS permission)
     */
    fun sendSms(phoneNumber: String, message: String): Boolean {
        return try {
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Make a phone call
     */
    fun makeCall(phoneNumber: String): Boolean {
        return try {
            val callIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

package com.ai.phonecontroller

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class HttpServer(
    port: Int,
    private val prefs: SharedPreferences,
    private val context: Context,
    private val statusCallback: (String) -> Unit
) : NanoHTTPD(port) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandExecutor = CommandExecutor(context, prefs)
    
    companion object {
        private const val TAG = "PhoneAIController"
    }
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "$method $uri")
        
        // Check token
        val token = session.parameters["token"]?.firstOrNull()
            ?: session.headers["authorization"]?.removePrefix("Bearer ")
        
        val correctToken = prefs.getString("token", null)
        if (token != correctToken) {
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                MIME_PLAINTEXT,
                "Invalid token"
            )
        }
        
        return when (uri) {
            "/health" -> handleHealth()
            "/exec" -> handleExec(session)
            "/screenshot" -> handleScreenshot()
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not found"
            )
        }
    }
    
    private fun handleHealth(): Response {
        val result = mapOf(
            "status" to "ok",
            "device" to Build.MODEL,
            "version" to Build.VERSION.RELEASE
        )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            com.squareup.moshi.Moshi.Builder().build().adapter(Map::class.java).toJson(result)
        )
    }
    
    private fun handleExec(session: IHTTPSession): Response {
        val params = session.parameters
        val action = params["action"]?.firstOrNull() ?: return errorResponse("Missing action")
        
        // Check permission
        val hasPermission = when (action) {
            "launch_app" -> prefs.getBoolean("perm_launch_app", true)
            "click", "swipe" -> prefs.getBoolean("perm_click", false)
            "input_text" -> prefs.getBoolean("perm_input", false)
            "screenshot" -> prefs.getBoolean("perm_screenshot", false)
            "get_clipboard" -> prefs.getBoolean("perm_input", false)
            else -> true
        }
        
        if (!hasPermission) {
            return errorResponse("Permission denied for action: $action")
        }
        
        return runCatching {
            when (action) {
                "launch_app" -> {
                    val packageName = params["package"]?.firstOrNull()
                        ?: params["app"]?.firstOrNull()
                        ?: return errorResponse("Missing package name")
                    commandExecutor.launchApp(packageName)
                    okResponse("App launched: $packageName")
                }
                
                "click" -> {
                    val x = params["x"]?.firstOrNull()?.toIntOrNull()
                        ?: return errorResponse("Missing x coordinate")
                    val y = params["y"]?.firstOrNull()?.toIntOrNull()
                        ?: return errorResponse("Missing y coordinate")
                    commandExecutor.click(x, y)
                    okResponse("Clicked at ($x, $y)")
                }
                
                "swipe" -> {
                    val x1 = params["x1"]?.firstOrNull()?.toIntOrNull() ?: 0
                    val y1 = params["y1"]?.firstOrNull()?.toIntOrNull() ?: 0
                    val x2 = params["x2"]?.firstOrNull()?.toIntOrNull() ?: 0
                    val y2 = params["y2"]?.firstOrNull()?.toIntOrNull() ?: 0
                    val duration = params["duration"]?.firstOrNull()?.toIntOrNull() ?: 300
                    commandExecutor.swipe(x1, y1, x2, y2, duration)
                    okResponse("Swiped from ($x1,$y1) to ($x2,$y2)")
                }
                
                "input_text" -> {
                    val text = params["text"]?.firstOrNull()
                        ?: return errorResponse("Missing text")
                    commandExecutor.inputText(text)
                    okResponse("Input text: $text")
                }
                
                "press_back" -> {
                    commandExecutor.pressBack()
                    okResponse("Back pressed")
                }
                
                "press_home" -> {
                    commandExecutor.pressHome()
                    okResponse("Home pressed")
                }
                
                "press_recent" -> {
                    commandExecutor.pressRecent()
                    okResponse("Recent apps pressed")
                }
                
                else -> errorResponse("Unknown action: $action")
            }
        }.getOrElse { e ->
            errorResponse(e.message ?: "Unknown error")
        }
    }
    
    private fun handleScreenshot(): Response {
        if (!prefs.getBoolean("perm_screenshot", false)) {
            return errorResponse("Permission denied")
        }
        
        return try {
            val file = commandExecutor.screenshot()
            if (file != null && file.exists()) {
                val bytes = file.readBytes()
                file.delete()
                newFixedLengthResponse(
                    Response.Status.OK,
                    "image/png",
                    bytes
                ).apply {
                    addHeader("Content-Disposition", "attachment; filename=screenshot.png")
                }
            } else {
                errorResponse("Screenshot failed")
            }
        } catch (e: Exception) {
            errorResponse(e.message ?: "Screenshot error")
        }
    }
    
    private fun okResponse(message: String): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"success":true,"message":"$message"}"""
        )
    }
    
    private fun errorResponse(message: String): Response {
        return newFixedLengthResponse(
            Response.Status.BAD_REQUEST,
            "application/json",
            """{"success":false,"error":"$message"}"""
        )
    }
    
    override fun start() {
        super.start()
        statusCallback("服务运行中...")
    }
    
    override fun stop() {
        super.stop()
        scope.cancel()
        statusCallback("服务已停止")
    }
}

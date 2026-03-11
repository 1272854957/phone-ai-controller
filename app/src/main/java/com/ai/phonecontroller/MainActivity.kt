package com.ai.phonecontroller

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var prefs: SharedPreferences
    private var server: HttpServer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // UI Elements
    private lateinit var statusText: TextView
    private lateinit var addressText: TextView
    private lateinit var tokenText: TextView
    private lateinit var switchServer: Switch
    private lateinit var permissionLayout: LinearLayout
    
    // Permission switches
    private var switchLaunchApp: Switch? = null
    private var switchClick: Switch? = null
    private var switchInput: Switch? = null
    private var switchScreenshot: Switch? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        prefs = getSharedPreferences("phone_ai_ctrl", Context.MODE_PRIVATE)
        
        initViews()
        loadSettings()
        checkOverlayPermission()
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        addressText = findViewById(R.id.addressText)
        tokenText = findViewById(R.id.tokenText)
        switchServer = findViewById(R.id.switchServer)
        permissionLayout = findViewById(R.id.permissionLayout)
        
        switchLaunchApp = findViewById(R.id.switchLaunchApp)
        switchClick = findViewById(R.id.switchClick)
        switchInput = findViewById(R.id.switchInput)
        switchScreenshot = findViewById(R.id.switchScreenshot)
        
        // Server toggle
        switchServer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startServer()
            } else {
                stopServer()
            }
        }
        
        // Permission toggles - save immediately
        switchLaunchApp?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("perm_launch_app", isChecked).apply()
        }
        switchClick?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("perm_click", isChecked).apply()
        }
        switchInput?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("perm_input", isChecked).apply()
        }
        switchScreenshot?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("perm_screenshot", isChecked).apply()
        }
        
        // Generate new token button
        findViewById<Button>(R.id.btnNewToken).setOnClickListener {
            generateNewToken()
        }
    }
    
    private fun loadSettings() {
        // Load token
        var token = prefs.getString("token", null)
        if (token == null) {
            token = generateToken()
            prefs.edit().putString("token", token).apply()
        }
        tokenText.text = "授权码: ${token?.take(4)}****${token?.takeLast(4)}"
        
        // Load permissions
        switchLaunchApp?.isChecked = prefs.getBoolean("perm_launch_app", true)
        switchClick?.isChecked = prefs.getBoolean("perm_click", false)
        switchInput?.isChecked = prefs.getBoolean("perm_input", false)
        switchScreenshot?.isChecked = prefs.getBoolean("perm_screenshot", false)
        
        // Auto-start server if was running
        if (prefs.getBoolean("server_running", false)) {
            switchServer.isChecked = true
        }
    }
    
    private fun generateToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }
    
    private fun generateNewToken() {
        AlertDialog.Builder(this)
            .setTitle("确认")
            .setMessage("确定要生成新的授权码吗？旧的授权码将失效！")
            .setPositiveButton("确定") { _, _ ->
                val newToken = generateToken()
                prefs.edit().putString("token", newToken).apply()
                tokenText.text = "授权码: ${newToken.take(4)}****${newToken.takeLast(4)}"
                Toast.makeText(this, "新授权码已生成", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun startServer() {
        val port = prefs.getInt("port", 8080)
        server = HttpServer(port, prefs, this) { status ->
            runOnUiThread {
                statusText.text = status
            }
        }
        scope.launch {
            try {
                server?.start()
                val ip = getLocalIPAddress()
                addressText.text = "http://$ip:$port"
                prefs.edit().putBoolean("server_running", true).apply()
                Toast.makeText(this@MainActivity, "服务已启动", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                switchServer.isChecked = false
                statusText.text = "启动失败: ${e.message}"
                Toast.makeText(this@MainActivity, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun stopServer() {
        server?.stop()
        server = null
        statusText.text = "服务已停止"
        addressText.text = "-"
        prefs.edit().putBoolean("server_running", false).apply()
    }
    
    private fun getLocalIPAddress(): String? {
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifi.connectionInfo.ipAddress
            return String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            // Try network interfaces
            try {
                val en = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val intf = en.nextElement()
                    val addr = intf.inetAddresses
                    while (addr.hasMoreElements()) {
                        val address = addr.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return "127.0.0.1"
    }
    
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("需要悬浮窗权限才能执行屏幕操作，是否开启？")
                .setPositiveButton("去开启") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
                .setNegativeButton("稍后", null)
                .show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        server?.stop()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle("关于")
                    .setMessage("手机AI控制器 v1.0\n\n安全、便携的手机远程控制服务")
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

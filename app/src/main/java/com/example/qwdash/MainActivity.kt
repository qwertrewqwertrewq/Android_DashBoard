package com.example.qwdash

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.util.VLCVideoLayout
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val SCREEN_OFF_DELAY = 30000L // 30秒
        private const val BRIGHTNESS_KEEP_LOW_INTERVAL = 30000L // 30秒循环降低亮度
        private const val TAG = "MainActivity"
    }
    
    private lateinit var statusTextView: TextView
    private lateinit var statusScrollView: ScrollView
    private lateinit var blackOverlay: View
    private lateinit var videoView: VLCVideoLayout
    private lateinit var snapshotImageView: ImageView
    private lateinit var videoContainer: FrameLayout
    private var videoRotationContainer: FrameLayout? = null  // 仅在竖屏布局中存在
    private val buttons = mutableListOf<Button>()
    
    // RTSP 视频流管理器
    private var rtspStreamManager: RtspStreamManager? = null
    
    // HTTP配置服务器
    private var httpServer: ConfigHttpServer? = null
    
    // WakeLock 保持 CPU 运行
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 定时器
    private val handler = Handler(Looper.getMainLooper())
    private val screenOffRunnable = Runnable {
        turnOffScreen()
    }
    
    // 周期性降低亮度任务（容错机制）
    private val brightnessKeepLowRunnable = object : Runnable {
        override fun run() {
            thread {
                ScreenControl.turnOff(force = true)  // 强制执行
                runOnUiThread {
                    android.util.Log.d(TAG, "定期降低亮度（容错）")
                    updateStatus("定期降低亮度（容错）\n", append = true)
                }
            }
            // 30秒后再次执行
            handler.postDelayed(this, BRIGHTNESS_KEEP_LOW_INTERVAL)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 隐藏标题栏
        supportActionBar?.hide()
        
        // 初始化配置管理器
        ConfigManager.init(this)
        
        // 根据配置选择布局
        val layoutId = if (ConfigManager.isHorizontalLayout()) {
            R.layout.activity_main_horizontal
        } else {
            R.layout.activity_main
        }
        setContentView(layoutId)
        
        // 初始化控件
        statusTextView = findViewById(R.id.statusTextView)
        statusScrollView = findViewById(R.id.statusScrollView)
        blackOverlay = findViewById(R.id.blackOverlay)
        videoView = findViewById(R.id.videoView)
        snapshotImageView = findViewById(R.id.snapshotImageView)
        videoContainer = findViewById(R.id.videoContainer)
        videoRotationContainer = findViewById(R.id.videoRotationContainer)
        
        // 设置视频旋转（仅在竖屏布局中有该控件）
        if (videoRotationContainer != null) {
            setupVideoRotation()
        }
        
        // 设置按钮
        setupButtons()
        
        // 初始化 RTSP 视频流
        setupVideoStream()
        
        // 启动前台服务保活
        ForegroundService.startService(this)
        
        // 获取 WakeLock
        acquireWakeLock()
        
        // 设置触摸监听
        setupTouchListener()
        
        // 在后台线程中请求 Root 权限并测试
        thread {
            requestRootAndTestBacklight()
        }
        
        updateStatus("应用已启动\n")
        
        // 启动HTTP配置服务器
        startHttpServer()
        
        // 启动定时器
        resetTimer()
    }
    
    /**
     * 获取 WakeLock 保持 CPU 运行
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "BacklightController::WakeLock"
        )
        wakeLock?.acquire()
        updateStatus("WakeLock 已获取\n")
    }
    
    /**
     * 设置触摸监听
     */
    private fun setupTouchListener() {
        // 创建通用触摸监听器
        val touchListener = View.OnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                android.util.Log.d(TAG, "Touch detected on ${view.javaClass.simpleName}")
                onUserActivity()
            }
            false  // 不消费事件，让其他监听器也能响应
        }
        
        // 为根布局设置触摸监听
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setOnTouchListener(touchListener)
        
        // 为视频容器设置触摸监听
        videoContainer.setOnTouchListener(touchListener)
        
        // 为状态文本设置点击监听
        statusTextView.setOnClickListener {
            android.util.Log.d(TAG, "Click detected on statusTextView")
            onUserActivity()
        }
    }
    
    /**
     * 设置按钮
     */
    private fun setupButtons() {
        // 获取所有按钮
        buttons.add(findViewById(R.id.button1))
        buttons.add(findViewById(R.id.button2))
        buttons.add(findViewById(R.id.button3))
        buttons.add(findViewById(R.id.button4))
        buttons.add(findViewById(R.id.button5))
        buttons.add(findViewById(R.id.button6))
        buttons.add(findViewById(R.id.button7))
        buttons.add(findViewById(R.id.button8))
        
        // 8个按钮全部从配置加载
        for (i in 0..7) {
            val buttonData = ConfigManager.getButtonConfig(i)
            buttons[i].text = buttonData.name
            buttons[i].setOnClickListener {
                onButtonClick(i, buttonData)
            }
        }
    }
    
    /**
     * 按钮点击事件
     */
    private fun onButtonClick(index: Int, buttonData: ButtonData) {
        if (buttonData.curlCommand.isBlank()) {
            showToast("按钮未配置")
            updateStatus("按钮${index + 1}未配置\\n", append = true)
            return
        }
        
        updateStatus("\\n=== 执行: ${buttonData.name} ===\\n", append = true)
        updateStatus("Curl: ${buttonData.curlCommand}\\n", append = true)
        
        CurlExecutor.execute(buttonData.curlCommand) { success, response ->
            runOnUiThread {
                if (success) {
                    updateStatus("\\n$response\\n", append = true)
                    showToast("请求成功")
                } else {
                    updateStatus("\\n错误: $response\\n", append = true)
                    showToast("请求失败")
                }
            }
        }
    }
    
    
    /**
     * 设置视频旋转（横屏16:9旋转为竖屏9:16，高度占满屏幕）
     */
    private fun setupVideoRotation() {
        // 仅在竖屏布局中执行（该布局中有videoRotationContainer）
        val rotationContainer = videoRotationContainer ?: return
        
        videoContainer.post {
            // 获取屏幕高度
            val screenHeight = videoContainer.height
            
            // 计算视频容器宽度（9:16竖屏比例）
            val containerWidth = (screenHeight * 9f / 16f).toInt()
            
            // 设置视频容器宽度
            val containerParams = videoContainer.layoutParams
            containerParams.width = containerWidth
            videoContainer.layoutParams = containerParams
            
            // 视频原始尺寸（16:9横屏，高度填满屏幕）
            val videoWidth = (screenHeight * 16f / 9f).toInt()  // 宽度 = 高度 * 16/9
            val videoHeight = screenHeight
            
            // 设置旋转容器尺寸为横屏视频尺寸
            val rotationParams = rotationContainer.layoutParams
            rotationParams.width = videoWidth
            rotationParams.height = videoHeight
            rotationContainer.layoutParams = rotationParams
            
            // 设置旋转中心点（容器中心）
            rotationContainer.pivotX = (videoWidth / 2).toFloat()
            rotationContainer.pivotY = (videoHeight / 2).toFloat()
            
            // 顺时针旋转90度
            rotationContainer.rotation = 90f
            
            // 旋转后宽高互换：原宽(videoWidth)变成高，原高(videoHeight)变成宽
            // 需要缩放让旋转后的宽(原高videoHeight)适应容器宽度containerWidth
            val scale = containerWidth.toFloat() / videoHeight.toFloat()
            rotationContainer.scaleX = scale
            rotationContainer.scaleY = scale
            
            // 调整位置：顶部对齐
            rotationContainer.translationX = 0f
            rotationContainer.translationY = 0f
            
            android.util.Log.d(TAG, "Video rotation setup: screen=${containerWidth}x${screenHeight}, video=${videoWidth}x${videoHeight}, scale=$scale")
        }
    }
    
    /**
     * 设置视频流
     */
    private fun setupVideoStream() {
        try {
            val rtspUrl = ConfigManager.getRtspUrl()
            rtspStreamManager = RtspStreamManager(
                context = this,
                videoLayout = videoView,
                snapshotImageView = snapshotImageView,
                rtspUrl = rtspUrl
            )
            rtspStreamManager?.initialize()
            rtspStreamManager?.startLiveMode()
            updateStatus("RTSP 视频流已启动: $rtspUrl\n", append = true)
        } catch (e: Exception) {
            updateStatus("视频流启动失败: ${e.message}\n", append = true)
        }
    }
    
    /**
     * 用户活动检测（触摸或运动）
     */
    private fun onUserActivity() {
        android.util.Log.d(TAG, "User activity detected, turning on screen")
        turnOnScreen()
        resetTimer()
    }
    
    /**
     * 重置定时器
     */
    private fun resetTimer() {
        handler.removeCallbacks(screenOffRunnable)
        handler.postDelayed(screenOffRunnable, SCREEN_OFF_DELAY)
    }
    
    /**
     * 打开屏幕
     */
    private fun turnOnScreen() {
        // 停止周期性降低亮度任务
        handler.removeCallbacks(brightnessKeepLowRunnable)
        
        thread {
            ScreenControl.turnOn(force = true)  // 强制执行
            runOnUiThread {
                blackOverlay.visibility = View.GONE
                updateStatus("屏幕已打开\n", append = true)
            }
        }
    }
    
    /**
     * 降低屏幕亮度
     */
    private fun turnOffScreen() {
        thread {
            ScreenControl.turnOff()
            runOnUiThread {
                blackOverlay.visibility = View.GONE
                updateStatus("屏幕已降到最低亮度（30秒无操作）\n", append = true)
            }
        }
        
        // 启动周期性降低亮度任务（容错机制）
        handler.removeCallbacks(brightnessKeepLowRunnable)
        handler.postDelayed(brightnessKeepLowRunnable, BRIGHTNESS_KEEP_LOW_INTERVAL)
        android.util.Log.d(TAG, "已启动周期性降低亮度任务")
    }
    
    /**
     * 请求 Root 权限并测试背光路径查找
     */
    private fun requestRootAndTestBacklight() {
        updateStatus("正在请求 Root 权限...")
        
        // 初始化 Root Shell
        val hasRoot = RootShell.init()
        
        if (hasRoot) {
            updateStatus("Root 权限获取成功！\n")
            showToast("Root 权限获取成功")
            
            // 初始化屏幕控制
            updateStatus("正在初始化屏幕控制...\n", append = true)
            ScreenControl.init()
            
            val backlightPath = ScreenControl.getBacklightPath()
            if (backlightPath != null) {
                updateStatus("背光路径: $backlightPath\n", append = true)
                showToast("屏幕控制已就绪")
            } else {
                updateStatus("未找到背光路径\n", append = true)
                showToast("未找到背光路径")
            }
            
        } else {
            updateStatus("Root 权限获取失败！\n请确保设备已获取 Root 权限")
            showToast("Root 权限获取失败")
        }
    }
    
    /**
     * 更新状态文本
     */
    private fun updateStatus(text: String, append: Boolean = false) {
        runOnUiThread {
            if (append) {
                statusTextView.append(text)
            } else {
                statusTextView.text = text
            }
            statusScrollView.post {
                statusScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }
    
    /**
     * 显示 Toast 消息
     */
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 启动HTTP配置服务器
     */
    private fun startHttpServer() {
        thread {
            try {
                httpServer = ConfigHttpServer(8888)
                httpServer?.start()
                
                // 获取设备IP地址
                val ipAddress = getDeviceIpAddress()
                
                runOnUiThread {
                    val message = "HTTP服务器已启动\nhttp://$ipAddress:8888\n"
                    updateStatus(message, append = true)
                    showToast("配置服务器已启动: http://$ipAddress:8888")
                    android.util.Log.d(TAG, "HTTP服务器启动成功: $message")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    updateStatus("HTTP服务器启动失败: ${e.message}\n", append = true)
                    showToast("服务器启动失败: ${e.message}")
                    android.util.Log.e(TAG, "HTTP服务器启动失败", e)
                }
            }
        }
    }
    
    /**
     * 停止HTTP配置服务器
     */
    private fun stopHttpServer() {
        try {
            httpServer?.stop()
            httpServer = null
            updateStatus("HTTP服务器已停止\n", append = true)
            android.util.Log.d(TAG, "HTTP服务器已停止")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "停止HTTP服务器失败", e)
        }
    }
    
    /**
     * 获取设备IP地址
     */
    private fun getDeviceIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces) {
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "获取IP地址失败", e)
        }
        return "127.0.0.1"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 移除定时器
        handler.removeCallbacks(screenOffRunnable)
        handler.removeCallbacks(brightnessKeepLowRunnable)
        // 停止HTTP服务器
        stopHttpServer()
        // 释放视频流资源
        rtspStreamManager?.release()
        // 释放 WakeLock
        wakeLock?.release()
        // 停止前台服务
        ForegroundService.stopService(this)
    }
}


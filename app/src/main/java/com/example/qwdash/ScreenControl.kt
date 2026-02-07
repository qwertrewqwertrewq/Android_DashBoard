package com.example.qwdash

import android.util.Log

/**
 * 屏幕控制单例
 * 通过 Root 命令控制背光开关
 */
object ScreenControl {
    
    private const val TAG = "ScreenControl"
    
    private var backlightPath: String? = null
    private var maxBrightness: Int = 255
    private var minBrightness: Int = 1  // 最低亮度，避免完全黑屏
    private var isScreenOn: Boolean = true
    
    /**
     * 初始化屏幕控制
     * 查找背光路径和最大亮度值
     */
    fun init() {
        val paths = RootShell.findBacklightPaths()
        
        if (paths.isNotEmpty()) {
            backlightPath = paths[0]
            Log.d(TAG, "Using backlight path: $backlightPath")
            
            // 获取最大亮度值
            val maxPath = backlightPath?.replace("/brightness", "/max_brightness")
            if (maxPath != null) {
                val result = RootShell.exec("cat $maxPath").trim()
                try {
                    maxBrightness = result.toInt()
                    Log.d(TAG, "Max brightness: $maxBrightness")
                } catch (e: NumberFormatException) {
                    Log.w(TAG, "Failed to parse max brightness, using default: 255")
                    maxBrightness = 255
                }
            }
        } else {
            Log.e(TAG, "No backlight path found")
        }
    }
    
    /**
     * 打开屏幕（设置最大亮度）
     * @param force 是否强制执行（忽略状态检查）
     */
    fun turnOn(force: Boolean = false) {
        if (backlightPath == null) {
            Log.e(TAG, "Backlight path not initialized")
            return
        }
        
        if (!isScreenOn || force) {
            val result = RootShell.exec("echo $maxBrightness > $backlightPath")
            Log.d(TAG, "Screen turned ON (force=$force): $result")
            isScreenOn = true
        } else {
            Log.d(TAG, "Screen already ON, skipped")
        }
    }
    
    /**
     * 降低屏幕亮度到最低
     * @param force 是否强制执行（忽略状态检查）
     */
    fun turnOff(force: Boolean = false) {
        if (backlightPath == null) {
            Log.e(TAG, "Backlight path not initialized")
            return
        }
        
        if (isScreenOn || force) {
            val result = RootShell.exec("echo $minBrightness > $backlightPath")
            Log.d(TAG, "Screen dimmed to minimum (force=$force): $result")
            isScreenOn = false
        } else {
            Log.d(TAG, "Screen already OFF, skipped")
        }
    }
    
    /**
     * 获取当前屏幕状态
     */
    fun isScreenOn(): Boolean = isScreenOn
    
    /**
     * 获取背光路径
     */
    fun getBacklightPath(): String? = backlightPath
}

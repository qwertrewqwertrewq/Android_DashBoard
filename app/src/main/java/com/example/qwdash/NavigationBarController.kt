package com.example.qwdash

import android.util.Log

/**
 * 导航栏控制单例
 * 通过 Root 命令禁用/启用底部三个按键
 */
object NavigationBarController {
    
    private const val TAG = "NavigationBarController"
    private var isEnabled: Boolean = true
    
    /**
     * 禁用导航栏按键
     */
    fun disable() {
        try {
            // 禁用导航栏的多种方法
            
            // 方法1: 通过 wm overscan 隐藏导航栏
            RootShell.exec("wm overscan 0,0,0,-1000")
            
            // 方法2: 禁用虚拟按键
            RootShell.exec("settings put secure nav_bar_kids_mode 1")
            
            // 方法3: 修改系统属性
            RootShell.exec("setprop qemu.hw.mainkeys 1")
            
            isEnabled = false
            Log.d(TAG, "Navigation bar disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable navigation bar: ${e.message}")
        }
    }
    
    /**
     * 启用导航栏按键
     */
    fun enable() {
        try {
            // 恢复导航栏
            
            // 方法1: 重置 overscan
            RootShell.exec("wm overscan reset")
            
            // 方法2: 启用虚拟按键
            RootShell.exec("settings put secure nav_bar_kids_mode 0")
            
            // 方法3: 重置系统属性
            RootShell.exec("setprop qemu.hw.mainkeys 0")
            
            isEnabled = true
            Log.d(TAG, "Navigation bar enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable navigation bar: ${e.message}")
        }
    }
    
    /**
     * 获取当前状态
     */
    fun isNavigationBarEnabled(): Boolean = isEnabled
    
    /**
     * 切换导航栏状态
     */
    fun toggle(): Boolean {
        if (isEnabled) {
            disable()
        } else {
            enable()
        }
        return isEnabled
    }
}

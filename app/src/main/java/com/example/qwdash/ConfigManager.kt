package com.example.qwdash

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置管理器
 */
object ConfigManager {
    private const val PREFS_NAME = "app_config"
    private const val KEY_RTSP_URL = "rtsp_url"
    private const val KEY_BUTTONS = "buttons"
    private const val KEY_LAYOUT = "layout_mode"
    private const val KEY_FONT_SIZE = "font_size"
    private const val DEFAULT_RTSP_URL = "rtsp://192.168.31.17:8554/stream1"
    private const val LAYOUT_VERTICAL = "vertical"
    private const val LAYOUT_HORIZONTAL = "horizontal"
    private const val DEFAULT_FONT_SIZE = 16f
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 获取 RTSP 视频流地址
     */
    fun getRtspUrl(): String {
        return prefs.getString(KEY_RTSP_URL, DEFAULT_RTSP_URL) ?: DEFAULT_RTSP_URL
    }
    
    /**
     * 保存 RTSP 视频流地址
     */
    fun saveRtspUrl(url: String) {
        prefs.edit().putString(KEY_RTSP_URL, url).apply()
    }
    
    /**
     * 获取默认按钮颜色
     */
    private fun getDefaultColor(index: Int): String {
        return when (index) {
            0 -> "#FF5722"
            1 -> "#4CAF50"
            2 -> "#2196F3"
            3 -> "#FFC107"
            4 -> "#9C27B0"
            5 -> "#F44336"
            6 -> "#00BCD4"
            7 -> "#FF9800"
            else -> "#808080"
        }
    }
    
    /**
     * 获取按钮配置
     */
    fun getButtonConfig(index: Int): ButtonData {
        val buttonsJson = prefs.getString(KEY_BUTTONS, null)
        if (buttonsJson != null) {
            try {
                val jsonArray = JSONArray(buttonsJson)
                if (index < jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(index)
                    return ButtonData(
                        name = jsonObject.getString("name"),
                        curlCommand = jsonObject.getString("curl"),
                        color = jsonObject.optString("color", getDefaultColor(index))
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ButtonData("按钮${index + 1}", "", getDefaultColor(index))
    }
    
    /**
     * 保存按钮配置
     */
    fun saveButtonConfig(index: Int, buttonData: ButtonData) {
        val buttonsJson = prefs.getString(KEY_BUTTONS, null)
        val jsonArray = if (buttonsJson != null) {
            try {
                JSONArray(buttonsJson)
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }
        
        // 确保数组足够大
        while (jsonArray.length() <= index) {
            val defaultButton = JSONObject()
            val idx = jsonArray.length()
            defaultButton.put("name", "按钮${idx + 1}")
            defaultButton.put("curl", "")
            defaultButton.put("color", getDefaultColor(idx))
            jsonArray.put(defaultButton)
        }
        
        // 更新指定位置的按钮
        val jsonObject = JSONObject()
        jsonObject.put("name", buttonData.name)
        jsonObject.put("curl", buttonData.curlCommand)
        jsonObject.put("color", buttonData.color)
        jsonArray.put(index, jsonObject)
        
        prefs.edit().putString(KEY_BUTTONS, jsonArray.toString()).apply()
    }
    
    /**
     * 获取布局模式
     */
    fun getLayoutMode(): String {
        return prefs.getString(KEY_LAYOUT, LAYOUT_HORIZONTAL) ?: LAYOUT_HORIZONTAL
    }
    
    /**
     * 保存布局模式
     */
    fun saveLayoutMode(mode: String) {
        prefs.edit().putString(KEY_LAYOUT, mode).apply()
    }
    
    /**
     * 切换布局模式
     */
    fun toggleLayoutMode(): String {
        val currentMode = getLayoutMode()
        val newMode = if (currentMode == LAYOUT_VERTICAL) LAYOUT_HORIZONTAL else LAYOUT_VERTICAL
        saveLayoutMode(newMode)
        return newMode
    }
    
    /**
     * 检查是否为水平布局
     */
    fun isHorizontalLayout(): Boolean {
        return getLayoutMode() == LAYOUT_HORIZONTAL
    }
    
    /**
     * 获取按钮字体大小（单位：sp）
     */
    fun getFontSize(): Float {
        return prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
    }
    
    /**
     * 保存按钮字体大小（单位：sp）
     */
    fun saveFontSize(size: Float) {
        prefs.edit().putFloat(KEY_FONT_SIZE, size).apply()
    }
}

/**
 * 按钮数据
 */
data class ButtonData(
    val name: String,
    val curlCommand: String,
    val color: String = "#808080"
)

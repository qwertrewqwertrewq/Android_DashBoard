package com.example.backlightcontroller

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
    private const val DEFAULT_RTSP_URL = "rtsp://192.168.31.17:8554/stream1"
    
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
                        curlCommand = jsonObject.getString("curl")
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ButtonData("按钮${index + 1}", "")
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
            defaultButton.put("name", "按钮${jsonArray.length() + 1}")
            defaultButton.put("curl", "")
            jsonArray.put(defaultButton)
        }
        
        // 更新指定位置的按钮
        val jsonObject = JSONObject()
        jsonObject.put("name", buttonData.name)
        jsonObject.put("curl", buttonData.curlCommand)
        jsonArray.put(index, jsonObject)
        
        prefs.edit().putString(KEY_BUTTONS, jsonArray.toString()).apply()
    }
}

/**
 * 按钮数据
 */
data class ButtonData(
    val name: String,
    val curlCommand: String
)

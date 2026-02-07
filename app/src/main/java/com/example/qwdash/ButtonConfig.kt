package com.example.qwdash

/**
 * 按钮配置数据类
 * @param name 按钮显示名称
 * @param color 按钮背景颜色 (十六进制格式，如 "#FF5733")
 * @param url 点击按钮时要请求的 URL
 */
data class ButtonConfig(
    val name: String,
    val color: String,
    val url: String
)

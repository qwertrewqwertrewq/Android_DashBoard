package com.example.backlightcontroller

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Curl 命令解析和执行器
 */
object CurlExecutor {
    
    private val client = OkHttpClient()
    
    /**
     * 解析并执行 curl 命令
     */
    fun execute(curlCommand: String, callback: (success: Boolean, response: String) -> Unit) {
        if (curlCommand.isBlank()) {
            callback(false, "Curl命令为空")
            return
        }
        
        try {
            val parsed = parseCurl(curlCommand)
            val request = buildRequest(parsed)
            
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(false, "请求失败: ${e.message}")
                }
                
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    val formatted = formatResponse(response, body)
                    callback(response.isSuccessful, formatted)
                }
            })
        } catch (e: Exception) {
            callback(false, "解析Curl命令失败: ${e.message}")
        }
    }
    
    /**
     * 解析 curl 命令
     */
    private fun parseCurl(command: String): CurlRequest {
        var url = ""
        var method = "GET"
        val headers = mutableMapOf<String, String>()
        var data = ""
        
        // 移除 curl 前缀和多余空格
        val cleaned = command.replace("curl", "")
            .replace("\\\n", " ")
            .replace("\\", "")
            .trim()
        
        // 使用正则表达式提取各部分
        val tokens = splitCommand(cleaned)
        
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i].trim()
            when {
                token == "-X" || token == "--request" -> {
                    if (i + 1 < tokens.size) {
                        method = tokens[i + 1].trim()
                        i++
                    }
                }
                token == "-H" || token == "--header" -> {
                    if (i + 1 < tokens.size) {
                        val header = tokens[i + 1].trim().removeSurrounding("\"").removeSurrounding("'")
                        val parts = header.split(":", limit = 2)
                        if (parts.size == 2) {
                            headers[parts[0].trim()] = parts[1].trim()
                        }
                        i++
                    }
                }
                token == "-d" || token == "--data" || token == "--data-raw" -> {
                    if (i + 1 < tokens.size) {
                        data = tokens[i + 1].trim().removeSurrounding("\"").removeSurrounding("'")
                        i++
                    }
                }
                token.startsWith("http://") || token.startsWith("https://") -> {
                    url = token.removeSurrounding("\"").removeSurrounding("'")
                }
            }
            i++
        }
        
        return CurlRequest(url, method, headers, data)
    }
    
    /**
     * 分割命令（处理引号）
     */
    private fun splitCommand(command: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '
        
        for (char in command) {
            when {
                (char == '"' || char == '\'') && !inQuote -> {
                    inQuote = true
                    quoteChar = char
                    current.append(char)
                }
                char == quoteChar && inQuote -> {
                    inQuote = false
                    current.append(char)
                }
                char.isWhitespace() && !inQuote -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> {
                    current.append(char)
                }
            }
        }
        
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        
        return tokens
    }
    
    /**
     * 构建 OkHttp 请求
     */
    private fun buildRequest(curlRequest: CurlRequest): Request {
        val builder = Request.Builder().url(curlRequest.url)
        
        // 添加 headers
        curlRequest.headers.forEach { (key, value) ->
            builder.addHeader(key, value)
        }
        
        // 设置请求方法和 body
        when (curlRequest.method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> {
                val body = curlRequest.data.toRequestBody("application/json".toMediaType())
                builder.post(body)
            }
            "PUT" -> {
                val body = curlRequest.data.toRequestBody("application/json".toMediaType())
                builder.put(body)
            }
            "DELETE" -> builder.delete()
            "PATCH" -> {
                val body = curlRequest.data.toRequestBody("application/json".toMediaType())
                builder.patch(body)
            }
        }
        
        return builder.build()
    }
    
    /**
     * 格式化响应
     */
    private fun formatResponse(response: Response, body: String): String {
        val sb = StringBuilder()
        
        // 状态行
        sb.append("HTTP/1.1 ${response.code} ${response.message}\n")
        
        // Headers
        sb.append("\n=== Headers ===\n")
        response.headers.forEach { (name, value) ->
            sb.append("$name: $value\n")
        }
        
        // Body
        sb.append("\n=== Body ===\n")
        if (body.isNotEmpty()) {
            try {
                // 尝试美化 JSON
                val formatted = if (body.trim().startsWith("{")) {
                    JSONObject(body).toString(2)
                } else if (body.trim().startsWith("[")) {
                    JSONArray(body).toString(2)
                } else {
                    body
                }
                sb.append(formatted)
            } catch (e: Exception) {
                sb.append(body)
            }
        } else {
            sb.append("(empty)")
        }
        
        return sb.toString()
    }
}

/**
 * Curl 请求数据
 */
private data class CurlRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val data: String
)

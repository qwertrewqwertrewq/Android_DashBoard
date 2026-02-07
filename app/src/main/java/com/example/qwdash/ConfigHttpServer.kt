package com.example.qwdash

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * 简单的HTTP配置服务器
 * 提供REST API来查询和修改应用配置
 */
class ConfigHttpServer(port: Int = 8888) : NanoHTTPD(port) {
    
    companion object {
        private const val TAG = "ConfigHttpServer"
    }
    
    private val gson = Gson()
    
    override fun serve(session: IHTTPSession): Response {
        val method = session.method.name
        val uri = session.uri
        
        Log.d(TAG, "$method $uri")
        
        return when {
            // 获取所有配置
            uri == "/api/config" && method == "GET" -> {
                getConfig()
            }
            
            // 获取RTSP URL
            uri == "/api/rtsp" && method == "GET" -> {
                getRtspUrl()
            }
            
            // 设置RTSP URL
            uri == "/api/rtsp" && method == "POST" -> {
                val postData = parsePostData(session)
                val url = postData.optString("url", "")
                if (url.isEmpty()) {
                    errorResponse("URL不能为空")
                } else {
                    ConfigManager.saveRtspUrl(url)
                    successResponse("RTSP URL已保存: $url")
                }
            }
            
            // 获取布局模式
            uri == "/api/layout" && method == "GET" -> {
                val layout = ConfigManager.getLayoutMode()
                successResponse(mapOf("layout" to layout))
            }
            
            // 设置布局模式
            uri == "/api/layout" && method == "POST" -> {
                val postData = parsePostData(session)
                val layout = postData.optString("layout", "")
                if (layout.isEmpty()) {
                    errorResponse("布局模式不能为空")
                } else {
                    ConfigManager.saveLayoutMode(layout)
                    successResponse("布局模式已改为: $layout")
                }
            }
            
            // 获取按钮配置
            uri.startsWith("/api/buttons") && method == "GET" -> {
                getButtonsConfig()
            }
            
            // 获取单个按钮配置
            uri.matches(Regex("/api/buttons/\\d+")) && method == "GET" -> {
                val buttonId = uri.split("/").last().toIntOrNull()
                if (buttonId != null && buttonId in 0..6) {
                    getButtonConfig(buttonId)
                } else {
                    errorResponse("无效的按钮ID")
                }
            }
            
            // 更新按钮配置
            uri.matches(Regex("/api/buttons/\\d+")) && method == "POST" -> {
                val buttonId = uri.split("/").last().toIntOrNull()
                if (buttonId != null && buttonId in 0..6) {
                    val postData = parsePostData(session)
                    updateButtonConfig(buttonId, postData)
                } else {
                    errorResponse("无效的按钮ID")
                }
            }
            
            // 获取网页界面
            uri == "/" || uri == "/admin" -> {
                getAdminPage()
            }
            
            // 获取状态
            uri == "/api/status" && method == "GET" -> {
                successResponse(mapOf(
                    "status" to "运行中",
                    "timestamp" to System.currentTimeMillis()
                ))
            }
            
            else -> {
                notFoundResponse()
            }
        }
    }
    
    /**
     * 获取所有配置
     */
    private fun getConfig(): Response {
        val config = JsonObject()
        config.addProperty("rtspUrl", ConfigManager.getRtspUrl())
        config.addProperty("layout", ConfigManager.getLayoutMode())
        
        val buttons = mutableListOf<Map<String, String>>()
        for (i in 0..6) {
            val buttonData = ConfigManager.getButtonConfig(i)
            buttons.add(mapOf(
                "id" to i.toString(),
                "name" to buttonData.name,
                "curl" to buttonData.curlCommand
            ))
        }
        config.add("buttons", gson.toJsonTree(buttons))
        
        return successResponse(config)
    }
    
    /**
     * 获取RTSP URL
     */
    private fun getRtspUrl(): Response {
        return successResponse(mapOf("url" to ConfigManager.getRtspUrl()))
    }
    
    /**
     * 获取所有按钮配置
     */
    private fun getButtonsConfig(): Response {
        val buttons = mutableListOf<Map<String, String>>()
        for (i in 0..6) {
            val buttonData = ConfigManager.getButtonConfig(i)
            buttons.add(mapOf(
                "id" to i.toString(),
                "name" to buttonData.name,
                "curl" to buttonData.curlCommand
            ))
        }
        return successResponse(mapOf("buttons" to buttons))
    }
    
    /**
     * 获取单个按钮配置
     */
    private fun getButtonConfig(index: Int): Response {
        val buttonData = ConfigManager.getButtonConfig(index)
        return successResponse(mapOf(
            "id" to index.toString(),
            "name" to buttonData.name,
            "curl" to buttonData.curlCommand
        ))
    }
    
    /**
     * 更新单个按钮配置
     */
    private fun updateButtonConfig(index: Int, data: JSONObject): Response {
        val name = data.optString("name", "")
        val curl = data.optString("curl", "")
        
        if (name.isEmpty()) {
            return errorResponse("按钮名称不能为空")
        }
        
        ConfigManager.saveButtonConfig(index, ButtonData(name, curl))
        return successResponse("按钮 $index 已更新")
    }
    
    /**
     * 解析POST数据
     */
    private fun parsePostData(session: IHTTPSession): JSONObject {
        return try {
            val inputData = mutableMapOf<String, String>()
            session.parseBody(inputData)
            val bodyContent = inputData["postData"]
            if (!bodyContent.isNullOrEmpty()) {
                JSONObject(bodyContent)
            } else if (session.parms.isNotEmpty()) {
                JSONObject(session.parms as Map<*, *>)
            } else {
                JSONObject()
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析POST数据失败", e)
            JSONObject()
        }
    }
    
    /**
     * 成功响应
     */
    private fun successResponse(data: Any): Response {
        val response = JsonObject()
        response.addProperty("success", true)
        response.add("data", gson.toJsonTree(data))
        return newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, 
            "application/json", 
            response.toString()
        )
    }
    
    /**
     * 错误响应
     */
    private fun errorResponse(message: String): Response {
        val response = JsonObject()
        response.addProperty("success", false)
        response.addProperty("message", message)
        return newFixedLengthResponse(
            NanoHTTPD.Response.Status.BAD_REQUEST, 
            "application/json", 
            response.toString()
        )
    }
    
    /**
     * 404响应
     */
    private fun notFoundResponse(): Response {
        return newFixedLengthResponse(
            NanoHTTPD.Response.Status.NOT_FOUND, 
            "text/plain", 
            "404 - 未找到"
        )
    }
    
    /**
     * 获取管理员网页
     */
    private fun getAdminPage(): Response {
        val html = getHtmlContent()
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", html)
    }
    
    /**
     * 获取HTML内容（避免Kotlin字符串模板与JS模板混淆）
     */
    private fun getHtmlContent(): String {
        return """<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>QWdash 管理界面</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif; background: #f5f5f5; }
        .container { max-width: 1000px; margin: 0 auto; padding: 20px; }
        h1 { color: #333; margin-bottom: 30px; text-align: center; }
        .card { background: white; border-radius: 8px; padding: 20px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .card h2 { font-size: 18px; color: #333; margin-bottom: 15px; border-bottom: 2px solid #007bff; padding-bottom: 10px; }
        .form-group { margin-bottom: 15px; }
        label { display: block; margin-bottom: 5px; color: #555; font-weight: 500; }
        input, textarea, select { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }
        textarea { resize: vertical; min-height: 80px; font-family: monospace; }
        button { background: #007bff; color: white; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; }
        button:hover { background: #0056b3; }
        .button-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 15px; }
        .button-card { background: #f9f9f9; padding: 15px; border-radius: 4px; border-left: 4px solid #007bff; }
        .status { padding: 10px; border-radius: 4px; margin-top: 10px; }
        .status.success { background: #d4edda; color: #155724; }
        .status.error { background: #f8d7da; color: #721c24; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🎮 QWdash 配置管理</h1>
        
        <div class="card">
            <h2>视频流配置</h2>
            <div class="form-group">
                <label for="rtspUrl">RTSP URL</label>
                <input type="text" id="rtspUrl" placeholder="rtsp://192.168.31.17:8554/stream1">
                <button onclick="saveRtspUrl()">保存RTSP地址</button>
                <div id="rtspStatus" class="status" style="display:none;"></div>
            </div>
        </div>
        
        <div class="card">
            <h2>布局模式</h2>
            <div class="form-group">
                <label for="layoutMode">选择布局</label>
                <select id="layoutMode">
                    <option value="vertical">竖屏布局（旋转视频）</option>
                    <option value="horizontal">横屏布局（侧边按钮）</option>
                </select>
                <button onclick="saveLayout()">保存布局</button>
                <div id="layoutStatus" class="status" style="display:none;"></div>
            </div>
        </div>
        
        <div class="card">
            <h2>按钮配置（1-7号）</h2>
            <div class="button-grid" id="buttonsContainer"></div>
        </div>
        
        <div class="card">
            <h2>系统状态</h2>
            <div id="systemStatus">加载中...</div>
        </div>
    </div>
    
    <script>
        const API_BASE = window.location.origin;
        
        async function loadConfig() {
            try {
                let res = await fetch(API_BASE + '/api/rtsp');
                let data = await res.json();
                if (data.success) document.getElementById('rtspUrl').value = data.data.url;
                
                res = await fetch(API_BASE + '/api/layout');
                data = await res.json();
                if (data.success) document.getElementById('layoutMode').value = data.data.layout;
                
                res = await fetch(API_BASE + '/api/buttons');
                data = await res.json();
                if (data.success) {
                    const buttons = data.data.buttons;
                    const container = document.getElementById('buttonsContainer');
                    container.innerHTML = '';
                    buttons.forEach(btn => {
                        container.innerHTML += '<div class="button-card">' +
                            '<h3>按钮 ' + btn.id + '</h3>' +
                            '<div class="form-group">' +
                            '<label>名称</label>' +
                            '<input type="text" id="btn_name_' + btn.id + '" value="' + btn.name + '">' +
                            '</div>' +
                            '<div class="form-group">' +
                            '<label>Curl命令</label>' +
                            '<textarea id="btn_curl_' + btn.id + '">' + (btn.curl || '') + '</textarea>' +
                            '<button onclick="saveButton(' + btn.id + ')">保存</button>' +
                            '<div id="btn_status_' + btn.id + '" class="status" style="display:none;"></div>' +
                            '</div></div>';
                    });
                }
                
                res = await fetch(API_BASE + '/api/status');
                data = await res.json();
                if (data.success) {
                    document.getElementById('systemStatus').innerHTML = 
                        '<p>状态: ' + data.data.status + '</p>' +
                        '<p>时间: ' + new Date(data.data.timestamp).toLocaleString() + '</p>';
                }
            } catch (error) {
                console.error('加载配置失败:', error);
            }
        }
        
        async function saveRtspUrl() {
            const url = document.getElementById('rtspUrl').value;
            if (!url) {
                showStatus('rtspStatus', 'URL不能为空', false);
                return;
            }
            try {
                const res = await fetch(API_BASE + '/api/rtsp', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({url: url})
                });
                const data = await res.json();
                showStatus('rtspStatus', data.data || data.message, data.success);
            } catch (error) {
                showStatus('rtspStatus', '保存失败: ' + error.message, false);
            }
        }
        
        async function saveLayout() {
            const layout = document.getElementById('layoutMode').value;
            try {
                const res = await fetch(API_BASE + '/api/layout', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({layout: layout})
                });
                const data = await res.json();
                showStatus('layoutStatus', data.data || data.message, data.success);
            } catch (error) {
                showStatus('layoutStatus', '保存失败: ' + error.message, false);
            }
        }
        
        async function saveButton(id) {
            const name = document.getElementById('btn_name_' + id).value;
            const curl = document.getElementById('btn_curl_' + id).value;
            if (!name) {
                showStatus('btn_status_' + id, '名称不能为空', false);
                return;
            }
            try {
                const res = await fetch(API_BASE + '/api/buttons/' + id, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({name: name, curl: curl})
                });
                const data = await res.json();
                showStatus('btn_status_' + id, data.data || data.message, data.success);
            } catch (error) {
                showStatus('btn_status_' + id, '保存失败: ' + error.message, false);
            }
        }
        
        function showStatus(elementId, message, success) {
            const elem = document.getElementById(elementId);
            elem.textContent = message;
            elem.className = 'status ' + (success ? 'success' : 'error');
            elem.style.display = 'block';
            setTimeout(() => { elem.style.display = 'none'; }, 5000);
        }
        
        window.addEventListener('load', loadConfig);
        setInterval(() => {
            fetch(API_BASE + '/api/status')
                .then(r => r.json())
                .then(d => {
                    if (d.success) {
                        document.getElementById('systemStatus').innerHTML = 
                            '<p>状态: ' + d.data.status + '</p>' +
                            '<p>更新: ' + new Date(d.data.timestamp).toLocaleTimeString() + '</p>';
                    }
                });
        }, 30000);
    </script>
</body>
</html>"""
    }
}

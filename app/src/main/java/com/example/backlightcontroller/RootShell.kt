package com.example.backlightcontroller

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Root Shell 单例工具类
 * 用于执行需要 Root 权限的命令
 */
object RootShell {
    private const val TAG = "RootShell"
    private var process: Process? = null
    private var outputStream: DataOutputStream? = null
    
    /**
     * 初始化 Root Shell
     * @return 是否成功获取 Root 权限
     */
    fun init(): Boolean {
        return try {
            process = Runtime.getRuntime().exec("su")
            outputStream = DataOutputStream(process!!.outputStream)
            Log.d(TAG, "Root shell initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize root shell", e)
            false
        }
    }
    
    /**
     * 执行 su 命令
     * @param command 要执行的命令
     * @return 命令执行结果（输出内容）
     */
    fun exec(command: String): String {
        val result = StringBuilder()
        
        try {
            if (process == null || outputStream == null) {
                if (!init()) {
                    return "Error: Failed to get root access"
                }
            }
            
            Log.d(TAG, "Executing command: $command")
            
            // 写入命令
            outputStream?.apply {
                writeBytes("$command\n")
                flush()
            }
            
            // 使用临时进程读取输出
            val tempProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(tempProcess.inputStream))
            val errorReader = BufferedReader(InputStreamReader(tempProcess.errorStream))
            
            // 读取标准输出
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                result.append(line).append("\n")
            }
            
            // 读取错误输出
            val errorOutput = StringBuilder()
            while (errorReader.readLine().also { line = it } != null) {
                errorOutput.append(line).append("\n")
            }
            
            tempProcess.waitFor()
            reader.close()
            errorReader.close()
            
            if (errorOutput.isNotEmpty()) {
                Log.e(TAG, "Command error output: $errorOutput")
            }
            
            Log.d(TAG, "Command result: $result")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: $command", e)
            result.append("Error: ${e.message}")
        }
        
        return result.toString()
    }
    
    /**
     * 查找设备上的背光控制文件路径
     * @return 找到的背光路径列表
     */
    fun findBacklightPaths(): List<String> {
        val backlightPaths = mutableListOf<String>()
        val baseDir = "/sys/class/backlight/"
        
        Log.d(TAG, "Searching for backlight paths in $baseDir")
        
        // 列出 backlight 目录下的所有子目录
        val lsResult = exec("ls $baseDir")
        
        if (lsResult.contains("Error") || lsResult.isEmpty()) {
            Log.e(TAG, "Failed to list backlight directory")
            return backlightPaths
        }
        
        val directories = lsResult.trim().split("\n")
        
        for (dir in directories) {
            if (dir.isNotEmpty()) {
                val brightnessPath = "$baseDir$dir/brightness"
                
                // 检查 brightness 文件是否存在
                val checkResult = exec("[ -f $brightnessPath ] && echo 'exists' || echo 'not found'")
                
                if (checkResult.trim() == "exists") {
                    backlightPaths.add(brightnessPath)
                    Log.d(TAG, "Found backlight path: $brightnessPath")
                    
                    // 同时记录相关信息
                    val maxBrightness = exec("cat $baseDir$dir/max_brightness").trim()
                    val currentBrightness = exec("cat $brightnessPath").trim()
                    
                    Log.d(TAG, "  - Max brightness: $maxBrightness")
                    Log.d(TAG, "  - Current brightness: $currentBrightness")
                    
                    println("Backlight path found: $brightnessPath")
                    println("  Max brightness: $maxBrightness")
                    println("  Current brightness: $currentBrightness")
                }
            }
        }
        
        if (backlightPaths.isEmpty()) {
            Log.w(TAG, "No backlight paths found")
        } else {
            Log.d(TAG, "Total ${backlightPaths.size} backlight path(s) found")
        }
        
        return backlightPaths
    }
    
    /**
     * 关闭 Root Shell
     */
    fun close() {
        try {
            outputStream?.apply {
                writeBytes("exit\n")
                flush()
                close()
            }
            process?.destroy()
            process = null
            outputStream = null
            Log.d(TAG, "Root shell closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing root shell", e)
        }
    }
}

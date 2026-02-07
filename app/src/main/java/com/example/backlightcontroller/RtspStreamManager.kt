package com.example.backlightcontroller

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * RTSP 流管理器 - 使用 LibVLC
 * 处理视频流播放和截图
 */
class RtspStreamManager(
    private val context: Context,
    private val videoLayout: VLCVideoLayout,
    private val snapshotImageView: ImageView,
    private val rtspUrl: String
) {
    
    private val TAG = "RtspStreamManager"
    private val handler = Handler(Looper.getMainLooper())
    private var isLiveMode = false
    private var snapshotRunnable: Runnable? = null
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var reconnectRunnable: Runnable? = null
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 5
    
    /**
     * 初始化播放器
     */
    fun initialize() {
        try {
            libVLC = LibVLC(context, ArrayList<String>().apply {
                // 网络缓存增加到2秒，改善网络抖动问题
                add("--network-caching=2000")
                // 使用TCP协议，更稳定
                add("--rtsp-tcp")
                // 启用硬件加速
                add("--codec=mediacodec_ndk,mediacodec_jni,all")
                // 允许跳帧，避免卡顿
                add("--drop-late-frames")
                add("--skip-frames")
                // 音视频同步
                add("--audio-time-stretch")
                // 减少日志输出
                add("-vv")
            })
            
            mediaPlayer = MediaPlayer(libVLC).apply {
                // 使用 TextureView 而不是 SurfaceView，这样才能应用旋转变换
                attachViews(videoLayout, null, false, true)
                
                // 添加事件监听处理错误和状态
                setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.EncounteredError -> {
                            Log.e(TAG, "MediaPlayer encountered error, attempting reconnect")
                            scheduleReconnect()
                        }
                        MediaPlayer.Event.Playing -> {
                            Log.d(TAG, "MediaPlayer started playing")
                            reconnectAttempts = 0  // 重置重连次数
                        }
                        MediaPlayer.Event.Buffering -> {
                            val buffering = event.buffering
                            if (buffering < 100f) {
                                Log.d(TAG, "Buffering: ${buffering}%")
                            }
                        }
                        MediaPlayer.Event.Stopped -> {
                            Log.d(TAG, "MediaPlayer stopped")
                        }
                    }
                }
            }
            
            Log.d(TAG, "LibVLC initialized with optimized settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LibVLC: ${e.message}", e)
        }
    }
    
    /**
     * 启动实时播放模式
     */
    fun startLiveMode() {
        if (isLiveMode) {
            Log.d(TAG, "Already in live mode, skipping")
            return
        }
        
        isLiveMode = true
        stopSnapshotMode()
        cancelReconnect()
        
        try {
            videoLayout.visibility = android.view.View.VISIBLE
            snapshotImageView.visibility = android.view.View.GONE
            
            val media = Media(libVLC, Uri.parse(rtspUrl))
            // 增加网络缓存到2秒
            media.addOption(":network-caching=2000")
            media.addOption(":rtsp-tcp")
            // 降低延迟
            media.addOption(":live-caching=500")
            
            mediaPlayer?.apply {
                setMedia(media)
                play()
            }
            
            media.release()
            
            Log.d(TAG, "Started live mode with URL: $rtspUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start live mode: ${e.message}", e)
            scheduleReconnect()
        }
    }
    
    /**
     * 启动快照模式（每分钟更新一次）
     */
    fun startSnapshotMode() {
        if (!isLiveMode) return
        
        isLiveMode = false
        
        // 停止播放
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping player: ${e.message}")
        }
        
        videoLayout.visibility = android.view.View.GONE
        snapshotImageView.visibility = android.view.View.VISIBLE
        
        // 立即获取一次快照
        captureSnapshot()
        
        // 设置定时任务，每60秒更新一次
        snapshotRunnable = object : Runnable {
            override fun run() {
                captureSnapshot()
                handler.postDelayed(this, 60000) // 60秒
            }
        }
        snapshotRunnable?.let { handler.postDelayed(it, 60000) }
        
        Log.d(TAG, "Started snapshot mode")
    }
    
    /**
     * 停止快照模式
     */
    private fun stopSnapshotMode() {
        snapshotRunnable?.let { handler.removeCallbacks(it) }
        snapshotRunnable = null
    }
    
    /**     * 安排自动重连
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached, giving up")
            return
        }
        
        cancelReconnect()
        
        reconnectAttempts++
        val delayMs = (reconnectAttempts * 2000L).coerceAtMost(10000L)  // 2秒到10秒
        
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${delayMs}ms")
        
        reconnectRunnable = Runnable {
            try {
                Log.d(TAG, "Attempting to reconnect...")
                mediaPlayer?.stop()
                isLiveMode = false
                startLiveMode()
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect failed: ${e.message}")
                scheduleReconnect()
            }
        }
        
        reconnectRunnable?.let { handler.postDelayed(it, delayMs) }
    }
    
    /**
     * 取消自动重连
     */
    private fun cancelReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }
    
    /**     * 捕获当前视频帧
     */
    private fun captureSnapshot() {
        try {
            // 临时播放以获取帧
            val tempLibVLC = LibVLC(context, ArrayList<String>().apply {
                add("--rtsp-tcp")
            })
            val tempPlayer = MediaPlayer(tempLibVLC)
            
            val media = Media(tempLibVLC, Uri.parse(rtspUrl))
            media.addOption(":network-caching=300")
            
            tempPlayer.apply {
                setMedia(media)
                play()
                
                // 等待视频帧加载
                handler.postDelayed({
                    try {
                        // 从VideoLayout获取截图
                        videoLayout.isDrawingCacheEnabled = true
                        val bitmap = Bitmap.createBitmap(videoLayout.drawingCache)
                        videoLayout.isDrawingCacheEnabled = false
                        
                        snapshotImageView.setImageBitmap(bitmap)
                        
                        // 停止临时播放器
                        stop()
                        release()
                        tempLibVLC.release()
                        media.release()
                        
                        Log.d(TAG, "Snapshot captured")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to capture snapshot: ${e.message}")
                        release()
                        tempLibVLC.release()
                        media.release()
                    }
                }, 2000)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start snapshot capture: ${e.message}")
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopSnapshotMode()
        cancelReconnect()
        reconnectAttempts = 0
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            libVLC?.release()
            libVLC = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing player: ${e.message}")
        }
        Log.d(TAG, "LibVLC released")
    }
}

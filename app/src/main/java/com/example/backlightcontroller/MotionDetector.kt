package com.example.backlightcontroller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.app.ActivityCompat

/**
 * 运动检测器
 * 使用前置摄像头检测画面亮度变化来判断是否有运动
 */
class MotionDetector(
    private val context: Context,
    private val onMotionDetected: () -> Unit
) {
    
    private companion object {
        const val TAG = "MotionDetector"
        const val IMAGE_WIDTH = 640
        const val IMAGE_HEIGHT = 480
        const val BRIGHTNESS_THRESHOLD = 15 // 亮度差异阈值
    }
    
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    private var previousBrightness: Float = -1f
    
    /**
     * 启动运动检测
     */
    fun start() {
        startBackgroundThread()
        openCamera()
    }
    
    /**
     * 停止运动检测
     */
    fun stop() {
        closeCamera()
        stopBackgroundThread()
    }
    
    /**
     * 启动后台线程
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }
    
    /**
     * 停止后台线程
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }
    
    /**
     * 打开摄像头
     */
    private fun openCamera() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            // 查找前置摄像头
            val cameraId = findFrontCamera(cameraManager) ?: run {
                Log.e(TAG, "No front camera found")
                return
            }
            
            Log.d(TAG, "Opening front camera: $cameraId")
            
            // 检查权限
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Camera permission not granted")
                return
            }
            
            // 创建 ImageReader
            imageReader = ImageReader.newInstance(
                IMAGE_WIDTH,
                IMAGE_HEIGHT,
                ImageFormat.YUV_420_888,
                2 // maxImages
            ).apply {
                setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
            }
            
            // 打开摄像头
            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error opening camera", e)
        }
    }
    
    /**
     * 查找前置摄像头
     */
    private fun findFrontCamera(cameraManager: CameraManager): String? {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return cameraId
            }
        }
        return null
    }
    
    /**
     * 摄像头状态回调
     */
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            Log.d(TAG, "Camera opened")
            createCaptureSession()
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
            Log.d(TAG, "Camera disconnected")
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
            Log.e(TAG, "Camera error: $error")
        }
    }
    
    /**
     * 创建捕获会话
     */
    private fun createCaptureSession() {
        try {
            val surface = imageReader?.surface ?: return
            
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )?.apply {
                addTarget(surface)
            }
            
            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        
                        try {
                            // 开始连续捕获
                            captureRequestBuilder?.let {
                                session.setRepeatingRequest(
                                    it.build(),
                                    null,
                                    backgroundHandler
                                )
                            }
                            Log.d(TAG, "Capture session started")
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Error starting capture", e)
                        }
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                    }
                },
                backgroundHandler
            )
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error creating capture session", e)
        }
    }
    
    /**
     * 图像可用监听器
     */
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        var image: android.media.Image? = null
        
        try {
            image = reader.acquireLatestImage()
            
            if (image != null) {
                // 获取 Y 平面（YUV 格式的亮度信息）
                val yPlane = image.planes[0]
                val yBuffer = yPlane.buffer
                val ySize = yBuffer.remaining()
                
                // 计算平均亮度
                val currentBrightness = calculateAverageBrightness(yBuffer, ySize)
                
                // 检测运动
                if (previousBrightness >= 0) {
                    val brightnessDiff = Math.abs(currentBrightness - previousBrightness)
                    
                    if (brightnessDiff > BRIGHTNESS_THRESHOLD) {
                        Log.w(TAG, "MOTION DETECTED - Brightness diff: $brightnessDiff")
                        // 回调通知运动检测
                        onMotionDetected()
                    }
                }
                
                previousBrightness = currentBrightness
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        } finally {
            // 关键：及时关闭图像，防止内存溢出
            image?.close()
        }
    }
    
    /**
     * 计算平均亮度
     * @param buffer Y 平面的数据缓冲区
     * @param size 缓冲区大小
     * @return 平均亮度值 (0-255)
     */
    private fun calculateAverageBrightness(buffer: java.nio.ByteBuffer, size: Int): Float {
        var sum = 0L
        
        // 遍历所有像素
        for (i in 0 until size) {
            // 将 byte 转换为无符号值 (0-255)
            val brightness = buffer.get(i).toInt() and 0xFF
            sum += brightness
        }
        
        // 计算平均值
        return sum.toFloat() / size
    }
    
    /**
     * 关闭摄像头
     */
    private fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            
            cameraDevice?.close()
            cameraDevice = null
            
            imageReader?.close()
            imageReader = null
            
            Log.d(TAG, "Camera closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }
}

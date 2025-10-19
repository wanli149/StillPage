package io.stillpage.app.ui.book.video

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.view.GestureDetectorCompat
import io.stillpage.app.constant.AppLog
import kotlin.math.abs

/**
 * 视频播放手势控制视图
 * 支持音量、亮度、进度调节
 */
class VideoGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gestureDetector: GestureDetectorCompat
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    private var onGestureListener: OnGestureListener? = null
    private var screenWidth = 0
    private var screenHeight = 0
    
    // 手势状态
    private var isVolumeGesture = false
    private var isBrightnessGesture = false
    private var isProgressGesture = false
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    
    // 音量和亮度
    private var maxVolume = 0
    private var currentVolume = 0
    private var currentBrightness = 0f
    
    interface OnGestureListener {
        fun onSingleTap()
        fun onDoubleTap()
        fun onVolumeChange(volume: Int, maxVolume: Int)
        fun onBrightnessChange(brightness: Float)
        fun onProgressChange(progress: Long, isFinished: Boolean)
        fun onLongPress()
    }

    init {
        gestureDetector = GestureDetectorCompat(context, GestureListener())
        
        // 获取屏幕尺寸
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        
        // 初始化音量
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // 初始化亮度
        try {
            currentBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            ) / 255f
        } catch (e: Settings.SettingNotFoundException) {
            currentBrightness = 0.5f
        }
        
        AppLog.put("VideoGestureView: 初始化完成 - 音量:$currentVolume/$maxVolume, 亮度:$currentBrightness")
    }

    fun setOnGestureListener(listener: OnGestureListener) {
        this.onGestureListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                gestureDownX = event.x
                gestureDownY = event.y
                isVolumeGesture = false
                isBrightnessGesture = false
                isProgressGesture = false
            }
            
            MotionEvent.ACTION_MOVE -> {
                handleMoveGesture(event)
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isProgressGesture) {
                    onGestureListener?.onProgressChange(0, true)
                }
                isVolumeGesture = false
                isBrightnessGesture = false
                isProgressGesture = false
            }
        }
        
        return true
    }

    private fun handleMoveGesture(event: MotionEvent) {
        val deltaX = event.x - gestureDownX
        val deltaY = event.y - gestureDownY
        
        // 判断手势类型
        if (abs(deltaX) > 50 || abs(deltaY) > 50) {
            if (abs(deltaX) > abs(deltaY)) {
                // 水平滑动 - 进度调节
                if (!isVolumeGesture && !isBrightnessGesture) {
                    isProgressGesture = true
                    handleProgressGesture(deltaX)
                }
            } else {
                // 垂直滑动 - 音量或亮度调节
                if (!isProgressGesture) {
                    if (gestureDownX < screenWidth / 2) {
                        // 左侧 - 亮度调节
                        if (!isVolumeGesture) {
                            isBrightnessGesture = true
                            handleBrightnessGesture(deltaY)
                        }
                    } else {
                        // 右侧 - 音量调节
                        if (!isBrightnessGesture) {
                            isVolumeGesture = true
                            handleVolumeGesture(deltaY)
                        }
                    }
                }
            }
        }
    }

    private fun handleVolumeGesture(deltaY: Float) {
        val volumeChange = (-deltaY / screenHeight * maxVolume).toInt()
        val newVolume = (currentVolume + volumeChange).coerceIn(0, maxVolume)
        
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        onGestureListener?.onVolumeChange(newVolume, maxVolume)
        
        AppLog.put("VideoGestureView: 音量调节 - $newVolume/$maxVolume")
    }

    private fun handleBrightnessGesture(deltaY: Float) {
        val brightnessChange = -deltaY / screenHeight
        val newBrightness = (currentBrightness + brightnessChange).coerceIn(0f, 1f)
        
        // 设置窗口亮度
        val activity = context as? android.app.Activity
        activity?.window?.attributes?.let { layoutParams ->
            layoutParams.screenBrightness = newBrightness
            activity.window.attributes = layoutParams
        }
        
        onGestureListener?.onBrightnessChange(newBrightness)
        
        AppLog.put("VideoGestureView: 亮度调节 - $newBrightness")
    }

    private fun handleProgressGesture(deltaX: Float) {
        // 计算进度变化（以秒为单位）
        val progressChange = (deltaX / screenWidth * 60 * 1000).toLong() // 最大60秒
        onGestureListener?.onProgressChange(progressChange, false)
        
        AppLog.put("VideoGestureView: 进度调节 - ${progressChange}ms")
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onGestureListener?.onSingleTap()
            AppLog.put("VideoGestureView: 单击")
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onGestureListener?.onDoubleTap()
            AppLog.put("VideoGestureView: 双击")
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onGestureListener?.onLongPress()
            AppLog.put("VideoGestureView: 长按")
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
    }
}
package io.stillpage.app.ui.book.video

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.EventBus
import io.stillpage.app.constant.Status
import io.stillpage.app.constant.Theme
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.databinding.ActivityVideoPlayBinding
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.config.VideoPlayConfig
import io.stillpage.app.help.exoplayer.ExoPlayerHelper
import io.stillpage.app.model.VideoPlay
import io.stillpage.app.service.VideoPlayService
import io.stillpage.app.utils.observeEvent
import io.stillpage.app.utils.postEvent
import io.stillpage.app.utils.toastOnUi
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import io.stillpage.app.data.appDb
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 视频播放Activity
 * 基于ExoPlayer的专业视频播放器，完全替代WebView播放方案
 */
@SuppressLint("ObsoleteSdkInt")
class VideoPlayActivity : 
    VMBaseActivity<ActivityVideoPlayBinding, VideoPlayViewModel>(toolBarTheme = Theme.Dark),
    Player.Listener,
    VideoPlay.CallBack {

    override val binding by viewBinding(ActivityVideoPlayBinding::inflate)
    override val viewModel by viewModels<VideoPlayViewModel>()
    
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playerView: PlayerView
    private var isFullScreen = false
    private var currentSpeed = VideoPlayConfig.defaultPlaySpeed
    private var bookUrl: String? = null
    private var chapterIndex: Int = 0
    private var chapterUrl: String? = null
    private var videoTitle: String? = null
    private var bookSource: BookSource? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        AppLog.put("VideoPlayActivity: onCreate - 视频播放器启动")
        
        // 设置沉浸式全屏
        setupImmersiveMode()
        
        // 初始化播放器
        initExoPlayer()
        
        // 初始化UI
        initViews()
        
        // 处理Intent参数
        handleIntent()
        
        // 初始化事件监听
        initEvents()
        
        // 设置返回键处理
        setupBackPressedHandler()
        
        // 注册VideoPlay回调
        VideoPlay.registerCallBack(this)
    }

    /**
     * 设置沉浸式模式
     */
    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * 初始化ExoPlayer
     */
    private fun initExoPlayer() {
        exoPlayer = ExoPlayerHelper.createVideoExoPlayer(this)
        exoPlayer.addListener(this)
        exoPlayer.playWhenReady = true
        
        // 设置播放速度
        exoPlayer.setPlaybackSpeed(currentSpeed)
        
        AppLog.put("VideoPlayActivity: ExoPlayer初始化完成")
    }

    /**
     * 初始化视图
     */
    private fun initViews() {
        playerView = binding.playerView
        playerView.player = exoPlayer
        playerView.useController = true
        playerView.controllerShowTimeoutMs = 3000
        
        // 设置播放器控制器
        setupPlayerController()
    }

    /**
     * 设置播放器控制器
     */
    private fun setupPlayerController() {
        // 隐藏默认的一些控件，使用自定义控制
        // 在Media3中，这些方法可能需要不同的调用方式
        // playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        // playerView.setShowNextButton(false)
        // playerView.setShowPreviousButton(false)
    }

    /**
     * 处理Intent参数
     */
    private fun handleIntent() {
        intent?.let { intent ->
            bookUrl = intent.getStringExtra("bookUrl")
            chapterIndex = intent.getIntExtra("chapterIndex", 0)
            chapterUrl = intent.getStringExtra("chapterUrl")
            videoTitle = intent.getStringExtra("title")
            bookSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("bookSource", BookSource::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("bookSource")
            }
            
            AppLog.put("VideoPlayActivity: 接收参数 - bookUrl=$bookUrl, chapterIndex=$chapterIndex, title=$videoTitle")
            
            // 设置标题
            videoTitle?.let { title ->
                binding.titleBar.title = title
            }
            
            // 开始播放
            startVideoPlay()
        }
    }

    /**
     * 初始化事件监听
     */
    private fun initEvents() {
        // 返回按钮
        binding.titleBar.setNavigationOnClickListener {
            finish()
        }
        
        // 全屏切换按钮
        binding.btnFullscreen.setOnClickListener {
            toggleFullScreen()
        }
        
        // 播放速度按钮
        binding.btnSpeed.setOnClickListener {
            showSpeedDialog()
        }
        
        // 菜单按钮
        binding.btnMenu.setOnClickListener {
            // 显示溢出菜单
            showPopupMenu()
        }
        
        // 手势控制监听
        binding.gestureOverlay.setOnGestureListener(object : VideoGestureView.OnGestureListener {
            override fun onSingleTap() {
                // 单击切换控制栏显示/隐藏
                toggleControlsVisibility()
            }

            override fun onDoubleTap() {
                // 双击播放/暂停
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.play()
                }
            }

            override fun onVolumeChange(volume: Int, maxVolume: Int) {
                // 显示音量调节提示
                showVolumeIndicator(volume, maxVolume)
            }

            override fun onBrightnessChange(brightness: Float) {
                // 显示亮度调节提示
                showBrightnessIndicator(brightness)
            }

            override fun onProgressChange(progress: Long, isFinished: Boolean) {
                // 显示进度调节提示
                if (isFinished) {
                    hideProgressIndicator()
                } else {
                    showProgressIndicator(progress)
                }
            }

            override fun onLongPress() {
                // 长按显示视频信息
                showVideoInfo()
            }
        })
        
        // 监听播放状态变化
        observeEvent<Int>(EventBus.VIDEO_STATE) { state ->
            when (state) {
                Status.PLAY -> {
                    AppLog.put("VideoPlayActivity: 播放状态 - 播放中")
                    updatePlayPauseButton(true)
                }
                Status.PAUSE -> {
                    AppLog.put("VideoPlayActivity: 播放状态 - 暂停")
                    updatePlayPauseButton(false)
                }
                Status.STOP -> {
                    AppLog.put("VideoPlayActivity: 播放状态 - 停止")
                    updatePlayPauseButton(false)
                }
            }
        }
        
        // 监听播放速度变化
        observeEvent<Float>(EventBus.VIDEO_SPEED) { speed ->
            currentSpeed = speed
            binding.btnSpeed.text = "${speed}x"
        }
        
        // 监听章节切换
        observeEvent<Int>(EventBus.VIDEO_CHAPTER_CHANGE) { chapterIndex ->
            AppLog.put("VideoPlayActivity: 章节切换到 - $chapterIndex")
            updateChapterInfo(chapterIndex)
        }
        
        // 监听播放进度
        observeEvent<Int>(EventBus.VIDEO_PROGRESS) { position ->
            // 更新播放进度（如果需要自定义进度显示）
        }
        
        // 监听画中画请求
        observeEvent<Boolean>(EventBus.VIDEO_PIP_REQUEST) { request ->
            if (request && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                enterPipMode()
            }
        }
    }

    /**
     * 开始视频播放
     */
    private fun startVideoPlay() {
        lifecycleScope.launch {
            try {
                val url = chapterUrl
                if (url.isNullOrBlank()) {
                    toastOnUi("视频链接为空")
                    return@launch
                }
                
                AppLog.put("VideoPlayActivity: 开始播放视频 - $url")
                
                // 初始化VideoPlay模型
                initVideoPlayModel()
                
                // 启动VideoPlayService
                startVideoPlayService()
                
                // 使用ViewModel处理视频URL和播放
                viewModel.playVideo(url, bookSource)
                
            } catch (e: Exception) {
                AppLog.put("VideoPlayActivity: 播放失败", e)
                toastOnUi("播放失败: ${e.message}")
            }
        }
    }

    /**
     * 初始化VideoPlay模型
     */
    private fun initVideoPlayModel() {
        try {
            // 从Intent获取书籍信息
            val bookUrl = this.bookUrl ?: return
            
            lifecycleScope.launch {
                val book = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(bookUrl)
                }
                
                val chapter = withContext(Dispatchers.IO) {
                    appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
                }
                
                if (book != null && chapter != null) {
                    // 设置VideoPlay状态
                    VideoPlay.book = book
                    VideoPlay.bookSource = bookSource
                    VideoPlay.durChapter = chapter
                    VideoPlay.durChapterIndex = chapterIndex
                    VideoPlay.durChapterPos = book.durChapterPos
                    VideoPlay.inBookshelf = intent.getBooleanExtra("inBookshelf", false)
                    
                    AppLog.put("VideoPlayActivity: VideoPlay模型初始化完成")
                } else {
                    AppLog.put("VideoPlayActivity: 无法加载书籍或章节信息")
                    toastOnUi("加载播放信息失败")
                }
            }
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 初始化VideoPlay模型失败", e)
        }
    }

    /**
     * 启动VideoPlayService
     */
    private fun startVideoPlayService() {
        try {
            val intent = Intent(this, VideoPlayService::class.java)
            intent.action = io.stillpage.app.constant.IntentAction.play
            startService(intent)
            
            AppLog.put("VideoPlayActivity: VideoPlayService启动完成")
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 启动VideoPlayService失败", e)
        }
    }

    /**
     * 播放视频
     */
    private fun playVideo(url: String, headers: Map<String, String> = emptyMap()) {
        lifecycleScope.launch {
            try {
                val mediaItem = ExoPlayerHelper.createVideoMediaItem(url, headers)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                
                AppLog.put("VideoPlayActivity: 视频播放开始")
                
            } catch (e: Exception) {
                AppLog.put("VideoPlayActivity: 播放视频失败", e)
                toastOnUi("播放失败: ${e.message}")
            }
        }
    }

    /**
     * 切换全屏模式
     */
    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        
        if (isFullScreen) {
            enterFullScreen()
        } else {
            exitFullScreen()
        }
    }

    /**
     * 进入全屏模式
     */
    private fun enterFullScreen() {
        AppLog.put("VideoPlayActivity: 进入全屏模式")
        
        // 设置横屏（传感器横屏，支持左右翻转）
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        // 隐藏标题栏
        binding.titleBar.visibility = View.GONE
        
        // 隐藏系统UI，启用沉浸式模式
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // 调整播放器布局参数
        adjustPlayerLayoutForFullScreen(true)
        
        // 更新全屏按钮图标
        binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit)
    }

    /**
     * 退出全屏模式
     */
    private fun exitFullScreen() {
        AppLog.put("VideoPlayActivity: 退出全屏模式")
        
        // 恢复竖屏
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        // 显示标题栏
        binding.titleBar.visibility = View.VISIBLE
        
        // 显示系统UI
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        
        // 调整播放器布局参数
        adjustPlayerLayoutForFullScreen(false)
        
        // 更新全屏按钮图标
        binding.btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
    }

    /**
     * 调整播放器布局以适应全屏/窗口模式
     */
    private fun adjustPlayerLayoutForFullScreen(isFullScreen: Boolean) {
        val layoutParams = binding.playerView.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        
        if (isFullScreen) {
            // 全屏模式：播放器占满整个屏幕
            layoutParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.topToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            layoutParams.bottomToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        } else {
            // 窗口模式：播放器在标题栏和控制栏之间
            layoutParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            layoutParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            layoutParams.topToBottom = binding.titleBar.id
            layoutParams.bottomToTop = binding.controlLayout.id
        }
        
        binding.playerView.layoutParams = layoutParams
    }

    /**
     * 处理配置变化（屏幕旋转等）
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        AppLog.put("VideoPlayActivity: 配置变化 - orientation=${newConfig.orientation}")
        
        when (newConfig.orientation) {
            android.content.res.Configuration.ORIENTATION_LANDSCAPE -> {
                // 横屏时自动进入全屏（如果还没有）
                if (!isFullScreen) {
                    isFullScreen = true
                    enterFullScreen()
                }
            }
            android.content.res.Configuration.ORIENTATION_PORTRAIT -> {
                // 竖屏时自动退出全屏（如果还在全屏）
                if (isFullScreen) {
                    isFullScreen = false
                    exitFullScreen()
                }
            }
        }
        
        // 重新调整手势检测区域
        binding.gestureOverlay.post {
            val displayMetrics = resources.displayMetrics
            // 更新手势视图的屏幕尺寸信息
        }
    }

    /**
     * 处理返回键 - 使用现代的OnBackPressedDispatcher
     */
    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isFullScreen -> {
                        // 全屏时按返回键退出全屏
                        toggleFullScreen()
                    }
                    else -> {
                        // 非全屏时正常退出
                        finish()
                    }
                }
            }
        })
    }

    /**
     * 显示播放速度选择对话框
     */
    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
        val speedValues = arrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择播放速度")
            .setItems(speeds) { _, which ->
                setPlaybackSpeed(speedValues[which])
            }
            .show()
    }

    /**
     * 设置播放速度
     */
    private fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        exoPlayer.setPlaybackSpeed(speed)
        
        // 保存用户偏好
        VideoPlayConfig.defaultPlaySpeed = speed
        
        // 更新按钮显示
        binding.btnSpeed.text = "${speed}x"
        
        AppLog.put("VideoPlayActivity: 设置播放速度 - ${speed}x")
    }

    // ========== ExoPlayer.Listener 实现 ==========

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        
        when (playbackState) {
            Player.STATE_IDLE -> {
                AppLog.put("VideoPlayActivity: 播放器状态 - IDLE")
            }
            Player.STATE_BUFFERING -> {
                AppLog.put("VideoPlayActivity: 播放器状态 - BUFFERING")
                binding.progressBar.visibility = View.VISIBLE
            }
            Player.STATE_READY -> {
                AppLog.put("VideoPlayActivity: 播放器状态 - READY")
                binding.progressBar.visibility = View.GONE
            }
            Player.STATE_ENDED -> {
                AppLog.put("VideoPlayActivity: 播放器状态 - ENDED")
                // 播放结束，可以自动播放下一集
                onVideoEnded()
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        super.onPlayerError(error)
        AppLog.put("VideoPlayActivity: 播放出错", error)
        
        binding.progressBar.visibility = View.GONE
        toastOnUi("播放出错: ${error.message}")
        
        // 可以尝试重新播放或提供其他选项
        showRetryDialog()
    }

    /**
     * 视频播放结束处理
     */
    private fun onVideoEnded() {
        // 这里可以实现自动播放下一集的逻辑
        AppLog.put("VideoPlayActivity: 视频播放结束")
        
        // 暂时简单处理，显示播放结束提示
        toastOnUi("播放结束")
    }

    /**
     * 显示重试对话框
     */
    private fun showRetryDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("播放失败")
            .setMessage("视频播放出现问题，是否重试？")
            .setPositiveButton("重试") { _, _ ->
                startVideoPlay()
            }
            .setNegativeButton("取消") { _, _ ->
                finish()
            }
            .show()
    }

    /**
     * 切换控制栏显示/隐藏
     */
    private fun toggleControlsVisibility() {
        if (binding.controlLayout.visibility == android.view.View.VISIBLE) {
            hideControls()
        } else {
            showControls()
        }
    }

    /**
     * 显示控制栏
     */
    private fun showControls() {
        binding.controlLayout.visibility = android.view.View.VISIBLE
        if (!isFullScreen) {
            binding.titleBar.visibility = android.view.View.VISIBLE
        }
        
        // 3秒后自动隐藏
        binding.controlLayout.removeCallbacks(hideControlsRunnable)
        binding.controlLayout.postDelayed(hideControlsRunnable, 3000)
    }

    /**
     * 隐藏控制栏
     */
    private fun hideControls() {
        binding.controlLayout.visibility = android.view.View.GONE
        if (isFullScreen) {
            binding.titleBar.visibility = android.view.View.GONE
        }
    }

    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    /**
     * 显示音量指示器
     */
    private fun showVolumeIndicator(volume: Int, maxVolume: Int) {
        binding.volumeBrightnessLayout.visibility = android.view.View.VISIBLE
        binding.ivVolumeBrightness.setImageResource(
            if (volume == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        )
        binding.tvVolumeBrightness.text = "${(volume * 100 / maxVolume)}%"
        
        // 2秒后隐藏
        binding.volumeBrightnessLayout.removeCallbacks(hideVolumeIndicatorRunnable)
        binding.volumeBrightnessLayout.postDelayed(hideVolumeIndicatorRunnable, 2000)
    }

    /**
     * 显示亮度指示器
     */
    private fun showBrightnessIndicator(brightness: Float) {
        binding.volumeBrightnessLayout.visibility = android.view.View.VISIBLE
        binding.ivVolumeBrightness.setImageResource(R.drawable.ic_brightness_6)
        binding.tvVolumeBrightness.text = "${(brightness * 100).toInt()}%"
        
        // 2秒后隐藏
        binding.volumeBrightnessLayout.removeCallbacks(hideVolumeIndicatorRunnable)
        binding.volumeBrightnessLayout.postDelayed(hideVolumeIndicatorRunnable, 2000)
    }

    private val hideVolumeIndicatorRunnable = Runnable {
        binding.volumeBrightnessLayout.visibility = android.view.View.GONE
    }

    /**
     * 显示进度指示器
     */
    private fun showProgressIndicator(progressChange: Long) {
        binding.progressLayout.visibility = android.view.View.VISIBLE
        
        val currentPos = exoPlayer.currentPosition
        val newPos = currentPos + progressChange
        val duration = exoPlayer.duration
        
        val currentTime = formatTime(newPos)
        val totalTime = if (duration > 0) formatTime(duration) else "--:--"
        
        binding.tvProgress.text = "$currentTime / $totalTime"
        binding.ivProgress.setImageResource(
            if (progressChange > 0) R.drawable.ic_fast_forward else R.drawable.ic_fast_rewind
        )
    }

    /**
     * 隐藏进度指示器
     */
    private fun hideProgressIndicator() {
        binding.progressLayout.visibility = android.view.View.GONE
    }

    /**
     * 格式化时间显示
     */
    private fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * 更新播放/暂停按钮状态
     */
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        // ExoPlayer的PlayerView会自动处理播放/暂停按钮状态
        // 这里可以添加额外的UI更新逻辑
    }

    /**
     * 显示弹出菜单
     */
    private fun showPopupMenu() {
        val popup = androidx.appcompat.widget.PopupMenu(this, binding.btnMenu)
        popup.menuInflater.inflate(R.menu.menu_video_play, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            onCompatOptionsItemSelected(item)
        }
        
        popup.show()
    }

    /**
     * 快退
     */
    private fun seekBackward(milliseconds: Long) {
        val newPosition = maxOf(0, exoPlayer.currentPosition - milliseconds)
        exoPlayer.seekTo(newPosition)
        showSeekIndicator(-milliseconds)
        AppLog.put("VideoPlayActivity: 快退${milliseconds/1000}秒到 ${formatTime(newPosition)}")
    }

    /**
     * 快进
     */
    private fun seekForward(milliseconds: Long) {
        val duration = exoPlayer.duration
        val newPosition = if (duration > 0) {
            minOf(duration, exoPlayer.currentPosition + milliseconds)
        } else {
            exoPlayer.currentPosition + milliseconds
        }
        exoPlayer.seekTo(newPosition)
        showSeekIndicator(milliseconds)
        AppLog.put("VideoPlayActivity: 快进${milliseconds/1000}秒到 ${formatTime(newPosition)}")
    }

    /**
     * 显示快进快退指示器
     */
    private fun showSeekIndicator(seekMs: Long) {
        binding.progressLayout.visibility = android.view.View.VISIBLE
        
        val seekSeconds = seekMs / 1000
        binding.tvProgress.text = if (seekMs > 0) "+${seekSeconds}秒" else "${seekSeconds}秒"
        binding.ivProgress.setImageResource(
            if (seekMs > 0) R.drawable.ic_fast_forward else R.drawable.ic_fast_rewind
        )
        
        // 1秒后隐藏
        binding.progressLayout.removeCallbacks(hideSeekIndicatorRunnable)
        binding.progressLayout.postDelayed(hideSeekIndicatorRunnable, 1000)
    }

    private val hideSeekIndicatorRunnable = Runnable {
        binding.progressLayout.visibility = android.view.View.GONE
    }

    /**
     * 更新章节信息
     */
    private fun updateChapterInfo(chapterIndex: Int) {
        lifecycleScope.launch {
            try {
                val book = viewModel.bookData.value ?: return@launch
                val chapters = viewModel.chapterListData.value ?: return@launch
                
                if (chapterIndex in chapters.indices) {
                    val chapter = chapters[chapterIndex]
                    binding.titleBar.title = "${book.name} 第${chapterIndex + 1}集"
                    videoTitle = "${book.name} 第${chapterIndex + 1}集"
                    
                    AppLog.put("VideoPlayActivity: 更新章节信息 - ${chapter.title}")
                }
            } catch (e: Exception) {
                AppLog.put("VideoPlayActivity: 更新章节信息失败", e)
            }
        }
    }

    /**
     * 显示视频质量选择对话框
     */
    private fun showVideoQualityDialog() {
        val qualities = arrayOf("自动", "1080P", "720P", "480P", "360P")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("选择视频质量")
            .setItems(qualities) { _, which ->
                val quality = qualities[which]
                toastOnUi("已选择: $quality")
                AppLog.put("VideoPlayActivity: 选择视频质量 - $quality")
                // 这里可以实现实际的质量切换逻辑
            }
            .show()
    }

    /**
     * 显示播放列表对话框
     */
    private fun showPlaylistDialog() {
        lifecycleScope.launch {
            try {
                val chapters = viewModel.chapterListData.value ?: return@launch
                val currentIndex = VideoPlay.durChapterIndex
                
                val chapterTitles = chapters.mapIndexed { index, chapter ->
                    val prefix = if (index == currentIndex) "▶ " else "   "
                    "${prefix}第${index + 1}集: ${chapter.title}"
                }.toTypedArray()
                
                androidx.appcompat.app.AlertDialog.Builder(this@VideoPlayActivity)
                    .setTitle("播放列表")
                    .setItems(chapterTitles) { _, which ->
                        if (which != currentIndex) {
                            VideoPlay.skipTo(which)
                            AppLog.put("VideoPlayActivity: 从播放列表跳转到第${which + 1}集")
                        }
                    }
                    .show()
                    
            } catch (e: Exception) {
                AppLog.put("VideoPlayActivity: 显示播放列表失败", e)
                toastOnUi("获取播放列表失败")
            }
        }
    }

    /**
     * 显示循环模式对话框
     */
    private fun showLoopModeDialog() {
        val modes = arrayOf("列表播放完停止", "列表循环播放", "单集循环播放")
        val currentMode = VideoPlay.playMode.ordinal
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("循环播放模式")
            .setSingleChoiceItems(modes, currentMode) { dialog, which ->
                VideoPlay.playMode = VideoPlay.PlayMode.values()[which]
                toastOnUi("已设置: ${modes[which]}")
                AppLog.put("VideoPlayActivity: 设置循环模式 - ${modes[which]}")
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 显示缓存管理对话框
     */
    private fun showCacheManageDialog() {
        lifecycleScope.launch {
            try {
                val cacheStats = ExoPlayerHelper.getCacheStats()
                
                val message = buildString {
                    appendLine("缓存统计信息:")
                    appendLine("总大小: ${cacheStats.totalSizeMB.toInt()}MB")
                    appendLine("已使用: ${cacheStats.usedSizeMB.toInt()}MB (${cacheStats.usagePercentage.toInt()}%)")
                    appendLine("剩余空间: ${cacheStats.freeSpaceMB.toInt()}MB")
                    appendLine("文件数量: ${cacheStats.fileCount}个")
                    appendLine()
                    appendLine("选择操作:")
                }
                
                val options = arrayOf("清理所有缓存", "清理过期缓存", "查看详细信息", "诊断检查")
                
                androidx.appcompat.app.AlertDialog.Builder(this@VideoPlayActivity)
                    .setTitle("缓存管理")
                    .setMessage(message)
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> clearAllCache()
                            1 -> clearExpiredCache()
                            2 -> showCacheDetails()
                            3 -> runDiagnostics()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
                    
            } catch (e: Exception) {
                AppLog.put("VideoPlayActivity: 显示缓存管理失败", e)
                toastOnUi("获取缓存信息失败")
            }
        }
    }

    /**
     * 清理所有缓存
     */
    private fun clearAllCache() {
        lifecycleScope.launch {
            try {
                ExoPlayerHelper.clearCache()
                toastOnUi("所有缓存已清理")
                AppLog.put("VideoPlayActivity: 清理所有缓存完成")
            } catch (e: Exception) {
                AppLog.put("VideoPlayActivity: 清理缓存失败", e)
                toastOnUi("清理缓存失败")
            }
        }
    }

    /**
     * 清理过期缓存
     */
    private fun clearExpiredCache() {
        lifecycleScope.launch {
            try {
                val expireHours = VideoPlayConfig.cacheExpireHours
                ExoPlayerHelper.clearExpiredCache(expireHours)
                toastOnUi("过期缓存已清理")
                AppLog.put("VideoPlayActivity: 清理过期缓存完成")
            } catch (e: Exception) {
                AppLog.put("VideoPlayActivity: 清理过期缓存失败", e)
                toastOnUi("清理过期缓存失败")
            }
        }
    }

    /**
     * 显示缓存详细信息
     */
    private fun showCacheDetails() {
        lifecycleScope.launch {
            try {
                val cacheStats = ExoPlayerHelper.getCacheStats()
                val configSummary = VideoPlayConfig.getConfigSummary()
                
                val details = buildString {
                    appendLine("缓存详细信息:")
                    appendLine("=".repeat(30))
                    appendLine("总容量: ${cacheStats.totalSizeMB.toInt()}MB")
                    appendLine("已使用: ${cacheStats.usedSizeMB.toInt()}MB")
                    appendLine("使用率: ${cacheStats.usagePercentage.toInt()}%")
                    appendLine("文件数: ${cacheStats.fileCount}个")
                    appendLine("剩余空间: ${cacheStats.freeSpaceMB.toInt()}MB")
                    appendLine()
                    appendLine(configSummary)
                }
                
                androidx.appcompat.app.AlertDialog.Builder(this@VideoPlayActivity)
                    .setTitle("缓存详情")
                    .setMessage(details)
                    .setPositiveButton("确定", null)
                    .show()
                    
            } catch (e: Exception) {
                AppLog.put("VideoPlayActivity: 显示缓存详情失败", e)
                toastOnUi("获取缓存详情失败")
            }
        }
    }

    /**
     * 运行诊断检查
     */
    private fun runDiagnostics() {
        lifecycleScope.launch {
            try {
                toastOnUi("正在运行诊断检查...")
                
                // 获取当前播放的URL进行诊断
                val currentUrl = chapterUrl ?: ""
                if (currentUrl.isNotBlank()) {
                    val diagnosticResult = VideoPlayDiagnostics.diagnoseVideoUrl(currentUrl)
                    val report = diagnosticResult.toDetailedString()
                    
                    // 记录到日志
                    AppLog.put("VideoPlayActivity: 诊断报告:\n$report")
                    
                    // 显示报告
                    androidx.appcompat.app.AlertDialog.Builder(this@VideoPlayActivity)
                        .setTitle("诊断报告")
                        .setMessage(report)
                        .setPositiveButton("确定", null)
                        .setNeutralButton("复制报告") { _, _ ->
                            copyToClipboard(report)
                        }
                        .show()
                } else {
                    toastOnUi("没有可诊断的视频URL")
                }
                    
                AppLog.put("VideoPlayActivity: 诊断检查完成")
                
            } catch (e: Exception) {
                AppLog.put("VideoPlayActivity: 诊断检查失败", e)
                toastOnUi("诊断检查失败")
            }
        }
    }

    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("诊断报告", text)
            clipboard.setPrimaryClip(clip)
            toastOnUi("报告已复制到剪贴板")
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 复制到剪贴板失败", e)
            toastOnUi("复制失败")
        }
    }

    /**
     * 切换字幕显示
     */
    private fun toggleSubtitle(enable: Boolean) {
        try {
            // ExoPlayer字幕控制
            val trackSelector = exoPlayer.trackSelector
            if (trackSelector is androidx.media3.exoplayer.trackselection.DefaultTrackSelector) {
                val parametersBuilder = trackSelector.parameters.buildUpon()
                
                if (enable) {
                    // 启用字幕
                    parametersBuilder.setRendererDisabled(2, false) // 字幕轨道通常是索引2
                } else {
                    // 禁用字幕
                    parametersBuilder.setRendererDisabled(2, true)
                }
                
                trackSelector.setParameters(parametersBuilder)
                toastOnUi(if (enable) "字幕已开启" else "字幕已关闭")
                AppLog.put("VideoPlayActivity: 字幕${if (enable) "开启" else "关闭"}")
            }
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 切换字幕失败", e)
            toastOnUi("字幕控制失败")
        }
    }

    /**
     * 显示字幕语言选择对话框
     */
    private fun showSubtitleLanguageDialog() {
        try {
            val tracks = exoPlayer.currentTracks
            val subtitleTracks = mutableListOf<String>()
            
            // 获取可用的字幕轨道
            for (trackGroup in tracks.groups) {
                if (trackGroup.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val language = format.language ?: "未知语言"
                        subtitleTracks.add(language)
                    }
                }
            }
            
            if (subtitleTracks.isEmpty()) {
                toastOnUi("当前视频没有字幕轨道")
                return
            }
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择字幕语言")
                .setItems(subtitleTracks.toTypedArray()) { _, which ->
                    toastOnUi("已选择字幕: ${subtitleTracks[which]}")
                    AppLog.put("VideoPlayActivity: 选择字幕语言 - ${subtitleTracks[which]}")
                    // 这里可以实现实际的字幕轨道切换
                }
                .show()
                
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 显示字幕语言选择失败", e)
            toastOnUi("获取字幕信息失败")
        }
    }

    /**
     * 显示音轨选择对话框
     */
    private fun showAudioTrackDialog() {
        try {
            val tracks = exoPlayer.currentTracks
            val audioTracks = mutableListOf<String>()
            
            // 获取可用的音轨
            for (trackGroup in tracks.groups) {
                if (trackGroup.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val language = format.language ?: "默认音轨"
                        val bitrate = if (format.bitrate > 0) " (${format.bitrate/1000}kbps)" else ""
                        audioTracks.add("$language$bitrate")
                    }
                }
            }
            
            if (audioTracks.isEmpty()) {
                toastOnUi("当前视频只有一个音轨")
                return
            }
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择音轨")
                .setItems(audioTracks.toTypedArray()) { _, which ->
                    toastOnUi("已选择音轨: ${audioTracks[which]}")
                    AppLog.put("VideoPlayActivity: 选择音轨 - ${audioTracks[which]}")
                    // 这里可以实现实际的音轨切换
                }
                .show()
                
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 显示音轨选择失败", e)
            toastOnUi("获取音轨信息失败")
        }
    }

    /**
     * 显示视频信息
     */
    private fun showVideoInfo() {
        val info = StringBuilder()
        info.append("视频标题: ${videoTitle ?: "未知"}\n")
        info.append("播放速度: ${currentSpeed}x\n")
        info.append("视频时长: ${formatTime(exoPlayer.duration)}\n")
        info.append("当前位置: ${formatTime(exoPlayer.currentPosition)}\n")
        
        // 获取视频格式信息
        try {
            val videoFormat = exoPlayer.videoFormat
            if (videoFormat != null) {
                info.append("分辨率: ${videoFormat.width}x${videoFormat.height}\n")
                info.append("帧率: ${videoFormat.frameRate}fps\n")
                info.append("编码: ${videoFormat.codecs ?: "未知"}\n")
            }
            
            val audioFormat = exoPlayer.audioFormat
            if (audioFormat != null) {
                info.append("音频编码: ${audioFormat.codecs ?: "未知"}\n")
                info.append("采样率: ${audioFormat.sampleRate}Hz\n")
                info.append("声道数: ${audioFormat.channelCount}\n")
            }
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 获取格式信息失败", e)
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("视频信息")
            .setMessage(info.toString())
            .setPositiveButton("确定", null)
            .show()
    }

    // ========== 菜单处理 ==========

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_video_play, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_seek_back -> {
                seekBackward(10000) // 快退10秒
                return true
            }
            R.id.action_seek_forward -> {
                seekForward(10000) // 快进10秒
                return true
            }
            R.id.action_toggle_fullscreen -> {
                toggleFullScreen()
                return true
            }
            R.id.action_speed_075 -> {
                setPlaybackSpeed(0.75f)
                return true
            }
            R.id.action_speed_10 -> {
                setPlaybackSpeed(1.0f)
                return true
            }
            R.id.action_speed_125 -> {
                setPlaybackSpeed(1.25f)
                return true
            }
            R.id.action_speed_150 -> {
                setPlaybackSpeed(1.5f)
                return true
            }
            R.id.action_pip -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    enterPipMode()
                }
                return true
            }
            R.id.action_video_quality -> {
                showVideoQualityDialog()
                return true
            }
            R.id.action_playlist -> {
                showPlaylistDialog()
                return true
            }
            R.id.action_loop_mode -> {
                showLoopModeDialog()
                return true
            }
            R.id.action_video_info -> {
                showVideoInfo()
                return true
            }
            R.id.action_cache_manage -> {
                showCacheManageDialog()
                return true
            }
            R.id.action_subtitle_on -> {
                toggleSubtitle(true)
                return true
            }
            R.id.action_subtitle_off -> {
                toggleSubtitle(false)
                return true
            }
            R.id.action_subtitle_lang_select -> {
                showSubtitleLanguageDialog()
                return true
            }
            R.id.action_audio_track_select -> {
                showAudioTrackDialog()
                return true
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    // ========== 画中画支持 ==========

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun enterPipMode() {
        try {
            // 检查是否支持画中画
            if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                toastOnUi("设备不支持画中画模式")
                return
            }
            
            // 创建画中画参数
            val aspectRatio = calculateVideoAspectRatio()
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setActions(createPipActions())
                .build()
            
            val result = enterPictureInPictureMode(params)
            if (result) {
                AppLog.put("VideoPlayActivity: 成功进入画中画模式")
            } else {
                AppLog.put("VideoPlayActivity: 进入画中画模式失败")
                toastOnUi("无法进入画中画模式")
            }
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 画中画模式异常", e)
            toastOnUi("画中画模式出错")
        }
    }

    /**
     * 计算视频宽高比
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun calculateVideoAspectRatio(): android.util.Rational {
        return try {
            val videoFormat = exoPlayer.videoFormat
            if (videoFormat != null && videoFormat.width > 0 && videoFormat.height > 0) {
                android.util.Rational(videoFormat.width, videoFormat.height)
            } else {
                // 默认16:9比例
                android.util.Rational(16, 9)
            }
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 计算视频宽高比失败", e)
            android.util.Rational(16, 9)
        }
    }

    /**
     * 创建画中画模式的操作按钮
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun createPipActions(): List<android.app.RemoteAction> {
        val actions = mutableListOf<android.app.RemoteAction>()
        
        try {
            // 播放/暂停按钮
            val playPauseIcon = if (exoPlayer.isPlaying) {
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pause_24dp)
            } else {
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_play_24dp)
            }
            
            val playPauseTitle = if (exoPlayer.isPlaying) "暂停" else "播放"
            val playPauseIntent = android.app.PendingIntent.getBroadcast(
                this,
                1,
                android.content.Intent("ACTION_PLAY_PAUSE"),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            val playPauseAction = android.app.RemoteAction(
                playPauseIcon,
                playPauseTitle,
                playPauseTitle,
                playPauseIntent
            )
            actions.add(playPauseAction)
            
            // 下一集按钮（如果有）
            if (hasNextChapter()) {
                val nextIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_skip_next)
                val nextIntent = android.app.PendingIntent.getBroadcast(
                    this,
                    2,
                    android.content.Intent("ACTION_NEXT"),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                
                val nextAction = android.app.RemoteAction(
                    nextIcon,
                    "下一集",
                    "下一集",
                    nextIntent
                )
                actions.add(nextAction)
            }
            
        } catch (e: Exception) {
            AppLog.put("VideoPlayActivity: 创建画中画操作失败", e)
        }
        
        return actions
    }

    /**
     * 检查是否有下一章节
     */
    private fun hasNextChapter(): Boolean {
        val chapters = viewModel.chapterListData.value ?: return false
        val currentIndex = VideoPlay.durChapterIndex
        return currentIndex < chapters.size - 1
    }

    /**
     * 画中画模式变化回调
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        
        AppLog.put("VideoPlayActivity: 画中画模式变化 - $isInPictureInPictureMode")
        
        if (isInPictureInPictureMode) {
            // 进入画中画模式
            onEnterPictureInPictureMode()
        } else {
            // 退出画中画模式
            onExitPictureInPictureMode()
        }
    }

    /**
     * 进入画中画模式时的处理
     */
    private fun onEnterPictureInPictureMode() {
        // 隐藏所有UI控件，只保留视频播放
        binding.titleBar.visibility = View.GONE
        binding.controlLayout.visibility = View.GONE
        binding.volumeBrightnessLayout.visibility = View.GONE
        binding.progressLayout.visibility = View.GONE
        
        // 禁用手势控制
        binding.gestureOverlay.visibility = View.GONE
        
        AppLog.put("VideoPlayActivity: 已进入画中画模式，隐藏UI")
    }

    /**
     * 退出画中画模式时的处理
     */
    private fun onExitPictureInPictureMode() {
        // 恢复UI控件
        if (!isFullScreen) {
            binding.titleBar.visibility = View.VISIBLE
        }
        binding.controlLayout.visibility = View.VISIBLE
        
        // 恢复手势控制
        binding.gestureOverlay.visibility = View.VISIBLE
        
        AppLog.put("VideoPlayActivity: 已退出画中画模式，恢复UI")
    }

    /**
     * 用户离开应用时自动进入画中画（如果正在播放）
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
            exoPlayer.isPlaying && 
            !isFinishing && 
            !isDestroyed) {
            
            AppLog.put("VideoPlayActivity: 用户离开应用，自动进入画中画")
            enterPipMode()
        }
    }

    // ========== VideoPlay.CallBack 实现 ==========

    override fun upContent(bookChapter: BookChapter, nextChapterUrl: String?) {
        // 实现章节内容更新逻辑
    }

    override fun onVideoUrlParsed(url: String, headers: Map<String, String>) {
        // 视频URL解析完成，开始播放
        playVideo(url, headers)
    }

    override fun onVideoError(error: String) {
        // 视频解析或播放错误
        AppLog.put("VideoPlayActivity: 视频错误 - $error")
        toastOnUi(error)
    }

    // ========== 生命周期管理 ==========

    override fun onResume() {
        super.onResume()
        if (::exoPlayer.isInitialized) {
            exoPlayer.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::exoPlayer.isInitialized) {
            exoPlayer.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 释放播放器资源
        if (::exoPlayer.isInitialized) {
            exoPlayer.removeListener(this)
            exoPlayer.release()
        }
        
        // 取消注册回调
        VideoPlay.unregisterCallBack()
        
        // 清除屏幕常亮标志
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        AppLog.put("VideoPlayActivity: 资源释放完成")
    }
}
package io.stillpage.app.service

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioFocusRequestCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.stillpage.app.R
import io.stillpage.app.base.BaseService
import io.stillpage.app.constant.AppConst
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.EventBus
import io.stillpage.app.constant.IntentAction
import io.stillpage.app.constant.NotificationId
import io.stillpage.app.constant.Status
import io.stillpage.app.help.MediaHelp
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.config.VideoPlayConfig
import io.stillpage.app.help.coroutine.Coroutine
import io.stillpage.app.help.exoplayer.ExoPlayerHelper
import io.stillpage.app.help.glide.ImageLoader
import io.stillpage.app.model.VideoPlay
import io.stillpage.app.model.analyzeRule.AnalyzeUrl
import io.stillpage.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem
import io.stillpage.app.receiver.MediaButtonReceiver
import io.stillpage.app.ui.book.video.VideoPlayActivity
import io.stillpage.app.utils.activityPendingIntent
import io.stillpage.app.utils.broadcastPendingIntent
import io.stillpage.app.utils.postEvent
import io.stillpage.app.utils.printOnDebug
import io.stillpage.app.utils.servicePendingIntent
import io.stillpage.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.init.appCtx
import splitties.systemservices.audioManager
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager
import splitties.systemservices.wifiManager

/**
 * 视频播放服务
 * 支持后台播放、画中画模式和媒体会话管理
 */
class VideoPlayService : BaseService(),
    AudioManager.OnAudioFocusChangeListener,
    Player.Listener {

    companion object {
        @JvmStatic
        var isRun = false
            private set

        @JvmStatic
        var pause = true
            private set

        @JvmStatic
        var timeMinute: Int = 0

        var url: String = ""
            private set

        @JvmStatic
        var instance: VideoPlayService? = null
            private set

        private const val MEDIA_SESSION_ACTIONS = (PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SEEK_TO)

        private const val APP_ACTION_STOP = "Stop"
        private const val APP_ACTION_TIMER = "Timer"
    }

    private val useWakeLock = AppConfig.videoPlayUseWakeLock
    private val wakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:VideoPlayService")
            .apply {
                this.setReferenceCounted(false)
            }
    }
    private val wifiLock by lazy {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, "legado:VideoPlayService")?.apply {
            setReferenceCounted(false)
        }
    }
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionCallback: MediaSessionCallback
    private var broadcastReceiver: BroadcastReceiver? = null
    private var needResumeOnAudioFocusGain = false
    private var position = VideoPlay.book?.durChapterPos ?: 0
    private var dsJob: Job? = null
    private var upNotificationJob: Coroutine<*>? = null
    private var playSpeed = VideoPlayConfig.defaultPlaySpeed
    private var cover: Bitmap? = 
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.image_cover_default)
    
    private val mFocusRequest: AudioFocusRequestCompat by lazy {
        MediaHelp.buildAudioFocusRequestCompat(this)
    }

    override fun onCreate() {
        super.onCreate()
        isRun = true
        instance = this
        exoPlayer = ExoPlayerHelper.createVideoExoPlayer(this)
        exoPlayer.addListener(this)
        // 应用持久化的播放速度
        playSpeed = VideoPlayConfig.defaultPlaySpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            kotlin.runCatching { exoPlayer.setPlaybackSpeed(playSpeed) }
        }
        VideoPlay.registerService(this)
        initMediaSession()
        initBroadcastReceiver()
        upMediaMetadata()
        execute {
            ImageLoader
                .loadBitmap(this@VideoPlayService, VideoPlay.book?.getDisplayCover())
                .submit()
                .get()
        }.onSuccess {
            cover = it
            upMediaMetadata()
            upVideoPlayNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                IntentAction.play -> {
                    upPlayProgressJob?.cancel()
                    pause = false
                    position = VideoPlay.book?.durChapterPos ?: 0
                    url = VideoPlay.durPlayUrl
                    play()
                }
                IntentAction.resume -> {
                    upPlayProgressJob?.cancel()
                    pause = false
                    position = 0
                    url = VideoPlay.durPlayUrl
                    play()
                }
                IntentAction.stop -> {
                    exoPlayer.stop()
                    upPlayProgressJob?.cancel()
                    VideoPlay.status = Status.STOP
                    postEvent(EventBus.VIDEO_STATE, Status.STOP)
                }
                IntentAction.pause -> pause()
                IntentAction.resume -> resume()
                IntentAction.prev -> VideoPlay.prev()
                IntentAction.next -> VideoPlay.next()
                IntentAction.adjustSpeed -> upSpeed(intent.getFloatExtra("adjust", 1f))
                IntentAction.addTimer -> addTimer()
                IntentAction.setTimer -> setTimer(intent.getIntExtra("minute", 0))
                else -> stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRun = false
        instance = null
        exoPlayer.removeListener(this)
        exoPlayer.release()
        mediaSession.release()
        unregisterReceiver(broadcastReceiver)
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        VideoPlay.status = Status.STOP
        postEvent(EventBus.VIDEO_STATE, Status.STOP)
        VideoPlay.unregisterService()
        upNotificationJob?.invokeOnCompletion {
            notificationManager.cancel(NotificationId.VideoPlayService)
        }
    }

    private fun play() {
        upNotificationJob?.cancel()
        if (useWakeLock) {
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
            wifiLock?.acquire()
        }
        upVideoPlayNotification()
        if (!requestFocus()) {
            return
        }
        execute(context = Main) {
            VideoPlay.status = Status.STOP
            postEvent(EventBus.VIDEO_STATE, Status.STOP)
            upPlayProgressJob?.cancel()
            val analyzeUrl = AnalyzeUrl(
                url,
                source = VideoPlay.bookSource,
                ruleData = VideoPlay.book,
                chapter = VideoPlay.durChapter,
                headerMapF = VideoPlay.headerMap,
                coroutineContext = coroutineContext
            )
            kotlin.runCatching {
                val mediaItem = analyzeUrl.getMediaItem()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playWhenReady = true
                exoPlayer.prepare()
            }.onFailure {
                AppLog.put("VideoPlayService播放出错\n${it.localizedMessage}", it)
                toastOnUi("VideoPlayService播放出错\n${it.localizedMessage}")
            }
        }.onError {
            AppLog.put("VideoPlayService播放出错\n${it.localizedMessage}", it)
            toastOnUi("VideoPlayService播放出错\n${it.localizedMessage}")
        }
    }

    private fun pause() {
        if (exoPlayer.isPlaying) exoPlayer.pause()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        VideoPlay.status = Status.PAUSE
        postEvent(EventBus.VIDEO_STATE, Status.PAUSE)
        upVideoPlayNotification()
        upPlayProgressJob?.cancel()
        pause = true
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
    }

    private fun resume() {
        pause = false
        if (!requestFocus()) return
        if (useWakeLock) {
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
            wifiLock?.acquire()
        }
        exoPlayer.play()
        upVideoPlayNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        VideoPlay.status = Status.PLAY
        postEvent(EventBus.VIDEO_STATE, Status.PLAY)
    }

    private fun upSpeed(adjust: Float) {
        kotlin.runCatching {
            with(exoPlayer) {
                playSpeed += adjust
                setPlaybackSpeed(playSpeed)
                VideoPlayConfig.defaultPlaySpeed = playSpeed
            }
        }
        postEvent(EventBus.VIDEO_SPEED, playSpeed)
    }

    /**
     * 更新播放进度
     */
    private val upPlayProgressJob: Coroutine<Unit>? = null

    private fun startUpPlayProgressJob() {
        upPlayProgressJob?.cancel()
        lifecycleScope.launch {
            while (isActive) {
                if (!pause) {
                    position = exoPlayer.currentPosition.toInt()
                    VideoPlay.book?.durChapterPos = position
                    postEvent(EventBus.VIDEO_PROGRESS, position)
                }
                delay(1000)
            }
        }
    }

    /**
     * 请求音频焦点
     */
    private fun requestFocus(): Boolean {
        if (needResumeOnAudioFocusGain) {
            return true
        }
        val requestFocus = MediaHelp.requestFocus(mFocusRequest)
        if (!requestFocus) {
            toastOnUi("未获取到音频焦点")
        }
        return requestFocus
    }

    /**
     * 初始化MediaSession
     */
    private fun initMediaSession() {
        mediaSessionCallback = MediaSessionCallback()
        mediaSession = MediaSessionCompat(this, "VideoPlayService")
        mediaSession.setCallback(mediaSessionCallback)
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession.isActive = true
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_NONE)
    }

    private fun upMediaSessionPlaybackState(state: Int) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(MEDIA_SESSION_ACTIONS)
                .setState(state, position.toLong(), 1f)
                .build()
        )
    }

    private fun upMediaMetadata() {
        VideoPlay.book?.let { book ->
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, book.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, book.author)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, VideoPlay.durChapter?.title)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, exoPlayer.duration)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover)
                    .build()
            )
        }
    }

    /**
     * 初始化广播接收器
     */
    private fun initBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                    pause()
                }
            }
        }
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    /**
     * 更新通知
     */
    private fun upVideoPlayNotification() {
        upNotificationJob?.cancel()
        upNotificationJob = execute {
            createNotification()
        }
    }

    private fun createNotification(): NotificationCompat.Builder {
        val book = VideoPlay.book
        val builder = NotificationCompat.Builder(this, AppConst.channelIdReadAloud)
            .setSmallIcon(R.drawable.ic_play_24dp)
            .setLargeIcon(cover)
            .setOngoing(true)
            .setContentTitle(book?.name ?: getString(R.string.video_play))
            .setContentText(VideoPlay.durChapter?.title ?: "")
            .setContentIntent(
                activityPendingIntent<VideoPlayActivity>("activity") {
                    putExtra("bookUrl", book?.bookUrl)
                    putExtra("inBookshelf", VideoPlay.inBookshelf)
                }
            )
        
        if (pause) {
            builder.addAction(
                R.drawable.ic_play_24dp,
                getString(R.string.resume),
                broadcastPendingIntent<MediaButtonReceiver>(IntentAction.resume)
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause_24dp,
                getString(R.string.pause),
                broadcastPendingIntent<MediaButtonReceiver>(IntentAction.pause)
            )
        }
        
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.stop),
            broadcastPendingIntent<MediaButtonReceiver>(IntentAction.stop)
        )
        
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1)
                .setMediaSession(mediaSession.sessionToken)
        )
        
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val notification = builder.build()
        startForeground(NotificationId.VideoPlayService, notification)
        return builder
    }

    /**
     * 画中画模式支持
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPictureInPictureMode(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build()
            
            // 这里需要Activity的支持，Service本身不能直接进入PiP
            // 通过事件通知Activity进入PiP模式
            postEvent(EventBus.VIDEO_PIP_REQUEST, true)
            return true
        }
        return false
    }

    // ========== ExoPlayer.Listener 实现 ==========

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        when (playbackState) {
            Player.STATE_IDLE -> {
                AppLog.put("VideoPlayService: 播放器状态 - IDLE")
            }
            Player.STATE_BUFFERING -> {
                AppLog.put("VideoPlayService: 播放器状态 - BUFFERING")
            }
            Player.STATE_READY -> {
                AppLog.put("VideoPlayService: 播放器状态 - READY")
                if (exoPlayer.playWhenReady) {
                    VideoPlay.status = Status.PLAY
                    postEvent(EventBus.VIDEO_STATE, Status.PLAY)
                    upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    startUpPlayProgressJob()
                }
                upMediaMetadata()
            }
            Player.STATE_ENDED -> {
                AppLog.put("VideoPlayService: 播放器状态 - ENDED")
                VideoPlay.next()
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        AppLog.put("VideoPlayService播放出错", error)
        
        // 启动诊断分析
        lifecycleScope.launch {
            try {
                val currentUrl = VideoPlay.durChapter?.url ?: ""
                if (currentUrl.isNotBlank()) {
                    AppLog.put("VideoPlayService: 开始诊断播放失败的URL - $currentUrl")
                    val diagnosticResult = io.stillpage.app.ui.book.video.VideoPlayDiagnostics.diagnoseVideoUrl(currentUrl)
                    AppLog.put("VideoPlayService: 诊断报告:\n${diagnosticResult.toDetailedString()}")
                    
                    // 如果诊断找到了可用的视频URL，尝试重新播放
                    if (diagnosticResult.parsedVideoUrls.isNotEmpty()) {
                        val firstValidUrl = diagnosticResult.parsedVideoUrls.firstOrNull { url ->
                            ExoPlayerHelper.isDirectVideoUrl(url)
                        }
                        
                        if (firstValidUrl != null) {
                            AppLog.put("VideoPlayService: 诊断找到可用URL，尝试重新播放 - $firstValidUrl")
                            toastOnUi("检测到可用视频源，正在重试...")
                            
                            // 使用诊断找到的URL重新播放
                            VideoPlay.onVideoUrlParsed(firstValidUrl, emptyMap())
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.put("VideoPlayService: 诊断过程出错", e)
            }
            
            // 如果诊断没有找到可用URL，显示原始错误
            val errorMessage = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败"
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接超时"
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "视频格式错误"
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "播放列表格式错误"
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "无法识别的视频格式，可能需要解析API响应"
                else -> "播放出错: ${error.message}"
            }
            toastOnUi(errorMessage)
        }
        
        VideoPlay.status = Status.STOP
        postEvent(EventBus.VIDEO_STATE, Status.STOP)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        if (isPlaying) {
            VideoPlay.status = Status.PLAY
            postEvent(EventBus.VIDEO_STATE, Status.PLAY)
        } else {
            VideoPlay.status = Status.PAUSE
            postEvent(EventBus.VIDEO_STATE, Status.PAUSE)
        }
    }

    // ========== AudioManager.OnAudioFocusChangeListener 实现 ==========

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (needResumeOnAudioFocusGain) {
                    needResumeOnAudioFocusGain = false
                    resume()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (!pause) {
                    needResumeOnAudioFocusGain = true
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 降低音量，这里暂时暂停
                if (!pause) {
                    needResumeOnAudioFocusGain = true
                    pause()
                }
            }
        }
    }

    // ========== 定时器功能 ==========

    private fun addTimer() {
        if (timeMinute == 180) {
            timeMinute = 0
        } else {
            timeMinute += 15
        }
        if (timeMinute > 0) {
            val msg = getString(R.string.timer_m, timeMinute)
            toastOnUi(msg)
        } else {
            toastOnUi("定时器已取消")
        }
    }

    private fun setTimer(minute: Int) {
        timeMinute = minute
        upVideoPlayNotification()
    }

    /**
     * MediaSession回调
     */
    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        
        override fun onPlay() {
            resume()
        }

        override fun onPause() {
            pause()
        }

        override fun onStop() {
            VideoPlay.stop(this@VideoPlayService)
        }

        override fun onSkipToNext() {
            VideoPlay.next()
        }

        override fun onSkipToPrevious() {
            VideoPlay.prev()
        }

        override fun onSeekTo(pos: Long) {
            exoPlayer.seekTo(pos)
            position = pos.toInt()
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            exoPlayer.setPlaybackSpeed(speed)
            playSpeed = speed
            VideoPlayConfig.defaultPlaySpeed = speed
        }
    }
}
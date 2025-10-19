package io.stillpage.app.help.config

import io.stillpage.app.constant.PreferKey
import io.stillpage.app.utils.getPrefBoolean
import io.stillpage.app.utils.getPrefInt
import io.stillpage.app.utils.getPrefString
import io.stillpage.app.utils.putPrefBoolean
import io.stillpage.app.utils.putPrefInt
import io.stillpage.app.utils.putPrefString
import splitties.init.appCtx

/**
 * 视频播放器配置管理
 */
object VideoPlayConfig {

    // ========== 播放控制配置 ==========

    /**
     * 默认播放速度
     */
    var defaultPlaySpeed: Float
        get() = appCtx.getPrefString(PreferKey.videoPlaySpeed, "1.0")?.toFloatOrNull() ?: 1.0f
        set(value) = appCtx.putPrefString(PreferKey.videoPlaySpeed, value.toString())

    /**
     * 自动全屏播放
     */
    var autoFullScreen: Boolean
        get() = appCtx.getPrefBoolean("videoAutoFullScreen", false)
        set(value) = appCtx.putPrefBoolean("videoAutoFullScreen", value)

    /**
     * 画中画模式启用
     */
    var pipModeEnabled: Boolean
        get() = appCtx.getPrefBoolean("videoPipEnabled", true)
        set(value) = appCtx.putPrefBoolean("videoPipEnabled", value)

    /**
     * 用户离开时自动进入画中画
     */
    var autoEnterPip: Boolean
        get() = appCtx.getPrefBoolean("videoAutoEnterPip", true)
        set(value) = appCtx.putPrefBoolean("videoAutoEnterPip", value)

    /**
     * 快进快退时长（秒）
     */
    var seekDurationSeconds: Int
        get() = appCtx.getPrefInt("videoSeekDuration", 10)
        set(value) = appCtx.putPrefInt("videoSeekDuration", value)

    /**
     * 控制栏自动隐藏时间（毫秒）
     */
    var controlsHideDelay: Long
        get() = appCtx.getPrefInt("videoControlsHideDelay", 3000).toLong()
        set(value) = appCtx.putPrefInt("videoControlsHideDelay", value.toInt())

    // ========== 视频质量配置 ==========

    /**
     * 默认视频质量偏好
     */
    var defaultVideoQuality: String
        get() = appCtx.getPrefString("videoDefaultQuality", "auto") ?: "auto"
        set(value) = appCtx.putPrefString("videoDefaultQuality", value)

    /**
     * 自适应码率启用
     */
    var adaptiveBitrateEnabled: Boolean
        get() = appCtx.getPrefBoolean("videoAdaptiveBitrate", true)
        set(value) = appCtx.putPrefBoolean("videoAdaptiveBitrate", value)

    /**
     * 最大视频码率（kbps，0表示无限制）
     */
    var maxVideoBitrate: Int
        get() = appCtx.getPrefInt("videoMaxBitrate", 0)
        set(value) = appCtx.putPrefInt("videoMaxBitrate", value)

    // ========== 字幕配置 ==========

    /**
     * 字幕默认启用
     */
    var subtitleEnabled: Boolean
        get() = appCtx.getPrefBoolean("videoSubtitleEnabled", true)
        set(value) = appCtx.putPrefBoolean("videoSubtitleEnabled", value)

    /**
     * 字幕语言偏好
     */
    var subtitleLanguage: String
        get() = appCtx.getPrefString("videoSubtitleLanguage", "auto") ?: "auto"
        set(value) = appCtx.putPrefString("videoSubtitleLanguage", value)

    /**
     * 字幕文字大小
     */
    var subtitleTextSize: Float
        get() = appCtx.getPrefString("videoSubtitleTextSize", "16.0")?.toFloatOrNull() ?: 16.0f
        set(value) = appCtx.putPrefString("videoSubtitleTextSize", value.toString())

    // ========== 缓存配置 ==========

    /**
     * 视频缓存大小（MB）
     */
    var cacheSize: Int
        get() = appCtx.getPrefInt("videoCacheSize", 500)
        set(value) = appCtx.putPrefInt("videoCacheSize", value)

    /**
     * 自动清理过期缓存
     */
    var autoClearExpiredCache: Boolean
        get() = appCtx.getPrefBoolean("videoAutoClearCache", true)
        set(value) = appCtx.putPrefBoolean("videoAutoClearCache", value)

    /**
     * 缓存过期时间（小时）
     */
    var cacheExpireHours: Int
        get() = appCtx.getPrefInt("videoCacheExpireHours", 24)
        set(value) = appCtx.putPrefInt("videoCacheExpireHours", value)

    /**
     * 预加载下一集
     */
    var preloadNextEpisode: Boolean
        get() = appCtx.getPrefBoolean("videoPreloadNext", false)
        set(value) = appCtx.putPrefBoolean("videoPreloadNext", value)

    // ========== 手势控制配置 ==========

    /**
     * 手势控制启用
     */
    var gestureControlEnabled: Boolean
        get() = appCtx.getPrefBoolean("videoGestureEnabled", true)
        set(value) = appCtx.putPrefBoolean("videoGestureEnabled", value)

    /**
     * 音量手势灵敏度
     */
    var volumeGestureSensitivity: Float
        get() = appCtx.getPrefString("videoVolumeGestureSensitivity", "1.0")?.toFloatOrNull() ?: 1.0f
        set(value) = appCtx.putPrefString("videoVolumeGestureSensitivity", value.toString())

    /**
     * 亮度手势灵敏度
     */
    var brightnessGestureSensitivity: Float
        get() = appCtx.getPrefString("videoBrightnessGestureSensitivity", "1.0")?.toFloatOrNull() ?: 1.0f
        set(value) = appCtx.putPrefString("videoBrightnessGestureSensitivity", value.toString())

    /**
     * 进度手势灵敏度
     */
    var progressGestureSensitivity: Float
        get() = appCtx.getPrefString("videoProgressGestureSensitivity", "1.0")?.toFloatOrNull() ?: 1.0f
        set(value) = appCtx.putPrefString("videoProgressGestureSensitivity", value.toString())

    // ========== 播放模式配置 ==========

    /**
     * 默认循环播放模式
     */
    var defaultLoopMode: String
        get() = appCtx.getPrefString("videoDefaultLoopMode", "LIST_END_STOP") ?: "LIST_END_STOP"
        set(value) = appCtx.putPrefString("videoDefaultLoopMode", value)

    /**
     * 记住播放进度
     */
    var rememberPlayPosition: Boolean
        get() = appCtx.getPrefBoolean("videoRememberPosition", true)
        set(value) = appCtx.putPrefBoolean("videoRememberPosition", value)

    /**
     * 自动播放下一集
     */
    var autoPlayNext: Boolean
        get() = appCtx.getPrefBoolean("videoAutoPlayNext", true)
        set(value) = appCtx.putPrefBoolean("videoAutoPlayNext", value)

    // ========== 网络配置 ==========

    /**
     * 仅WiFi下播放高质量视频
     */
    var highQualityOnWifiOnly: Boolean
        get() = appCtx.getPrefBoolean("videoHighQualityWifiOnly", false)
        set(value) = appCtx.putPrefBoolean("videoHighQualityWifiOnly", value)

    /**
     * 移动网络下的最大码率（kbps）
     */
    var mobileMaxBitrate: Int
        get() = appCtx.getPrefInt("videoMobileMaxBitrate", 1000)
        set(value) = appCtx.putPrefInt("videoMobileMaxBitrate", value)

    /**
     * 网络超时时间（秒）
     */
    var networkTimeoutSeconds: Int
        get() = appCtx.getPrefInt("videoNetworkTimeout", 30)
        set(value) = appCtx.putPrefInt("videoNetworkTimeout", value)

    // ========== 高级配置 ==========

    /**
     * 硬件解码启用
     */
    var hardwareDecodingEnabled: Boolean
        get() = appCtx.getPrefBoolean("videoHardwareDecoding", true)
        set(value) = appCtx.putPrefBoolean("videoHardwareDecoding", value)

    /**
     * 调试模式启用
     */
    var debugModeEnabled: Boolean
        get() = appCtx.getPrefBoolean("videoDebugMode", false)
        set(value) = appCtx.putPrefBoolean("videoDebugMode", value)

    /**
     * 详细日志启用
     */
    var verboseLoggingEnabled: Boolean
        get() = appCtx.getPrefBoolean("videoVerboseLogging", false)
        set(value) = appCtx.putPrefBoolean("videoVerboseLogging", value)

    // ========== 工具方法 ==========

    /**
     * 重置所有配置为默认值
     */
    fun resetToDefaults() {
        defaultPlaySpeed = 1.0f
        autoFullScreen = false
        pipModeEnabled = true
        autoEnterPip = true
        seekDurationSeconds = 10
        controlsHideDelay = 3000L
        
        defaultVideoQuality = "auto"
        adaptiveBitrateEnabled = true
        maxVideoBitrate = 0
        
        subtitleEnabled = true
        subtitleLanguage = "auto"
        subtitleTextSize = 16.0f
        
        cacheSize = 500
        autoClearExpiredCache = true
        cacheExpireHours = 24
        preloadNextEpisode = false
        
        gestureControlEnabled = true
        volumeGestureSensitivity = 1.0f
        brightnessGestureSensitivity = 1.0f
        progressGestureSensitivity = 1.0f
        
        defaultLoopMode = "LIST_END_STOP"
        rememberPlayPosition = true
        autoPlayNext = true
        
        highQualityOnWifiOnly = false
        mobileMaxBitrate = 1000
        networkTimeoutSeconds = 30
        
        hardwareDecodingEnabled = true
        debugModeEnabled = false
        verboseLoggingEnabled = false
    }

    /**
     * 获取配置摘要
     */
    fun getConfigSummary(): String {
        return buildString {
            appendLine("视频播放器配置摘要:")
            appendLine("默认播放速度: ${defaultPlaySpeed}x")
            appendLine("自动全屏: $autoFullScreen")
            appendLine("画中画启用: $pipModeEnabled")
            appendLine("默认视频质量: $defaultVideoQuality")
            appendLine("字幕启用: $subtitleEnabled")
            appendLine("缓存大小: ${cacheSize}MB")
            appendLine("手势控制: $gestureControlEnabled")
            appendLine("循环模式: $defaultLoopMode")
            appendLine("硬件解码: $hardwareDecodingEnabled")
        }
    }

    /**
     * 验证配置有效性
     */
    fun validateConfig(): List<String> {
        val issues = mutableListOf<String>()
        
        if (defaultPlaySpeed < 0.25f || defaultPlaySpeed > 4.0f) {
            issues.add("播放速度超出有效范围 (0.25x - 4.0x)")
        }
        
        if (seekDurationSeconds < 1 || seekDurationSeconds > 60) {
            issues.add("快进快退时长超出有效范围 (1-60秒)")
        }
        
        if (cacheSize < 50 || cacheSize > 2048) {
            issues.add("缓存大小超出有效范围 (50MB - 2GB)")
        }
        
        if (subtitleTextSize < 8.0f || subtitleTextSize > 32.0f) {
            issues.add("字幕文字大小超出有效范围 (8-32)")
        }
        
        return issues
    }
}
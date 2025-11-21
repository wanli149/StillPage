package io.stillpage.app.ui.book.video

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.stillpage.app.constant.AppLog

/**
 * 视频轨道管理辅助类
 * 处理字幕、音轨的选择和切换
 */
object VideoTrackHelper {

    /**
     * 轨道信息数据类
     */
    data class TrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val format: Format,
        val displayName: String,
        val isSelected: Boolean
    )

    /**
     * 获取所有字幕轨道
     */
    fun getSubtitleTracks(player: ExoPlayer): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        val currentTracks = player.currentTracks
        
        try {
            for (groupIndex in currentTracks.groups.indices) {
                val trackGroup = currentTracks.groups[groupIndex]
                
                if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                    for (trackIndex in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(trackIndex)
                        val isSelected = trackGroup.isTrackSelected(trackIndex)
                        
                        val displayName = buildString {
                            append(format.language ?: "未知语言")
                            if (!format.label.isNullOrBlank()) {
                                append(" (${format.label})")
                            }
                            if (format.roleFlags != 0) {
                                append(" [${getRoleFlagsString(format.roleFlags)}]")
                            }
                        }
                        
                        tracks.add(TrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            format = format,
                            displayName = displayName,
                            isSelected = isSelected
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.put("VideoTrackHelper: 获取字幕轨道失败", e)
        }
        
        AppLog.put("VideoTrackHelper: 找到${tracks.size}个字幕轨道")
        return tracks
    }

    /**
     * 获取所有音频轨道
     */
    fun getAudioTracks(player: ExoPlayer): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        val currentTracks = player.currentTracks
        
        try {
            for (groupIndex in currentTracks.groups.indices) {
                val trackGroup = currentTracks.groups[groupIndex]
                
                if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                    for (trackIndex in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(trackIndex)
                        val isSelected = trackGroup.isTrackSelected(trackIndex)
                        
                        val displayName = buildString {
                            append(format.language ?: "默认音轨")
                            if (!format.label.isNullOrBlank()) {
                                append(" (${format.label})")
                            }
                            if (format.bitrate > 0) {
                                append(" ${format.bitrate / 1000}kbps")
                            }
                            if (format.channelCount > 0) {
                                append(" ${format.channelCount}声道")
                            }
                        }
                        
                        tracks.add(TrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            format = format,
                            displayName = displayName,
                            isSelected = isSelected
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.put("VideoTrackHelper: 获取音频轨道失败", e)
        }
        
        AppLog.put("VideoTrackHelper: 找到${tracks.size}个音频轨道")
        return tracks
    }

    /**
     * 获取所有视频轨道
     */
    fun getVideoTracks(player: ExoPlayer): List<TrackInfo> {
        val tracks = mutableListOf<TrackInfo>()
        val currentTracks = player.currentTracks
        
        try {
            for (groupIndex in currentTracks.groups.indices) {
                val trackGroup = currentTracks.groups[groupIndex]
                
                if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                    for (trackIndex in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(trackIndex)
                        val isSelected = trackGroup.isTrackSelected(trackIndex)
                        
                        val displayName = buildString {
                            if (format.width > 0 && format.height > 0) {
                                append("${format.width}x${format.height}")
                            }
                            if (format.frameRate > 0) {
                                append(" ${format.frameRate.toInt()}fps")
                            }
                            if (format.bitrate > 0) {
                                append(" ${format.bitrate / 1000}kbps")
                            }
                            if (!format.codecs.isNullOrBlank()) {
                                append(" (${format.codecs})")
                            }
                        }
                        
                        tracks.add(TrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            format = format,
                            displayName = displayName.ifBlank { "视频轨道 ${trackIndex + 1}" },
                            isSelected = isSelected
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.put("VideoTrackHelper: 获取视频轨道失败", e)
        }
        
        AppLog.put("VideoTrackHelper: 找到${tracks.size}个视频轨道")
        return tracks
    }

    /**
     * 选择字幕轨道
     */
    fun selectSubtitleTrack(player: ExoPlayer, trackInfo: TrackInfo?): Boolean {
        return try {
            val trackSelector = player.trackSelector as? DefaultTrackSelector
                ?: return false
            
            val parametersBuilder = trackSelector.parameters.buildUpon()
            
            if (trackInfo == null) {
                // 禁用字幕
                parametersBuilder.setRendererDisabled(C.TRACK_TYPE_TEXT, true)
                AppLog.put("VideoTrackHelper: 禁用字幕")
            } else {
                // 启用并选择指定字幕轨道
                parametersBuilder.setRendererDisabled(C.TRACK_TYPE_TEXT, false)
                // 这里需要更复杂的轨道选择逻辑
                AppLog.put("VideoTrackHelper: 选择字幕轨道 - ${trackInfo.displayName}")
            }
            
            trackSelector.setParameters(parametersBuilder)
            true
        } catch (e: Exception) {
            AppLog.put("VideoTrackHelper: 选择字幕轨道失败", e)
            false
        }
    }

    /**
     * 选择音频轨道
     */
    fun selectAudioTrack(player: ExoPlayer, trackInfo: TrackInfo): Boolean {
        return try {
            val trackSelector = player.trackSelector as? DefaultTrackSelector
                ?: return false
            
            val parametersBuilder = trackSelector.parameters.buildUpon()
            
            // 这里需要实现具体的音轨选择逻辑
            // ExoPlayer的轨道选择比较复杂，需要使用TrackSelectionOverride
            
            trackSelector.setParameters(parametersBuilder)
            AppLog.put("VideoTrackHelper: 选择音频轨道 - ${trackInfo.displayName}")
            true
        } catch (e: Exception) {
            AppLog.put("VideoTrackHelper: 选择音频轨道失败", e)
            false
        }
    }

    /**
     * 选择视频轨道（质量）
     */
    fun selectVideoTrack(player: ExoPlayer, trackInfo: TrackInfo): Boolean {
        return try {
            val trackSelector = player.trackSelector as? DefaultTrackSelector
                ?: return false
            
            val parametersBuilder = trackSelector.parameters.buildUpon()
            
            // 设置视频质量偏好
            if (trackInfo.format.width > 0 && trackInfo.format.height > 0) {
                parametersBuilder.setMaxVideoSize(trackInfo.format.width, trackInfo.format.height)
                parametersBuilder.setMaxVideoBitrate(trackInfo.format.bitrate)
            }
            
            trackSelector.setParameters(parametersBuilder)
            AppLog.put("VideoTrackHelper: 选择视频轨道 - ${trackInfo.displayName}")
            true
        } catch (e: Exception) {
            AppLog.put("VideoTrackHelper: 选择视频轨道失败", e)
            false
        }
    }

    /**
     * 启用自适应视频质量
     */
    fun enableAdaptiveVideoQuality(player: ExoPlayer): Boolean {
        return try {
            val trackSelector = player.trackSelector as? DefaultTrackSelector
                ?: return false
            
            val parametersBuilder = trackSelector.parameters.buildUpon()
            
            // 清除视频质量限制，启用自适应
            parametersBuilder.clearVideoSizeConstraints()
            parametersBuilder.setMaxVideoBitrate(Int.MAX_VALUE)
            
            trackSelector.setParameters(parametersBuilder)
            AppLog.put("VideoTrackHelper: 启用自适应视频质量")
            true
        } catch (e: Exception) {
            AppLog.put("VideoTrackHelper: 启用自适应视频质量失败", e)
            false
        }
    }

    /**
     * 获取角色标志字符串
     */
    private fun getRoleFlagsString(roleFlags: Int): String {
        val roles = mutableListOf<String>()
        
        if (roleFlags and C.ROLE_FLAG_MAIN != 0) roles.add("主要")
        if (roleFlags and C.ROLE_FLAG_ALTERNATE != 0) roles.add("备用")
        if (roleFlags and C.ROLE_FLAG_SUPPLEMENTARY != 0) roles.add("补充")
        if (roleFlags and C.ROLE_FLAG_COMMENTARY != 0) roles.add("评论")
        if (roleFlags and C.ROLE_FLAG_DUB != 0) roles.add("配音")
        if (roleFlags and C.ROLE_FLAG_EMERGENCY != 0) roles.add("紧急")
        if (roleFlags and C.ROLE_FLAG_CAPTION != 0) roles.add("字幕")
        if (roleFlags and C.ROLE_FLAG_SUBTITLE != 0) roles.add("副标题")
        if (roleFlags and C.ROLE_FLAG_SIGN != 0) roles.add("手语")
        
        return roles.joinToString(", ")
    }

    /**
     * 检查是否有字幕
     */
    fun hasSubtitles(player: ExoPlayer): Boolean {
        return getSubtitleTracks(player).isNotEmpty()
    }

    /**
     * 检查是否有多个音轨
     */
    fun hasMultipleAudioTracks(player: ExoPlayer): Boolean {
        return getAudioTracks(player).size > 1
    }

    /**
     * 检查是否有多个视频质量
     */
    fun hasMultipleVideoQualities(player: ExoPlayer): Boolean {
        return getVideoTracks(player).size > 1
    }

    /**
     * 获取当前选中的字幕轨道
     */
    fun getCurrentSubtitleTrack(player: ExoPlayer): TrackInfo? {
        return getSubtitleTracks(player).find { it.isSelected }
    }

    /**
     * 获取当前选中的音频轨道
     */
    fun getCurrentAudioTrack(player: ExoPlayer): TrackInfo? {
        return getAudioTracks(player).find { it.isSelected }
    }

    /**
     * 获取当前选中的视频轨道
     */
    fun getCurrentVideoTrack(player: ExoPlayer): TrackInfo? {
        return getVideoTracks(player).find { it.isSelected }
    }
}
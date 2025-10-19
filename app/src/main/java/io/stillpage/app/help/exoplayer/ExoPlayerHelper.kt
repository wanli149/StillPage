package io.stillpage.app.help.exoplayer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.gson.reflect.TypeToken
import io.stillpage.app.constant.AppLog
import io.stillpage.app.help.http.okHttpClient
import io.stillpage.app.utils.GSON
import io.stillpage.app.utils.externalCache
import okhttp3.CacheControl
import splitties.init.appCtx
import java.io.File
import java.util.concurrent.TimeUnit


@Suppress("unused")
@SuppressLint("UnsafeOptInUsageError")
object ExoPlayerHelper {

    private const val SPLIT_TAG = "\uD83D\uDEA7"

    private val mapType by lazy {
        object : TypeToken<Map<String, String>>() {}.type
    }

    fun createMediaItem(url: String, headers: Map<String, String>): MediaItem {
        val formatUrl = url + SPLIT_TAG + GSON.toJson(headers, mapType)
        val lower = url.lowercase()
        val builder = MediaItem.Builder().setUri(formatUrl)
        when {
            lower.contains(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            lower.contains(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            // 其他格式维持默认Progressive，避免过度限定
        }
        return builder.build()
    }

    /**
     * 创建视频专用的MediaItem
     * 支持更多视频格式和MIME类型检测
     */
    fun createVideoMediaItem(url: String, headers: Map<String, String>): MediaItem {
        AppLog.put("ExoPlayerHelper: 创建视频MediaItem - 原始URL: $url")
        AppLog.put("ExoPlayerHelper: Headers: $headers")
        
        val formatUrl = url + SPLIT_TAG + GSON.toJson(headers, mapType)
        val lower = url.lowercase()
        val builder = MediaItem.Builder().setUri(formatUrl)
        
        AppLog.put("ExoPlayerHelper: 格式化后URL: $formatUrl")
        
        // 完整的视频MIME类型支持
        when {
            lower.contains(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            lower.contains(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            lower.contains(".mp4") -> builder.setMimeType(MimeTypes.VIDEO_MP4)
            lower.contains(".webm") -> builder.setMimeType(MimeTypes.VIDEO_WEBM)
            lower.contains(".mkv") -> builder.setMimeType(MimeTypes.VIDEO_UNKNOWN)
            lower.contains(".avi") -> builder.setMimeType(MimeTypes.VIDEO_UNKNOWN)
            lower.contains(".flv") -> builder.setMimeType(MimeTypes.VIDEO_FLV)
            lower.contains(".ts") -> builder.setMimeType(MimeTypes.VIDEO_UNKNOWN)
            lower.contains(".mov") -> builder.setMimeType(MimeTypes.VIDEO_UNKNOWN)
            lower.contains(".wmv") -> builder.setMimeType(MimeTypes.VIDEO_UNKNOWN)
            lower.contains(".3gp") -> builder.setMimeType(MimeTypes.VIDEO_UNKNOWN)
            // 默认让ExoPlayer自动检测格式
            else -> { /* 让ExoPlayer自动检测格式 */ }
        }
        return builder.build()
    }

    fun createHttpExoPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context).setLoadControl(
            DefaultLoadControl.Builder().setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 10,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 10
            ).build()

        ).setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(resolvingDataSource)
                .setLiveTargetOffsetMs(5000)
        ).build()
    }

    /**
     * 创建视频专用的ExoPlayer实例
     * 针对视频播放优化缓冲策略和配置
     */
    fun createVideoExoPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        50000,  // 最小缓冲时间（视频需要更多缓冲）
                        300000, // 最大缓冲时间
                        2500,   // 播放缓冲时间
                        5000    // 重新缓冲时间
                    ).build()
            )
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(resolvingDataSource)
                    .setLiveTargetOffsetMs(5000)
            )
            .build()
    }




    private val resolvingDataSource: ResolvingDataSource.Factory by lazy {
        ResolvingDataSource.Factory(cacheDataSourceFactory) {
            var res = it

            if (it.uri.toString().contains(SPLIT_TAG)) {
                val urls = it.uri.toString().split(SPLIT_TAG)
                val url = urls[0]
                AppLog.put("ExoPlayerHelper: 解析URL - 原始: ${it.uri}, 解析后: $url")
                res = res.withUri(Uri.parse(url))
                try {
                    val headers: Map<String, String> = GSON.fromJson(urls[1], mapType)
                    AppLog.put("ExoPlayerHelper: 应用Headers: $headers")
                    okhttpDataFactory.setDefaultRequestProperties(headers)
                } catch (e: Exception) {
                    AppLog.put("ExoPlayerHelper: Headers解析失败", e)
                }
            } else {
                AppLog.put("ExoPlayerHelper: 直接使用URL: ${it.uri}")
            }

            res

        }
    }


    /**
     * 支持缓存的DataSource.Factory
     */
    private val cacheDataSourceFactory by lazy {
        //使用自定义的CacheDataSource以支持设置UA
        return@lazy CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(okhttpDataFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            )
    }

    /**
     * Okhttp DataSource.Factory
     */
    private val okhttpDataFactory by lazy {
        val client = okHttpClient.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
        OkHttpDataSource.Factory(client)
            .setCacheControl(CacheControl.Builder().maxAge(1, TimeUnit.DAYS).build())
    }

    /**
     * Exoplayer 内置的缓存
     * 针对视频内容扩大缓存空间
     */
    private val cache: Cache by lazy {
        val databaseProvider = StandaloneDatabaseProvider(appCtx)
        return@lazy SimpleCache(
            //Exoplayer的缓存路径
            File(appCtx.externalCache, "exoplayer"),
            //扩大到500M的缓存以支持视频内容
            LeastRecentlyUsedCacheEvictor((500 * 1024 * 1024).toLong()),
            //记录缓存的数据库
            databaseProvider
        )
    }

    /**
     * 判断URL是否为视频直链
     */
    fun isDirectVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        // 排除明显非媒体协议/资源
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") ||
            lower.startsWith("tel:") || lower.startsWith("sms:")) return false
        
        val excludeExt = listOf(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico",
            ".html", ".htm", ".php", ".asp", ".jsp"
        )
        if (excludeExt.any { lower.contains(it) }) return false
        
        // 更宽泛的识别逻辑：覆盖更多直链与提示关键词
        val videoHints = listOf(
            ".mp4", ".m3u8", ".mpd", ".webm", ".ts", ".mkv", ".avi", ".flv", ".mov", ".wmv", ".3gp",
            "/dash/", "application/dash+xml",
            "application/vnd.apple.mpegurl",
            "mime_type=video", "mime_type=video_mp4",
            "video/mp4", "video/mpeg", "video/webm",
            "play", "stream"
        )
        return videoHints.any { lower.contains(it) }
    }

    /**
     * 获取缓存大小（字节）
     */
    fun getCacheSize(): Long {
        return try {
            cache.cacheSpace
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 获取已使用的缓存大小（字节）
     */
    fun getUsedCacheSize(): Long {
        return try {
            // 计算缓存目录的实际大小
            val cacheDir = File(appCtx.externalCache, "exoplayer")
            if (cacheDir.exists()) {
                calculateDirectorySize(cacheDir)
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 计算目录大小
     */
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        try {
            if (directory.exists()) {
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        size += if (file.isDirectory) {
                            calculateDirectorySize(file)
                        } else {
                            file.length()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略异常
        }
        return size
    }

    /**
     * 清理缓存
     */
    fun clearCache() {
        try {
            val keys = cache.keys
            var clearedCount = 0
            for (key in keys) {
                cache.removeResource(key)
                clearedCount++
            }
            
            // 同时清理缓存目录中的文件
            val cacheDir = File(appCtx.externalCache, "exoplayer")
            if (cacheDir.exists()) {
                clearDirectory(cacheDir)
            }
            
            AppLog.put("ExoPlayerHelper: 清理缓存完成，清理了${clearedCount}个缓存项")
        } catch (e: Exception) {
            AppLog.put("ExoPlayerHelper: 清理缓存失败", e)
        }
    }

    /**
     * 清理指定目录
     */
    private fun clearDirectory(directory: File) {
        try {
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isDirectory) {
                            clearDirectory(file)
                            file.delete()
                        } else {
                            file.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略异常
        }
    }

    /**
     * 清理过期缓存
     */
    fun clearExpiredCache(maxAgeHours: Int = 24) {
        try {
            val cacheDir = File(appCtx.externalCache, "exoplayer")
            if (!cacheDir.exists()) return
            
            val currentTime = System.currentTimeMillis()
            val maxAge = maxAgeHours * 60 * 60 * 1000L // 转换为毫秒
            
            var clearedCount = 0
            val files = cacheDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile && (currentTime - file.lastModified()) > maxAge) {
                        if (file.delete()) {
                            clearedCount++
                        }
                    }
                }
            }
            
            AppLog.put("ExoPlayerHelper: 清理过期缓存完成，清理了${clearedCount}个文件")
        } catch (e: Exception) {
            AppLog.put("ExoPlayerHelper: 清理过期缓存失败", e)
        }
    }

    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        return try {
            val totalSize = getCacheSize()
            val usedSize = getUsedCacheSize()
            val cacheDir = File(appCtx.externalCache, "exoplayer")
            val fileCount = if (cacheDir.exists()) {
                countFilesInDirectory(cacheDir)
            } else {
                0
            }
            
            CacheStats(
                totalSizeBytes = totalSize,
                usedSizeBytes = usedSize,
                fileCount = fileCount,
                hitRate = 0.0 // ExoPlayer没有直接提供命中率统计
            )
        } catch (e: Exception) {
            CacheStats(0, 0, 0, 0.0)
        }
    }

    /**
     * 计算目录中的文件数量
     */
    private fun countFilesInDirectory(directory: File): Int {
        var count = 0
        try {
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile) {
                            count++
                        } else if (file.isDirectory) {
                            count += countFilesInDirectory(file)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略异常
        }
        return count
    }

    /**
     * 缓存统计信息数据类
     */
    data class CacheStats(
        val totalSizeBytes: Long,
        val usedSizeBytes: Long,
        val fileCount: Int,
        val hitRate: Double
    ) {
        val totalSizeMB: Double get() = totalSizeBytes / (1024.0 * 1024.0)
        val usedSizeMB: Double get() = usedSizeBytes / (1024.0 * 1024.0)
        val freeSpaceBytes: Long get() = totalSizeBytes - usedSizeBytes
        val freeSpaceMB: Double get() = freeSpaceBytes / (1024.0 * 1024.0)
        val usagePercentage: Double get() = if (totalSizeBytes > 0) (usedSizeBytes.toDouble() / totalSizeBytes) * 100 else 0.0
    }

    /**
     * 通过kotlin扩展函数+反射实现CacheDataSource.Factory设置默认请求头
     * 需要添加混淆规则 -keepclassmembers class com.google.android.exoplayer2.upstream.cache.CacheDataSource$Factory{upstreamDataSourceFactory;}
     * @param headers
     * @return
     */
//    private fun CacheDataSource.Factory.setDefaultRequestProperties(headers: Map<String, String> = mapOf()): CacheDataSource.Factory {
//        val declaredField = this.javaClass.getDeclaredField("upstreamDataSourceFactory")
//        declaredField.isAccessible = true
//        val df = declaredField[this] as DataSource.Factory
//        if (df is OkHttpDataSource.Factory) {
//            df.setDefaultRequestProperties(headers)
//        }
//        return this
//    }

}
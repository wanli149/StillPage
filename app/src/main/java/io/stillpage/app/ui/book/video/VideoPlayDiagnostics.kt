package io.stillpage.app.ui.book.video

import android.content.Context
import io.stillpage.app.constant.AppLog
import io.stillpage.app.help.http.okHttpClient
import io.stillpage.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URL

/**
 * 视频播放诊断工具
 * 用于分析和调试视频播放问题
 */
object VideoPlayDiagnostics {

    /**
     * 诊断视频URL
     */
    suspend fun diagnoseVideoUrl(url: String): DiagnosticResult {
        return withContext(Dispatchers.IO) {
            try {
                AppLog.put("VideoPlayDiagnostics: 开始诊断URL - $url")
                
                val result = DiagnosticResult(
                    originalUrl = url,
                    timestamp = System.currentTimeMillis()
                )
                
                // 1. URL格式检查
                result.urlFormatValid = isValidUrl(url)
                AppLog.put("VideoPlayDiagnostics: URL格式检查 - ${result.urlFormatValid}")
                
                // 2. 网络连接测试
                val networkResult = testNetworkConnection(url)
                result.networkReachable = networkResult.first
                result.responseCode = networkResult.second
                result.responseHeaders = networkResult.third
                AppLog.put("VideoPlayDiagnostics: 网络连接测试 - 可达:${result.networkReachable}, 状态码:${result.responseCode}")
                
                // 3. 内容类型检查
                if (result.networkReachable) {
                    val contentResult = analyzeContent(url)
                    result.contentType = contentResult.first
                    result.contentLength = contentResult.second
                    result.actualContent = contentResult.third
                    AppLog.put("VideoPlayDiagnostics: 内容分析 - 类型:${result.contentType}, 长度:${result.contentLength}")
                    
                    // 4. 尝试解析JSON响应获取真实视频URL
                    result.parsedVideoUrls = parseVideoUrlsFromContent(result.actualContent)
                    if (result.parsedVideoUrls.isNotEmpty()) {
                        AppLog.put("VideoPlayDiagnostics: 从响应中解析到${result.parsedVideoUrls.size}个可能的视频URL")
                    }
                }
                
                // 5. 视频格式检查
                result.isDirectVideo = isDirectVideoFormat(url, result.contentType)
                AppLog.put("VideoPlayDiagnostics: 视频格式检查 - ${result.isDirectVideo}")
                
                // 6. 生成建议
                result.suggestions = generateSuggestions(result)
                
                AppLog.put("VideoPlayDiagnostics: 诊断完成")
                result
                
            } catch (e: Exception) {
                AppLog.put("VideoPlayDiagnostics: 诊断失败", e)
                DiagnosticResult(
                    originalUrl = url,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            }
        }
    }

    /**
     * 检查URL格式是否有效
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            URL(url)
            url.startsWith("http://") || url.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 测试网络连接
     */
    private suspend fun testNetworkConnection(url: String): Triple<Boolean, Int?, Map<String, String>> {
        return try {
            val request = Request.Builder()
                .url(url)
                .head() // 使用HEAD请求减少数据传输
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val headers = response.headers.toMultimap().mapValues { it.value.joinToString(", ") }
            
            Triple(response.isSuccessful, response.code, headers)
        } catch (e: Exception) {
            AppLog.put("VideoPlayDiagnostics: 网络连接测试失败", e)
            Triple(false, null, emptyMap())
        }
    }

    /**
     * 分析内容
     */
    private suspend fun analyzeContent(url: String): Triple<String?, Long?, String?> {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val contentType = response.header("Content-Type")
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            val body = response.body?.string()
            
            Triple(contentType, contentLength, body)
        } catch (e: Exception) {
            AppLog.put("VideoPlayDiagnostics: 内容分析失败", e)
            Triple(null, null, null)
        }
    }

    /**
     * 从响应内容中解析可能的视频URL
     */
    private fun parseVideoUrlsFromContent(content: String?): List<String> {
        if (content.isNullOrBlank()) return emptyList()
        
        val videoUrls = mutableListOf<String>()
        
        try {
            // 尝试解析JSON
            if (content.trim().startsWith("{")) {
                val jsonMap = GSON.fromJson(content, Map::class.java) as? Map<String, Any>
                jsonMap?.let { map ->
                    extractVideoUrlsFromMap(map, videoUrls)
                }
            } else if (content.trim().startsWith("[")) {
                val jsonArray = GSON.fromJson(content, List::class.java) as? List<Any>
                jsonArray?.forEach { item ->
                    if (item is Map<*, *>) {
                        extractVideoUrlsFromMap(item as Map<String, Any>, videoUrls)
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.put("VideoPlayDiagnostics: JSON解析失败", e)
        }
        
        // 使用正则表达式提取URL
        val urlPattern = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8|mpd|webm|ts|mkv|avi|flv|mov|wmv|3gp)(?:\?[^\s"'<>]*)?""")
        val matches = urlPattern.findAll(content)
        matches.forEach { match ->
            val url = match.value
            if (!videoUrls.contains(url)) {
                videoUrls.add(url)
            }
        }
        
        // 提取任何看起来像视频URL的链接
        val generalUrlPattern = Regex("""https?://[^\s"'<>]+""")
        val generalMatches = generalUrlPattern.findAll(content)
        generalMatches.forEach { match ->
            val url = match.value
            if (isLikelyVideoUrl(url) && !videoUrls.contains(url)) {
                videoUrls.add(url)
            }
        }
        
        return videoUrls
    }

    /**
     * 从Map中提取视频URL
     */
    private fun extractVideoUrlsFromMap(map: Map<String, Any>, videoUrls: MutableList<String>) {
        val videoFields = listOf(
            "url", "video_url", "play_url", "stream_url", "src", "source", 
            "file", "path", "link", "href", "video", "stream", "media",
            "playUrl", "videoUrl", "streamUrl", "mediaUrl", "fileUrl"
        )
        
        map.forEach { (key, value) ->
            when {
                videoFields.any { it.equals(key, ignoreCase = true) } && value is String -> {
                    if (isLikelyVideoUrl(value)) {
                        videoUrls.add(value)
                    }
                }
                value is Map<*, *> -> {
                    extractVideoUrlsFromMap(value as Map<String, Any>, videoUrls)
                }
                value is List<*> -> {
                    value.forEach { item ->
                        if (item is Map<*, *>) {
                            extractVideoUrlsFromMap(item as Map<String, Any>, videoUrls)
                        } else if (item is String && isLikelyVideoUrl(item)) {
                            videoUrls.add(item)
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断字符串是否可能是视频URL
     */
    private fun isLikelyVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.startsWith("http://") || lower.startsWith("https://")) &&
                (lower.contains("video") || lower.contains("play") || lower.contains("stream") ||
                 lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mpd") ||
                 lower.contains(".webm") || lower.contains(".ts") || lower.contains(".mkv") ||
                 lower.contains(".avi") || lower.contains(".flv"))
    }

    /**
     * 检查是否为直接视频格式
     */
    private fun isDirectVideoFormat(url: String, contentType: String?): Boolean {
        val urlLower = url.lowercase()
        val videoExtensions = listOf(".mp4", ".m3u8", ".mpd", ".webm", ".ts", ".mkv", ".avi", ".flv")
        val videoMimeTypes = listOf("video/", "application/vnd.apple.mpegurl", "application/dash+xml")
        
        return videoExtensions.any { urlLower.contains(it) } ||
                (contentType != null && videoMimeTypes.any { contentType.lowercase().contains(it) })
    }

    /**
     * 生成建议
     */
    private fun generateSuggestions(result: DiagnosticResult): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (!result.urlFormatValid) {
            suggestions.add("URL格式无效，请检查URL是否正确")
        }
        
        if (!result.networkReachable) {
            suggestions.add("无法连接到服务器，请检查网络连接")
        }
        
        if (result.responseCode != null && result.responseCode != 200) {
            suggestions.add("服务器返回错误状态码: ${result.responseCode}")
        }
        
        val contentLength = result.contentLength
        if (contentLength != null && contentLength < 1000) {
            suggestions.add("响应内容过短(${contentLength}字节)，可能不是视频文件")
            
            // 检查是否为JSON响应
            val actualContent = result.actualContent
            if (actualContent != null) {
                if (actualContent.trim().startsWith("{") || actualContent.trim().startsWith("[")) {
                    suggestions.add("响应内容是JSON格式，需要解析获取真实视频URL")
                    
                    if (result.parsedVideoUrls.isNotEmpty()) {
                        suggestions.add("从JSON中解析到${result.parsedVideoUrls.size}个可能的视频URL:")
                        result.parsedVideoUrls.forEach { url ->
                            suggestions.add("  • $url")
                        }
                        suggestions.add("建议使用解析到的URL进行播放")
                    } else {
                        suggestions.add("未能从JSON中解析到视频URL，请检查API响应格式")
                    }
                } else {
                    suggestions.add("响应内容格式未知，请手动检查内容")
                }
            }
        }
        
        if (!result.isDirectVideo && result.parsedVideoUrls.isEmpty()) {
            suggestions.add("URL不是直接的视频链接，且未找到可解析的视频URL")
        }
        
        val contentType = result.contentType
        if (contentType != null && !contentType.startsWith("video/") && 
            !contentType.contains("m3u8") && !contentType.contains("mpd") &&
            !contentType.contains("json")) {
            suggestions.add("内容类型(${contentType})既不是视频格式也不是JSON")
        }
        
        // 如果找到了解析的视频URL，建议修改解析逻辑
        if (result.parsedVideoUrls.isNotEmpty()) {
            suggestions.add("建议修改VideoPlayViewModel中的解析逻辑，使用解析到的视频URL")
        }
        
        return suggestions
    }

    /**
     * 诊断结果数据类
     */
    data class DiagnosticResult(
        val originalUrl: String,
        val timestamp: Long,
        var urlFormatValid: Boolean = false,
        var networkReachable: Boolean = false,
        var responseCode: Int? = null,
        var responseHeaders: Map<String, String> = emptyMap(),
        var contentType: String? = null,
        var contentLength: Long? = null,
        var actualContent: String? = null,
        var isDirectVideo: Boolean = false,
        var parsedVideoUrls: List<String> = emptyList(),
        var suggestions: List<String> = emptyList(),
        var error: String? = null
    ) {
        fun toDetailedString(): String {
            val sb = StringBuilder()
            sb.appendLine("=== 视频URL诊断报告 ===")
            sb.appendLine("URL: $originalUrl")
            sb.appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(timestamp))}")
            sb.appendLine()
            
            if (error != null) {
                sb.appendLine("错误: $error")
                return sb.toString()
            }
            
            sb.appendLine("URL格式有效: $urlFormatValid")
            sb.appendLine("网络可达: $networkReachable")
            sb.appendLine("响应状态码: ${responseCode ?: "未知"}")
            sb.appendLine("内容类型: ${contentType ?: "未知"}")
            sb.appendLine("内容长度: ${contentLength ?: "未知"} 字节")
            sb.appendLine("是否直接视频: $isDirectVideo")
            
            if (parsedVideoUrls.isNotEmpty()) {
                sb.appendLine("解析到的视频URL数量: ${parsedVideoUrls.size}")
            }
            sb.appendLine()
            
            if (responseHeaders.isNotEmpty()) {
                sb.appendLine("响应头:")
                responseHeaders.forEach { (key, value) ->
                    sb.appendLine("  $key: $value")
                }
                sb.appendLine()
            }
            
            if (parsedVideoUrls.isNotEmpty()) {
                sb.appendLine("解析到的视频URL:")
                parsedVideoUrls.forEach { url ->
                    sb.appendLine("  • $url")
                }
                sb.appendLine()
            }
            
            val actualContent = this.actualContent
            if (!actualContent.isNullOrBlank()) {
                sb.appendLine("实际内容 (前500字符):")
                sb.appendLine(actualContent.take(500))
                if (actualContent.length > 500) {
                    sb.appendLine("... (内容被截断)")
                }
                sb.appendLine()
            }
            
            if (suggestions.isNotEmpty()) {
                sb.appendLine("建议:")
                suggestions.forEach { suggestion ->
                    sb.appendLine("• $suggestion")
                }
            }
            
            return sb.toString()
        }
    }
}
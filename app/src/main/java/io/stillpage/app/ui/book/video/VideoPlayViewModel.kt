package io.stillpage.app.ui.book.video

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.stillpage.app.base.BaseViewModel
import io.stillpage.app.constant.AppLog
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.help.exoplayer.ExoPlayerHelper
import io.stillpage.app.model.VideoPlay
import io.stillpage.app.model.analyzeRule.AnalyzeUrl
import io.stillpage.app.model.webBook.WebBook
import io.stillpage.app.utils.GSON
import io.stillpage.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * 视频播放ViewModel
 * 处理视频播放相关的业务逻辑和状态管理
 */
class VideoPlayViewModel(application: Application) : BaseViewModel(application) {

    val bookData = MutableLiveData<Book>()
    val chapterData = MutableLiveData<BookChapter>()
    val chapterListData = MutableLiveData<List<BookChapter>>()
    val playStateData = MutableLiveData<Int>()
    val playSpeedData = MutableLiveData<Float>()
    val playProgressData = MutableLiveData<Int>()

    private var bookSource: BookSource? = null

    /**
     * 初始化数据
     */
    fun initData(bookUrl: String, chapterIndex: Int, source: BookSource?) {
        viewModelScope.launch {
            try {
                // 加载书籍信息
                val book = withContext(Dispatchers.IO) {
                    appDb.bookDao.getBook(bookUrl)
                }
                
                if (book != null) {
                    bookData.value = book
                    bookSource = source ?: withContext(Dispatchers.IO) {
                        appDb.bookSourceDao.getBookSource(book.origin)
                    }
                    
                    // 加载章节列表
                    loadChapterList(book)
                    
                    // 加载当前章节
                    loadChapter(book, chapterIndex)
                    
                } else {
                    AppLog.put("VideoPlayViewModel: 书籍不存在 - $bookUrl")
                    context.toastOnUi("书籍不存在")
                }
                
            } catch (e: Exception) {
                AppLog.put("VideoPlayViewModel: 初始化数据失败", e)
                context.toastOnUi("初始化失败: ${e.message}")
            }
        }
    }

    /**
     * 加载章节列表
     */
    private suspend fun loadChapterList(book: Book) {
        try {
            val chapters = withContext(Dispatchers.IO) {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }
            
            chapterListData.value = chapters
            VideoPlay.chapterSize = chapters.size
            
            AppLog.put("VideoPlayViewModel: 加载章节列表完成，共${chapters.size}章")
            
        } catch (e: Exception) {
            AppLog.put("VideoPlayViewModel: 加载章节列表失败", e)
        }
    }

    /**
     * 加载指定章节
     */
    private suspend fun loadChapter(book: Book, chapterIndex: Int) {
        try {
            val chapter = withContext(Dispatchers.IO) {
                appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
            }
            
            if (chapter != null) {
                chapterData.value = chapter
                VideoPlay.durChapter = chapter
                VideoPlay.durChapterIndex = chapterIndex
                
                AppLog.put("VideoPlayViewModel: 加载章节完成 - ${chapter.title}")
                
            } else {
                AppLog.put("VideoPlayViewModel: 章节不存在 - index=$chapterIndex")
                context.toastOnUi("章节不存在")
            }
            
        } catch (e: Exception) {
            AppLog.put("VideoPlayViewModel: 加载章节失败", e)
            context.toastOnUi("加载章节失败: ${e.message}")
        }
    }

    /**
     * 播放视频
     */
    fun playVideo(chapterUrl: String, bookSource: BookSource?) {
        viewModelScope.launch {
            try {
                AppLog.put("VideoPlayViewModel: 开始解析视频URL - $chapterUrl")
                
                // 处理视频URL
                val processedUrl = processVideoUrl(chapterUrl, bookSource)
                
                if (processedUrl.isNotBlank()) {
                    // 通知VideoPlay开始播放
                    VideoPlay.onVideoUrlParsed(processedUrl, emptyMap())
                } else {
                    VideoPlay.onVideoError("无法获取有效的视频播放链接")
                }
                
            } catch (e: Exception) {
                AppLog.put("VideoPlayViewModel: 播放视频失败", e)
                VideoPlay.onVideoError("播放失败: ${e.message}")
            }
        }
    }

    /**
     * 处理视频URL - 增强版多重解析策略
     */
    private suspend fun processVideoUrl(url: String, bookSource: BookSource?): String {
        return withContext(Dispatchers.IO) {
            try {
                var videoUrl = url
                AppLog.put("VideoPlayViewModel: 开始处理视频URL - $url")
                
                // 策略1: 检查是否已经是直链
                if (ExoPlayerHelper.isDirectVideoUrl(videoUrl)) {
                    AppLog.put("VideoPlayViewModel: URL已是直链，直接使用")
                    return@withContext videoUrl
                }
                
                AppLog.put("VideoPlayViewModel: URL不是直链，开始多重解析")
                
                // 策略2: 从章节内容中提取视频链接
                val chapter = chapterData.value
                if (chapter != null && bookSource != null) {
                    AppLog.put("VideoPlayViewModel: 策略2 - 从章节内容提取")
                    val content = getChapterContent(chapter, bookSource)
                    val extractedUrl = extractVideoUrl(content)
                    
                    if (ExoPlayerHelper.isDirectVideoUrl(extractedUrl)) {
                        AppLog.put("VideoPlayViewModel: 策略2成功 - $extractedUrl")
                        return@withContext extractedUrl
                    }
                }
                
                // 策略3: 页面渲染解析（使用WebView）
                if (bookSource != null) {
                    AppLog.put("VideoPlayViewModel: 策略3 - WebView渲染解析")
                    val renderedUrl = parseVideoFromPage(url, bookSource)
                    if (ExoPlayerHelper.isDirectVideoUrl(renderedUrl)) {
                        AppLog.put("VideoPlayViewModel: 策略3成功 - $renderedUrl")
                        return@withContext renderedUrl
                    }
                }
                
                // 策略4: 直接HTTP请求页面内容解析
                AppLog.put("VideoPlayViewModel: 策略4 - HTTP请求页面解析")
                val pageContent = fetchPageContentDirect(url)
                val pageExtractedUrl = extractVideoUrl(pageContent)
                if (ExoPlayerHelper.isDirectVideoUrl(pageExtractedUrl)) {
                    AppLog.put("VideoPlayViewModel: 策略4成功 - $pageExtractedUrl")
                    return@withContext pageExtractedUrl
                }
                
                // 策略5: 使用诊断工具分析并尝试解析JSON响应
                AppLog.put("VideoPlayViewModel: 策略5 - 诊断工具分析")
                val diagnosticResult = VideoPlayDiagnostics.diagnoseVideoUrl(url)
                AppLog.put("VideoPlayViewModel: 诊断结果 - ${diagnosticResult.suggestions.joinToString("; ")}")
                
                if (diagnosticResult.parsedVideoUrls.isNotEmpty()) {
                    // 尝试使用诊断工具解析到的视频URL
                    for (parsedUrl in diagnosticResult.parsedVideoUrls) {
                        if (ExoPlayerHelper.isDirectVideoUrl(parsedUrl)) {
                            AppLog.put("VideoPlayViewModel: 策略5成功 - 使用诊断解析的URL: $parsedUrl")
                            return@withContext parsedUrl
                        }
                    }
                }
                
                // 策略6: 尝试常见的视频URL模式匹配
                AppLog.put("VideoPlayViewModel: 策略6 - 常见模式匹配")
                val patternUrl = tryCommonVideoPatterns(url)
                if (ExoPlayerHelper.isDirectVideoUrl(patternUrl)) {
                    AppLog.put("VideoPlayViewModel: 策略6成功 - $patternUrl")
                    return@withContext patternUrl
                }
                
                AppLog.put("VideoPlayViewModel: 所有解析策略失败，返回原URL尝试播放")
                return@withContext url
                
            } catch (e: Exception) {
                AppLog.put("VideoPlayViewModel: 处理视频URL失败", e)
                return@withContext ""
            }
        }
    }

    /**
     * 获取章节内容
     */
    suspend fun getChapterContent(chapter: BookChapter, bookSource: BookSource? = null): String {
        return withContext(Dispatchers.IO) {
            try {
                val source = bookSource ?: this@VideoPlayViewModel.bookSource
                val book = bookData.value
                
                if (source != null && book != null) {
                    WebBook.getContentAwait(
                        bookSource = source,
                        book = book,
                        bookChapter = chapter,
                        nextChapterUrl = null,
                        needSave = false
                    )
                } else {
                    ""
                }
            } catch (e: Exception) {
                AppLog.put("VideoPlayViewModel: 获取章节内容失败", e)
                ""
            }
        }
    }

    /**
     * 从内容中提取视频URL
     */
    private fun extractVideoUrl(content: String): String {
        AppLog.put("VideoPlayViewModel: 开始提取视频URL")
        
        // 检测到"播放直链："提示但无实际链接时直接返回空
        if (content.contains("播放直链：") && !content.contains("http")) {
            AppLog.put("VideoPlayViewModel: 检测到播放直链标识但无实际链接")
            return ""
        }

        // 首先尝试解析JSON响应
        if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
            AppLog.put("VideoPlayViewModel: 检测到JSON格式，尝试解析")
            val jsonVideoUrl = extractVideoUrlFromJson(content)
            if (jsonVideoUrl.isNotBlank() && ExoPlayerHelper.isDirectVideoUrl(jsonVideoUrl)) {
                AppLog.put("VideoPlayViewModel: JSON解析成功 - $jsonVideoUrl")
                return jsonVideoUrl
            }
        }

        // 常见的视频URL模式
        val urlPatterns = listOf(
            // 直接的视频链接
            Pattern.compile("""https?://[^\s"'<>]+\.(?:mp4|m3u8|webm|ts|mkv|avi|flv|mov|wmv|3gp)(?:\?[^\s"'<>]*)?""", Pattern.CASE_INSENSITIVE),
            // M3U8播放列表
            Pattern.compile("""https?://[^\s"'<>]*\.m3u8[^\s"'<>]*""", Pattern.CASE_INSENSITIVE),
            // MPD文件
            Pattern.compile("""https?://[^\s"'<>]*\.mpd[^\s"'<>]*""", Pattern.CASE_INSENSITIVE),
            // 通用的媒体链接（包含video/play/stream关键词）
            Pattern.compile("""https?://[^\s"'<>]*(?:video|play|stream|media)[^\s"'<>]*""", Pattern.CASE_INSENSITIVE),
            // 任何HTTP链接（作为最后的尝试）
            Pattern.compile("""https?://[^\s"'<>]+""", Pattern.CASE_INSENSITIVE)
        )

        for ((index, pattern) in urlPatterns.withIndex()) {
            val matcher = pattern.matcher(content)
            while (matcher.find()) {
                val url = matcher.group()
                AppLog.put("VideoPlayViewModel: 模式${index + 1}找到链接 - $url")

                // 过滤掉明显不是媒体直链的URL
                if (ExoPlayerHelper.isDirectVideoUrl(url)) {
                    AppLog.put("VideoPlayViewModel: 确认为有效视频链接 - $url")
                    return url
                }
            }
        }

        AppLog.put("VideoPlayViewModel: 未找到有效的视频播放链接")
        return ""
    }

    /**
     * 从JSON内容中提取视频URL
     */
    private fun extractVideoUrlFromJson(jsonContent: String): String {
        try {
            AppLog.put("VideoPlayViewModel: 开始解析JSON内容")
            
            // 尝试解析为Map
            if (jsonContent.trim().startsWith("{")) {
                val jsonMap = GSON.fromJson(jsonContent, Map::class.java) as? Map<String, Any>
                jsonMap?.let { map ->
                    val videoUrl = findVideoUrlInMap(map)
                    if (videoUrl.isNotBlank()) {
                        AppLog.put("VideoPlayViewModel: 从JSON Map中找到视频URL - $videoUrl")
                        return videoUrl
                    }
                }
            }
            
            // 尝试解析为Array
            if (jsonContent.trim().startsWith("[")) {
                val jsonArray = GSON.fromJson(jsonContent, List::class.java) as? List<Any>
                jsonArray?.forEach { item ->
                    if (item is Map<*, *>) {
                        val videoUrl = findVideoUrlInMap(item as Map<String, Any>)
                        if (videoUrl.isNotBlank()) {
                            AppLog.put("VideoPlayViewModel: 从JSON Array中找到视频URL - $videoUrl")
                            return videoUrl
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            AppLog.put("VideoPlayViewModel: JSON解析失败", e)
        }
        
        return ""
    }

    /**
     * 在Map中递归查找视频URL
     */
    private fun findVideoUrlInMap(map: Map<String, Any>): String {
        // 常见的视频URL字段名
        val videoFields = listOf(
            "url", "video_url", "play_url", "stream_url", "src", "source", 
            "file", "path", "link", "href", "video", "stream", "media",
            "playUrl", "videoUrl", "streamUrl", "mediaUrl", "fileUrl",
            "video_link", "play_link", "stream_link", "media_link"
        )
        
        // 首先检查直接的视频字段
        for (field in videoFields) {
            val value = map[field]
            if (value is String && value.isNotBlank()) {
                if (ExoPlayerHelper.isDirectVideoUrl(value)) {
                    return value
                }
                // 如果不是直接的视频URL，但包含视频相关关键词，也尝试使用
                if (value.contains("video", ignoreCase = true) || 
                    value.contains("play", ignoreCase = true) ||
                    value.contains("stream", ignoreCase = true)) {
                    return value
                }
            }
        }
        
        // 递归检查嵌套的Map
        for ((key, value) in map) {
            when (value) {
                is Map<*, *> -> {
                    val nestedUrl = findVideoUrlInMap(value as Map<String, Any>)
                    if (nestedUrl.isNotBlank()) {
                        return nestedUrl
                    }
                }
                is List<*> -> {
                    for (item in value) {
                        if (item is Map<*, *>) {
                            val nestedUrl = findVideoUrlInMap(item as Map<String, Any>)
                            if (nestedUrl.isNotBlank()) {
                                return nestedUrl
                            }
                        }
                    }
                }
            }
        }
        
        return ""
    }

    /**
     * 从页面解析视频链接（WebView渲染）
     */
    private suspend fun parseVideoFromPage(url: String, bookSource: BookSource?): String {
        return try {
            AppLog.put("VideoPlayViewModel: 开始WebView渲染解析 - $url")
            
            val analyzeUrl = AnalyzeUrl(
                mUrl = url,
                source = bookSource,
                ruleData = bookData.value,
                chapter = chapterData.value
            )
            
            val response = analyzeUrl.getStrResponseAwait(useWebView = true)
            val content = response.body ?: ""
            
            AppLog.put("VideoPlayViewModel: WebView渲染完成，内容长度: ${content.length}")
            extractVideoUrl(content)
            
        } catch (e: Exception) {
            AppLog.put("VideoPlayViewModel: WebView渲染解析失败", e)
            ""
        }
    }

    /**
     * 直接HTTP请求获取页面内容
     */
    private suspend fun fetchPageContentDirect(url: String): String {
        return try {
            AppLog.put("VideoPlayViewModel: 开始HTTP直接请求 - $url")
            
            val analyzeUrl = AnalyzeUrl(
                mUrl = url,
                source = bookSource,
                ruleData = bookData.value,
                chapter = chapterData.value
            )
            
            val response = analyzeUrl.getStrResponseAwait(useWebView = false)
            val content = response.body ?: ""
            
            AppLog.put("VideoPlayViewModel: HTTP请求完成，内容长度: ${content.length}")
            content
            
        } catch (e: Exception) {
            AppLog.put("VideoPlayViewModel: HTTP直接请求失败", e)
            ""
        }
    }

    /**
     * 尝试常见的视频URL模式
     */
    private fun tryCommonVideoPatterns(url: String): String {
        AppLog.put("VideoPlayViewModel: 尝试常见视频URL模式")
        
        // 常见的视频网站URL转换模式
        val patterns = listOf(
            // 将页面URL转换为可能的视频直链
            { originalUrl: String ->
                when {
                    originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be") -> {
                        // YouTube链接处理（这里只是示例，实际需要YouTube API）
                        ""
                    }
                    originalUrl.contains("bilibili.com") -> {
                        // B站链接处理
                        ""
                    }
                    originalUrl.contains("/play/") -> {
                        // 将play页面转换为可能的视频文件链接
                        originalUrl.replace("/play/", "/video/") + ".mp4"
                    }
                    originalUrl.contains("/watch/") -> {
                        // 将watch页面转换为可能的视频文件链接
                        originalUrl.replace("/watch/", "/stream/") + ".m3u8"
                    }
                    else -> ""
                }
            }
        )
        
        for (pattern in patterns) {
            val result = pattern(url)
            if (result.isNotBlank() && ExoPlayerHelper.isDirectVideoUrl(result)) {
                AppLog.put("VideoPlayViewModel: 模式匹配成功 - $result")
                return result
            }
        }
        
        AppLog.put("VideoPlayViewModel: 所有模式匹配失败")
        return ""
    }

    /**
     * 跳转到指定章节
     */
    fun skipToChapter(index: Int) {
        viewModelScope.launch {
            try {
                val book = bookData.value ?: return@launch
                loadChapter(book, index)
                
                // 更新书籍进度
                withContext(Dispatchers.IO) {
                    book.durChapterIndex = index
                    book.durChapterPos = 0
                    appDb.bookDao.update(book)
                }
                
            } catch (e: Exception) {
                AppLog.put("VideoPlayViewModel: 跳转章节失败", e)
                context.toastOnUi("跳转失败: ${e.message}")
            }
        }
    }

    /**
     * 更新播放进度
     */
    fun updateProgress(position: Int) {
        playProgressData.value = position
        
        // 保存进度到数据库
        viewModelScope.launch {
            try {
                val book = bookData.value ?: return@launch
                withContext(Dispatchers.IO) {
                    book.durChapterPos = position
                    appDb.bookDao.update(book)
                }
            } catch (e: Exception) {
                AppLog.put("VideoPlayViewModel: 保存进度失败", e)
            }
        }
    }

    /**
     * 更新播放状态
     */
    fun updatePlayState(state: Int) {
        playStateData.value = state
    }

    /**
     * 更新播放速度
     */
    fun updatePlaySpeed(speed: Float) {
        playSpeedData.value = speed
    }

    /**
     * 获取下一章节
     */
    fun getNextChapter(): BookChapter? {
        val chapters = chapterListData.value ?: return null
        val currentIndex = VideoPlay.durChapterIndex
        
        return if (currentIndex < chapters.size - 1) {
            chapters[currentIndex + 1]
        } else {
            null
        }
    }

    /**
     * 获取上一章节
     */
    fun getPrevChapter(): BookChapter? {
        val chapters = chapterListData.value ?: return null
        val currentIndex = VideoPlay.durChapterIndex
        
        return if (currentIndex > 0) {
            chapters[currentIndex - 1]
        } else {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.put("VideoPlayViewModel: ViewModel清理")
    }
}
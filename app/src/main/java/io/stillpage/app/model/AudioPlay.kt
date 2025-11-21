package io.stillpage.app.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import io.stillpage.app.R
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.EventBus
import io.stillpage.app.constant.IntentAction
import io.stillpage.app.constant.Status
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.help.book.ContentProcessor

import io.stillpage.app.help.book.getBookSource
import io.stillpage.app.help.book.readSimulating
import io.stillpage.app.help.book.simulatedTotalChapterNum
import io.stillpage.app.help.book.update
import io.stillpage.app.help.coroutine.Coroutine
import io.stillpage.app.model.webBook.WebBook
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.service.AudioPlayService
import io.stillpage.app.utils.postEvent
import io.stillpage.app.utils.startService
import io.stillpage.app.utils.toastOnUi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelChildren
import splitties.init.appCtx

@SuppressLint("StaticFieldLeak")
@Suppress("unused")
object AudioPlay : CoroutineScope by MainScope() {
    /**
     * 播放模式枚举
     */
    enum class PlayMode(val iconRes: Int) {
        LIST_END_STOP(R.drawable.ic_play_mode_list_end_stop),
        SINGLE_LOOP(R.drawable.ic_play_mode_single_loop),
        RANDOM(R.drawable.ic_play_mode_random),
        LIST_LOOP(R.drawable.ic_play_mode_list_loop);

        fun next(): PlayMode {
            return when (this) {
                LIST_END_STOP -> SINGLE_LOOP
                SINGLE_LOOP -> RANDOM
                RANDOM -> LIST_LOOP
                LIST_LOOP -> LIST_END_STOP
            }
        }
    }
    var playMode = PlayMode.LIST_END_STOP
    var status = Status.STOP
    private var activityContext: Context? = null
    private var serviceContext: Context? = null
    private val context: Context get() = activityContext ?: serviceContext ?: appCtx
    var callback: CallBack? = null
    var book: Book? = null
    var chapterSize = 0
    var simulatedChapterSize = 0
    var durChapterIndex = 0
    var durChapterPos = 0
    var durChapter: BookChapter? = null
    var durPlayUrl = ""
    var durAudioSize = 0
    var inBookshelf = false
    var bookSource: BookSource? = null
    val loadingChapters = arrayListOf<Int>()
    private var retryCount = 0
    private const val MAX_RETRY_COUNT = 3
    // 进度保存节流窗口改为配置项：从 AppConfig 读取
    private var lastSaveAt: Long = 0
    // 标记是否是章节切换操作
    private var isChapterSwitching = false

    fun changePlayMode() {
        playMode = playMode.next()
        postEvent(EventBus.PLAY_MODE_CHANGED, playMode)
    }

    fun upData(book: Book) {
        AudioPlay.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        if (durChapterIndex != book.durChapterIndex) {
            durChapterIndex = book.durChapterIndex
            // 只有在非章节切换状态下才恢复播放位置，否则保持为0
            if (!isChapterSwitching) {
                durChapterPos = book.durChapterPos
                AppLog.put("AudioPlay: 恢复章节播放位置: ${book.durChapterPos}")
            } else {
                AppLog.put("AudioPlay: 章节切换中，保持位置为0")
            }
            durPlayUrl = ""
            durAudioSize = 0
        }
        upDurChapter()
    }

    fun resetData(book: Book) {
        // 如果当前有不同的书在播放，先保存进度并停止
        val currentBook = AudioPlay.book
        if (currentBook != null && currentBook.bookUrl != book.bookUrl) {
            AppLog.put("AudioPlay: 切换到新书籍，保存当前进度: ${currentBook.name} -> ${book.name}")
            saveRead() // 保存当前书籍的进度
            stop() // 停止当前播放
        }
        
        AudioPlay.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        bookSource = book.getBookSource()
        durChapterIndex = book.durChapterIndex
        durChapterPos = book.durChapterPos
        durPlayUrl = ""
        durAudioSize = 0
        upDurChapter()
        postEvent(EventBus.AUDIO_BUFFER_PROGRESS, 0)
    }

    private fun addLoading(index: Int): Boolean {
        synchronized(this) {
            if (loadingChapters.contains(index)) return false
            loadingChapters.add(index)
            return true
        }
    }

    private fun removeLoading(index: Int) {
        synchronized(this) {
            loadingChapters.remove(index)
        }
    }

    fun loadOrUpPlayUrl() {
        if (durPlayUrl.isEmpty()) {
            loadPlayUrl()
        } else {
            upPlayUrl()
        }
    }

    /**
     * 加载播放URL
     */
    private fun loadPlayUrl() {
        val index = durChapterIndex
        if (addLoading(index)) {
            val book = book
            val bookSource = bookSource
            if (book != null && bookSource != null) {
                upDurChapter()
                val chapter = durChapter
                if (chapter == null) {
                    removeLoading(index)
                    return
                }
                upLoading(true)
                WebBook.getContent(this, bookSource, book, chapter)
                    .onSuccess { content ->
                        AppLog.put("AudioPlay: 获取到内容 - 章节: ${chapter.title}, 内容长度: ${content.length}")
                        AppLog.put("AudioPlay: 内容预览 - ${content.take(200)}")

                        if (content.isEmpty()) {
                            if (retryCount < MAX_RETRY_COUNT) {
                                retryCount++
                                appCtx.toastOnUi("未获取到资源链接，正在重试($retryCount/$MAX_RETRY_COUNT)")
                                removeLoading(index)
                                loadPlayUrl() // 重试
                                return@onSuccess
                            } else {
                                appCtx.toastOnUi("未获取到资源链接")
                                retryCount = 0 // 重置重试计数
                            }
                        } else {
                            retryCount = 0 // 成功时重置重试计数
                            // 尝试从内容中提取播放链接
                            val playUrl = extractPlayUrl(content)
                            AppLog.put("AudioPlay: 提取的播放链接 - $playUrl")

                            if (playUrl.isNotEmpty()) {
                                contentLoadFinish(chapter, playUrl)
                            } else {
                                // 如果没有提取到播放链接，使用原内容
                                contentLoadFinish(chapter, content)
                            }
                        }
                    }.onError {
                        val shouldRetry = retryCount < MAX_RETRY_COUNT && (
                            it.message?.contains("404") == true ||
                            it.message?.contains("timeout") == true ||
                            it.message?.contains("connection") == true
                        )

                        if (shouldRetry) {
                            retryCount++
                            val errorMsg = "获取音频资源失败，正在重试($retryCount/$MAX_RETRY_COUNT)\n错误：${it.message}"
                            AppLog.put(errorMsg, it)
                            appCtx.toastOnUi("获取资源失败，正在重试($retryCount/$MAX_RETRY_COUNT)")
                            removeLoading(index)
                            // 延迟重试
                            Coroutine.async {
                                kotlinx.coroutines.delay(2000)
                                loadPlayUrl()
                            }
                            return@onError
                        }

                        retryCount = 0 // 重置重试计数
                        val errorMsg = when {
                            it.message?.contains("404") == true -> {
                                "获取音频资源失败：链接不存在(404)\n可能原因：\n1. 书源解析规则有误\n2. 音频文件已被删除\n3. 需要更新书源"
                            }
                            it.message?.contains("403") == true -> {
                                "获取音频资源失败：访问被拒绝(403)\n可能原因：\n1. 需要登录或认证\n2. IP被限制\n3. 防盗链保护"
                            }
                            it.message?.contains("timeout") == true -> {
                                "获取音频资源失败：连接超时\n请检查网络连接或稍后重试"
                            }
                            else -> {
                                "获取资源链接出错\n$it"
                            }
                        }
                        AppLog.put(errorMsg, it, true)
                        upLoading(false)
                    }.onFinally {
                        removeLoading(index)
                    }
            } else {
                removeLoading(index)
                appCtx.toastOnUi("book or source is null")
            }
        }
    }

    /**
     * 加载完成
     */
    private fun contentLoadFinish(chapter: BookChapter, content: String) {
        if (chapter.index == book?.durChapterIndex) {
            durPlayUrl = content

            upPlayUrl()
        }
    }



    private fun upPlayUrl() {
        // 如果是章节切换操作，总是从头播放
        if (isChapterSwitching) {
            AppLog.put("AudioPlay: 章节切换，使用playNew从头播放")
            isChapterSwitching = false // 重置标志
            playNew()
        } else if (durChapterPos == 0 || isPlayToEnd()) {
            AppLog.put("AudioPlay: 使用playNew从头播放，位置: $durChapterPos")
            playNew()
        } else {
            AppLog.put("AudioPlay: 使用play继续播放，位置: $durChapterPos")
            play()
        }
    }

    /**
     * 播放当前章节
     */
    fun play() {
        context.startService<AudioPlayService> {
            action = IntentAction.play
        }
    }

    /**
     * 从头播放新章节
     */
    private fun playNew() {
        context.startService<AudioPlayService> {
            action = IntentAction.playNew
        }
    }

    /**
     * 更新当前章节
     */
    fun upDurChapter() {
        val book = book ?: return
        durChapter = appDb.bookChapterDao.getChapter(book.bookUrl, durChapterIndex)
        durAudioSize = durChapter?.end?.toInt() ?: 0
        postEvent(EventBus.AUDIO_SUB_TITLE, durChapter?.title ?: appCtx.getString(R.string.data_loading))
        postEvent(EventBus.AUDIO_SIZE, durAudioSize)
        postEvent(EventBus.AUDIO_PROGRESS, durChapterPos)
    }

    fun pause(context: Context) {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.pause
            }
        }
    }

    fun resume(context: Context) {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.resume
            }
        }
    }

    fun stop() {
        AppLog.put("AudioPlay: 请求停止播放")
        // 先保存当前进度
        saveRead()
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.stop
            }
        }
        // 更新状态
        status = Status.STOP
        postEvent(EventBus.AUDIO_STATE, Status.STOP)
    }

    fun adjustSpeed(adjust: Float) {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.adjustSpeed
                putExtra("adjust", adjust)
            }
        }
    }

    fun adjustProgress(position: Int) {
        durChapterPos = position
        saveReadThrottled()
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.adjustProgress
                putExtra("position", position)
            }
        }
    }

    fun skipTo(index: Int) {
        Coroutine.async {
            AppLog.put("AudioPlay: 跳转到章节 $index，当前状态: $status")
            stopPlay()
            isChapterSwitching = true
            durChapterIndex = index
            durChapterPos = 0
            durPlayUrl = ""
            // 立即更新章节信息，确保UI显示正确
            upDurChapter()
            saveRead()
            // loadPlayUrl会通过upPlayUrl自动开始播放
            loadPlayUrl()
            AppLog.put("AudioPlay: 章节跳转请求已发送，等待加载完成后自动播放")
        }
    }

    fun prev() {
        AppLog.put("AudioPlay: prev() 调用，当前章节: $durChapterIndex，当前位置: $durChapterPos")
        Coroutine.async {
            stopPlay()
            if (durChapterIndex > 0) {
                isChapterSwitching = true
                durChapterIndex -= 1
                durChapterPos = 0
                durPlayUrl = ""
                AppLog.put("AudioPlay: 切换到上一章节 $durChapterIndex，位置重置为 0")
                // 立即更新章节信息，确保UI显示正确
                upDurChapter()
                saveRead()
                loadPlayUrl()
            }
        }
    }

    fun next() {
        AppLog.put("AudioPlay: next() 调用，当前章节: $durChapterIndex，当前位置: $durChapterPos")
        stopPlay()
        when (playMode) {
            PlayMode.LIST_END_STOP -> {
                if (durChapterIndex + 1 < simulatedChapterSize) {
                    isChapterSwitching = true
                    durChapterIndex += 1
                    durChapterPos = 0
                    durPlayUrl = ""
                    AppLog.put("AudioPlay: 切换到下一章节 $durChapterIndex，位置重置为 0")
                    // 立即更新章节信息，确保UI显示正确
                    upDurChapter()
                    saveRead()
                    loadPlayUrl()
                }
            }
            PlayMode.SINGLE_LOOP -> {
                isChapterSwitching = true
                durChapterPos = 0
                durPlayUrl = ""
                AppLog.put("AudioPlay: 单曲循环，位置重置为 0")
                // 立即更新章节信息，确保UI显示正确
                upDurChapter()
                saveRead()
                loadPlayUrl()
            }
            PlayMode.RANDOM -> {
                isChapterSwitching = true
                durChapterIndex = (0 until simulatedChapterSize).random()
                durChapterPos = 0
                durPlayUrl = ""
                AppLog.put("AudioPlay: 随机播放，切换到章节 $durChapterIndex，位置重置为 0")
                // 立即更新章节信息，确保UI显示正确
                upDurChapter()
                saveRead()
                loadPlayUrl()
            }
            PlayMode.LIST_LOOP -> {
                isChapterSwitching = true
                durChapterIndex = (durChapterIndex + 1) % simulatedChapterSize
                durChapterPos = 0
                durPlayUrl = ""
                AppLog.put("AudioPlay: 列表循环，切换到章节 $durChapterIndex，位置重置为 0")
                // 立即更新章节信息，确保UI显示正确
                upDurChapter()
                saveRead()
                loadPlayUrl()
            }
        }
    }

    fun setTimer(minute: Int) {
        if (AudioPlayService.isRun) {
            val intent = Intent(context, AudioPlayService::class.java)
            intent.action = IntentAction.setTimer
            intent.putExtra("minute", minute)
            context.startService(intent)
        } else {
            AudioPlayService.timeMinute = minute
            postEvent(EventBus.AUDIO_DS, minute)
        }
    }

    fun addTimer() {
        val intent = Intent(context, AudioPlayService::class.java)
        intent.action = IntentAction.addTimer
        context.startService(intent)
    }

    fun stopPlay() {
        if (AudioPlayService.isRun) {
            context.startService<AudioPlayService> {
                action = IntentAction.stopPlay
            }
        }
    }

    fun saveRead() {
        val book = book ?: return
        Coroutine.async {
            book.lastCheckCount = 0
            book.durChapterTime = System.currentTimeMillis()
            val chapterChanged = book.durChapterIndex != durChapterIndex
            book.durChapterIndex = durChapterIndex
            book.durChapterPos = durChapterPos
            if (chapterChanged) {
                appDb.bookChapterDao.getChapter(book.bookUrl, book.durChapterIndex)?.let {
                    book.durChapterTitle = it.getDisplayTitle(
                        ContentProcessor.get(book.name, book.origin).getTitleReplaceRules(),
                        book.getUseReplaceRule()
                    )
                }
            }
            book.update()
        }
    }

    /**
     * 保存章节长度
     */
    fun saveDurChapter(audioSize: Long) {
        val chapter = durChapter ?: return
        Coroutine.async {
            durAudioSize = audioSize.toInt()
            chapter.end = audioSize
            appDb.bookChapterDao.update(chapter)
        }
    }

    fun playPositionChanged(position: Int) {
        durChapterPos = position
        saveReadThrottled()
    }

    private fun saveReadThrottled(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastSaveAt >= AppConfig.saveThrottleMs.toLong()) {
            lastSaveAt = now
            saveRead()
        }
    }

    fun upLoading(loading: Boolean) {
        callback?.upLoading(loading)
    }

    private fun isPlayToEnd(): Boolean {
        return durChapterIndex + 1 == simulatedChapterSize
                && durChapterPos == durAudioSize
    }

    fun register(context: Context) {
        activityContext = context
        callback = context as CallBack
    }

    fun unregister(context: Context) {
        if (activityContext === context) {
            activityContext = null
            callback = null
        }
        coroutineContext.cancelChildren()
    }

    fun registerService(context: Context) {
        serviceContext = context
    }

    fun unregisterService() {
        serviceContext = null
    }

    /**
     * 获取ExoPlayer实例（用于视频播放）
     */
    fun getExoPlayer(): ExoPlayer? {
        return AudioPlayService.instance?.exoPlayer
    }

    /**
     * 从内容中提取播放链接
     */
    private fun extractPlayUrl(content: String): String {
        AppLog.put("AudioPlay: 开始提取播放链接")

        // 检查是否包含"播放直链："但链接为空的情况
        if (content.contains("播放直链：") && !content.contains("http")) {
            AppLog.put("AudioPlay: 检测到播放直链标识但无实际链接，可能需要刷新或重新获取")
            return ""
        }

        // 常见的音频URL模式
        val urlPatterns = listOf(
            // 直接的音频链接
            Regex("""https?://[^\s"'<>]+\.(?:mp3|m4a|aac|wav|ogg)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE),
            // 通用的媒体链接（包含audio关键词）
            Regex("""https?://[^\s"'<>]*(?:audio|play|stream)[^\s"'<>]*""", RegexOption.IGNORE_CASE),
            // 任何HTTP链接（作为最后的尝试）
            Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        )

        for ((index, pattern) in urlPatterns.withIndex()) {
            val matches = pattern.findAll(content)
            for (match in matches) {
                val url = match.value
                AppLog.put("AudioPlay: 模式${index + 1}找到链接 - $url")

                // 过滤掉明显不是媒体链接的URL
                if (isValidMediaUrl(url)) {
                    AppLog.put("AudioPlay: 确认为有效媒体链接 - $url")
                    return url
                }
            }
        }

        AppLog.put("AudioPlay: 未找到有效的播放链接")
        return ""
    }

    /**
     * 判断是否为有效的媒体URL
     */
    private fun isValidMediaUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()

        // 排除明显不是媒体的链接
        val excludePatterns = listOf(
            "javascript:", "mailto:", "tel:", "sms:",
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico",
            ".html", ".htm", ".php", ".asp", ".jsp"
        )

        for (pattern in excludePatterns) {
            if (lowerUrl.contains(pattern)) {
                return false
            }
        }

        // 包含音频相关关键词的链接优先
        val mediaKeywords = listOf(
            "audio", "play", "stream", "media",
            ".mp3", ".m4a", ".aac", ".wav", ".ogg"
        )

        return mediaKeywords.any { lowerUrl.contains(it) }
    }

    interface CallBack {

        fun upLoading(loading: Boolean)

        fun upPlayProgress(progress: Int) {}

        fun upPlaySpeed(speed: Float) {}

    }

}

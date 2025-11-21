package io.stillpage.app.model

import android.content.Context
import android.content.Intent
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.EventBus
import io.stillpage.app.constant.IntentAction
import io.stillpage.app.constant.Status
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.config.VideoPlayConfig
import io.stillpage.app.service.VideoPlayService
import io.stillpage.app.utils.postEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 视频播放管理类
 * 管理视频播放状态、章节切换等核心逻辑
 */
object VideoPlay {

    var book: Book? = null
    var bookSource: BookSource? = null
    var durChapter: BookChapter? = null
    var chapterSize = 0
    var durChapterIndex = 0
    var durChapterPos = 0
    var durPlayUrl = ""
    var status = Status.STOP
    var inBookshelf = false
    var headerMap = HashMap<String, String>()

    private var callBack: CallBack? = null
    private var videoPlayService: VideoPlayService? = null

    enum class PlayMode {
        LIST_END_STOP,      // 列表播放完停止
        LIST_LOOP,          // 列表循环播放
        SINGLE_LOOP         // 单集循环播放
    }

    var playMode = PlayMode.LIST_END_STOP

    interface CallBack {
        fun upContent(bookChapter: BookChapter, nextChapterUrl: String?)
        fun onVideoUrlParsed(url: String, headers: Map<String, String>)
        fun onVideoError(error: String)
    }

    fun registerCallBack(callBack: CallBack) {
        this.callBack = callBack
    }

    fun unregisterCallBack() {
        this.callBack = null
    }

    fun registerService(service: VideoPlayService) {
        this.videoPlayService = service
    }

    fun unregisterService() {
        this.videoPlayService = null
    }

    /**
     * 播放视频
     */
    fun play(
        context: Context,
        book: Book,
        chapter: BookChapter,
        bookSource: BookSource? = null,
        inBookshelf: Boolean = true
    ) {
        this.book = book
        this.bookSource = bookSource ?: appDb.bookSourceDao.getBookSource(book.origin)
        this.durChapter = chapter
        this.inBookshelf = inBookshelf
        this.durChapterIndex = chapter.index
        this.durChapterPos = book.durChapterPos
        
        AppLog.put("VideoPlay: 开始播放 ${book.name} - ${chapter.title}")
        
        val intent = Intent(context, VideoPlayService::class.java)
        intent.action = IntentAction.play
        context.startService(intent)
    }

    /**
     * 停止播放
     */
    fun stop(context: Context) {
        AppLog.put("VideoPlay: 停止播放")
        
        val intent = Intent(context, VideoPlayService::class.java)
        intent.action = IntentAction.stop
        context.startService(intent)
        
        status = Status.STOP
        postEvent(EventBus.VIDEO_STATE, Status.STOP)
    }

    /**
     * 暂停播放
     */
    fun pause(context: Context) {
        AppLog.put("VideoPlay: 暂停播放")
        
        val intent = Intent(context, VideoPlayService::class.java)
        intent.action = IntentAction.pause
        context.startService(intent)
    }

    /**
     * 恢复播放
     */
    fun resume(context: Context) {
        AppLog.put("VideoPlay: 恢复播放")
        
        val intent = Intent(context, VideoPlayService::class.java)
        intent.action = IntentAction.resume
        context.startService(intent)
    }

    /**
     * 上一集
     */
    fun prev() {
        if (durChapterIndex > 0) {
            durChapterIndex--
            skipTo(durChapterIndex)
        } else {
            AppLog.put("VideoPlay: 已经是第一集")
        }
    }

    /**
     * 下一集
     */
    fun next() {
        when (playMode) {
            PlayMode.SINGLE_LOOP -> {
                // 单集循环，重新播放当前集
                skipTo(durChapterIndex)
            }
            PlayMode.LIST_LOOP -> {
                // 列表循环
                if (durChapterIndex < chapterSize - 1) {
                    durChapterIndex++
                } else {
                    durChapterIndex = 0
                }
                skipTo(durChapterIndex)
            }
            PlayMode.LIST_END_STOP -> {
                // 列表播放完停止
                if (durChapterIndex < chapterSize - 1) {
                    durChapterIndex++
                    skipTo(durChapterIndex)
                } else {
                    AppLog.put("VideoPlay: 播放列表结束")
                    status = Status.STOP
                    postEvent(EventBus.VIDEO_STATE, Status.STOP)
                }
            }
        }
    }

    /**
     * 跳转到指定章节
     */
    fun skipTo(index: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val book = book ?: return@launch
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index)
                
                if (chapter != null) {
                    durChapter = chapter
                    durChapterIndex = index
                    durChapterPos = 0
                    
                    // 更新书籍进度
                    book.durChapterIndex = index
                    book.durChapterPos = 0
                    appDb.bookDao.update(book)
                    
                    AppLog.put("VideoPlay: 跳转到第${index + 1}集 - ${chapter.title}")
                    
                    // 通知UI更新
                    postEvent(EventBus.VIDEO_CHAPTER_CHANGE, index)
                    
                    // 开始播放新章节
                    videoPlayService?.let { service ->
                        val intent = Intent(service, VideoPlayService::class.java)
                        intent.action = IntentAction.play
                        service.startService(intent)
                    }
                } else {
                    AppLog.put("VideoPlay: 章节不存在 - index=$index")
                }
            } catch (e: Exception) {
                AppLog.put("VideoPlay: 跳转章节失败", e)
            }
        }
    }

    /**
     * 调整播放速度
     */
    fun adjustSpeed(adjust: Float, context: Context) {
        val intent = Intent(context, VideoPlayService::class.java)
        intent.action = IntentAction.adjustSpeed
        intent.putExtra("adjust", adjust)
        context.startService(intent)
    }

    /**
     * 设置播放速度
     */
    fun setSpeed(speed: Float) {
        VideoPlayConfig.defaultPlaySpeed = speed
        postEvent(EventBus.VIDEO_SPEED, speed)
    }

    /**
     * 获取当前播放进度百分比
     */
    fun getProgress(): Int {
        val chapter = durChapter ?: return 0
        val duration = chapter.end ?: 0L
        return if (duration > 0) {
            ((durChapterPos.toFloat() / duration) * 100).toInt()
        } else {
            0
        }
    }

    /**
     * 设置播放进度
     */
    fun seekTo(position: Long) {
        durChapterPos = position.toInt()
        book?.durChapterPos = durChapterPos
        
        videoPlayService?.let { service ->
            // 通过Service设置播放位置
            postEvent(EventBus.VIDEO_SEEK, position)
        }
    }

    /**
     * 获取章节列表
     */
    suspend fun getChapterList(): List<BookChapter> {
        val book = book ?: return emptyList()
        return appDb.bookChapterDao.getChapterList(book.bookUrl)
    }

    /**
     * 更新章节内容
     */
    fun updateContent(chapter: BookChapter, content: String) {
        callBack?.upContent(chapter, null)
    }

    /**
     * 视频URL解析完成
     */
    fun onVideoUrlParsed(url: String, headers: Map<String, String> = emptyMap()) {
        durPlayUrl = url
        this.headerMap.clear()
        this.headerMap.putAll(headers)
        
        callBack?.onVideoUrlParsed(url, headers)
        
        AppLog.put("VideoPlay: 视频URL解析完成 - $url")
    }

    /**
     * 视频播放错误
     */
    fun onVideoError(error: String) {
        AppLog.put("VideoPlay: 视频播放错误 - $error")
        callBack?.onVideoError(error)
        
        status = Status.STOP
        postEvent(EventBus.VIDEO_STATE, Status.STOP)
    }

    /**
     * 清理资源
     */
    fun clear() {
        book = null
        bookSource = null
        durChapter = null
        chapterSize = 0
        durChapterIndex = 0
        durChapterPos = 0
        durPlayUrl = ""
        status = Status.STOP
        inBookshelf = false
        headerMap.clear()
        callBack = null
        videoPlayService = null
        
        AppLog.put("VideoPlay: 清理资源完成")
    }
}
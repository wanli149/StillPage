package io.stillpage.app.ui.book.audio

import android.app.Application
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import io.stillpage.app.R
import io.stillpage.app.base.BaseViewModel
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.BookType
import io.stillpage.app.constant.EventBus
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.help.book.getBookSource
import io.stillpage.app.help.book.removeType
import io.stillpage.app.help.book.simulatedTotalChapterNum
import io.stillpage.app.model.AudioPlay
import io.stillpage.app.model.webBook.WebBook
import io.stillpage.app.utils.postEvent
import io.stillpage.app.utils.toastOnUi
import io.stillpage.app.utils.NetworkUtils

class AudioPlayViewModel(application: Application) : BaseViewModel(application) {
    val titleData = MutableLiveData<String>()
    val coverData = MutableLiveData<String>()

    fun initData(intent: Intent) = AudioPlay.apply {
        execute {
            val bookUrl = intent.getStringExtra("bookUrl") ?: book?.bookUrl ?: return@execute
            val chapterIndexExtra = intent.getIntExtra("chapterIndex", -1)
            inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            val shouldAutoPlay = intent.getBooleanExtra("autoPlay", true)

            var targetBook = appDb.bookDao.getBook(bookUrl)
            if (targetBook == null) {
                // 尝试解析书源并创建占位书籍
                val baseUrl = NetworkUtils.getBaseUrl(bookUrl)
                var source: BookSource? = null
                if (baseUrl != null) {
                    source = appDb.bookSourceDao.getBookSourceAddBook(baseUrl)
                }
                if (source == null) {
                    val parts = appDb.bookSourceDao.hasBookUrlPattern
                    source = parts.firstOrNull { part ->
                        val bs = part.getBookSource()
                        val pattern = bs?.bookUrlPattern
                        if (pattern.isNullOrBlank()) return@firstOrNull false
                        kotlin.runCatching {
                            bookUrl.matches(pattern.toRegex())
                        }.getOrDefault(false)
                    }?.getBookSource()
                }

                if (source != null) {
                    AppLog.put("AudioPlayViewModel: 解析书源成功 -> ${source.bookSourceName}")
                    targetBook = Book(
                        bookUrl = bookUrl,
                        tocUrl = "",
                        origin = source.bookSourceUrl,
                        originName = source.bookSourceName,
                        name = "",
                        author = "",
                        kind = null,
                        customTag = null,
                        coverUrl = null,
                        customCoverUrl = null,
                        intro = null,
                        customIntro = null,
                        charset = null,
                        type = BookType.audio,
                        group = 0,
                        latestChapterTitle = null,
                        latestChapterTime = System.currentTimeMillis(),
                        lastCheckTime = System.currentTimeMillis(),
                        lastCheckCount = 0,
                        totalChapterNum = 0,
                        durChapterTitle = null,
                        durChapterIndex = if (chapterIndexExtra >= 0) chapterIndexExtra else 0,
                        durChapterPos = 0,
                        durChapterTime = System.currentTimeMillis(),
                        wordCount = null,
                        canUpdate = true,
                        order = 0,
                        originOrder = 0,
                        variable = null,
                        readConfig = null,
                        syncTime = 0L
                    )
                    appDb.bookDao.insert(targetBook)
                    AudioPlay.bookSource = source
                } else {
                    AppLog.put("AudioPlayViewModel: 未匹配到书源 -> $bookUrl")
                    return@execute
                }
            } else {
                // 确保音频类型并根据传入索引定位
                if (targetBook.type and BookType.audio == 0) {
                    targetBook.type = targetBook.type or BookType.audio
                    appDb.bookDao.update(targetBook)
                }
                if (chapterIndexExtra >= 0) {
                    targetBook.durChapterIndex = chapterIndexExtra
                    // 不立即保存，等待initBook后统一处理
                }
                AudioPlay.bookSource = targetBook.getBookSource()
            }

            initBook(targetBook, shouldAutoPlay)
        }.onFinally {
            saveRead()
        }
    }

    private suspend fun initBook(book: Book, shouldAutoPlay: Boolean = true) {
        val isSameBook = AudioPlay.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            AudioPlay.upData(book)
        } else {
            AudioPlay.resetData(book)
        }
        titleData.postValue(book.name)
        coverData.postValue(book.getDisplayCover())
        // 尝试加载书籍信息，但即使失败也继续加载目录（参考 legado 的容错策略）
        if (book.tocUrl.isEmpty()) {
            kotlin.runCatching {
                loadBookInfo(book)
            }.onFailure {
                AppLog.put("详情页解析失败，尝试直接加载目录: ${it.localizedMessage}", it, true)
            }
        }
        // 加载目录（如果还未加载）
        val tocLoaded = if (AudioPlay.chapterSize == 0) loadChapterList(book) else true
        if (!tocLoaded || AudioPlay.chapterSize == 0) {
            context.toastOnUi(R.string.chapter_list_empty)
            return
        }

        // 保持 durChapterIndex 位置有效
        AudioPlay.upDurChapter()

        // 如果需要自动播放且当前没有在播放，则开始播放
        if (shouldAutoPlay && AudioPlay.status != io.stillpage.app.constant.Status.PLAY) {
            AudioPlay.loadOrUpPlayUrl()
        }
    }

    private suspend fun loadBookInfo(book: Book): Boolean {
        val bookSource = AudioPlay.bookSource ?: return true
        try {
            WebBook.getBookInfoAwait(bookSource, book)
            return true
        } catch (e: Exception) {
            AppLog.put("详情页出错: ${e.localizedMessage}", e, true)
            return false
        }
    }

    private suspend fun loadChapterList(book: Book): Boolean {
        val bookSource = AudioPlay.bookSource ?: return true
        try {
            val oldBook = book.copy()
            // 与详情页保持一致：启用 preUpdateJs，避免目录规则依赖预处理导致解析失败
            val cList = WebBook.getChapterListAwait(bookSource, book, true).getOrThrow()
            if (oldBook.bookUrl == book.bookUrl) {
                appDb.bookDao.update(book)
            } else {
                appDb.bookDao.replace(oldBook, book)
            }
            appDb.bookChapterDao.delByBook(book.bookUrl)
            appDb.bookChapterDao.insert(*cList.toTypedArray())
            AudioPlay.chapterSize = cList.size
            AudioPlay.simulatedChapterSize = book.simulatedTotalChapterNum()
            AudioPlay.upDurChapter()
            return true
        } catch (e: Exception) {
            context.toastOnUi(R.string.error_load_toc)
            return false
        }
    }

    fun upSource() {
        execute {
            val book = AudioPlay.book ?: return@execute
            AudioPlay.bookSource = book.getBookSource()
        }
    }

    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        execute {
            AudioPlay.book?.migrateTo(book, toc)
            book.removeType(BookType.updateError)
            AudioPlay.book?.delete()
            appDb.bookDao.insert(book)
            AudioPlay.book = book
            AudioPlay.bookSource = source
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            AudioPlay.upDurChapter()
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    fun removeFromBookshelf(success: (() -> Unit)?) {
        execute {
            AudioPlay.book?.let {
                appDb.bookDao.delete(it)
            }
        }.onSuccess {
            success?.invoke()
        }
    }

}
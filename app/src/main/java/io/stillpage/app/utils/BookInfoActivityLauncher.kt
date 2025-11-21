package io.stillpage.app.utils

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.BookSourceType
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.help.ContentTypeDetector
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType
import io.stillpage.app.help.book.isAudio
import io.stillpage.app.help.book.isDrama
import io.stillpage.app.help.book.isImage
import io.stillpage.app.ui.book.audio.info.AudioBookInfoActivity
import io.stillpage.app.ui.book.drama.info.DramaInfoActivity
import io.stillpage.app.ui.book.info.BookInfoActivity
import io.stillpage.app.ui.book.manga.info.MangaInfoActivity
import io.stillpage.app.ui.book.music.info.MusicInfoActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 书籍详情页面智能跳转工具类
 * 根据书籍类型自动跳转到对应的详情页面
 */
object BookInfoActivityLauncher {


    /**
     * 从Book对象启动详情页面
     */
    suspend fun launch(context: Context, book: Book) {
        val contentType = detectContentTypeFromBook(book)
        launchByType(context, contentType, book.name, book.author, book.bookUrl)
    }

    /**
     * 从SearchBook对象启动详情页面
     */
    suspend fun launch(context: Context, searchBook: SearchBook) {
        val contentType = detectContentTypeFromSearchBook(searchBook)
        launchByType(context, contentType, searchBook.name, searchBook.author, searchBook.bookUrl)
    }

    /**
     * 直接通过书籍信息启动详情页面
     */
    suspend fun launch(context: Context, name: String, author: String, bookUrl: String) {
        // 尝试从数据库获取书籍信息
        val book = withContext(Dispatchers.IO) {
            appDb.bookDao.getBook(name, author)
        }
        
        val contentType = if (book != null) {
            detectContentTypeFromBook(book)
        } else {
            // 从bookUrl获取书源信息进行判断
            val bookSource = withContext(Dispatchers.IO) {
                val origin = bookUrl.substringBefore("/", bookUrl)
                appDb.bookSourceDao.getBookSource(origin)
            }
            detectContentTypeFromBookSource(bookSource, name, author)
        }
        
        launchByType(context, contentType, name, author, bookUrl)
    }

    /**
     * 根据内容类型启动对应的详情页面
     */
    private fun launchByType(
        context: Context,
        contentType: ContentType,
        name: String,
        author: String,
        bookUrl: String
    ) {
        val intent = when (contentType) {
            ContentType.AUDIO -> {
                AppLog.put("智能跳转：音频书籍详情页 - $name")
                Intent(context, AudioBookInfoActivity::class.java)
            }

            ContentType.DRAMA -> {
                AppLog.put("智能跳转：短剧详情页 - $name")
                Intent(context, DramaInfoActivity::class.java)
            }

            ContentType.MUSIC -> {
                AppLog.put("智能跳转：音乐详情页 - $name")
                Intent(context, MusicInfoActivity::class.java)
            }

            ContentType.IMAGE -> {
                AppLog.put("智能跳转：漫画详情页 - $name")
                Intent(context, MangaInfoActivity::class.java)
            }

            else -> {
                AppLog.put("智能跳转：普通书籍详情页 - $name")
                Intent(context, BookInfoActivity::class.java)
            }
        }.apply {
            putExtra("name", name)
            putExtra("author", author)
            putExtra("bookUrl", bookUrl)
        }
        
        context.startActivity(intent)
    }

    /**
     * 从Book对象检测内容类型
     */
    private suspend fun detectContentTypeFromBook(book: Book): ContentType {
        // 优先使用Book对象的类型标记
        if (book.isDrama) return ContentType.DRAMA
        if (book.isAudio) return ContentType.AUDIO
        if (book.isImage) return ContentType.IMAGE

        // 使用统一检测器进行识别
        val contentType = withContext(Dispatchers.IO) {
            val source = appDb.bookSourceDao.getBookSource(book.origin)
            if (source != null) ContentTypeDetector.detectContentType(book.toSearchBook(), source)
            else ContentType.TEXT
        }
        return contentType
    }

    /**
     * 从SearchBook对象检测内容类型
     */
    private suspend fun detectContentTypeFromSearchBook(searchBook: SearchBook): ContentType {
        val contentType = withContext(Dispatchers.IO) {
            val source = appDb.bookSourceDao.getBookSource(searchBook.origin)
            if (source != null) ContentTypeDetector.detectContentType(searchBook, source)
            else ContentType.TEXT
        }
        return contentType
    }

    /**
     * 从书源信息检测内容类型
     */
    private fun detectContentTypeFromBookSource(
        bookSource: io.stillpage.app.data.entities.BookSource?,
        name: String,
        author: String
    ): ContentType {
        // 若无法取得书源，退回文本类型
        if (bookSource == null) return ContentType.TEXT

        // 使用统一解析规则：override > hint > explicit > heuristics
        return io.stillpage.app.help.ContentTypeResolver.resolveFromSource(bookSource)
    }
}

/**
 * Context扩展函数：启动书籍详情页面
 */
suspend fun Context.startBookInfoActivity(book: Book) {
    BookInfoActivityLauncher.launch(this, book)
}

suspend fun Context.startBookInfoActivity(searchBook: SearchBook) {
    BookInfoActivityLauncher.launch(this, searchBook)
}

suspend fun Context.startBookInfoActivity(name: String, author: String, bookUrl: String) {
    BookInfoActivityLauncher.launch(this, name, author, bookUrl)
}

/**
 * Fragment扩展函数：启动书籍详情页面
 */
suspend fun Fragment.startBookInfoActivity(book: Book) {
    BookInfoActivityLauncher.launch(requireContext(), book)
}

suspend fun Fragment.startBookInfoActivity(searchBook: SearchBook) {
    BookInfoActivityLauncher.launch(requireContext(), searchBook)
}

suspend fun Fragment.startBookInfoActivity(name: String, author: String, bookUrl: String) {
    BookInfoActivityLauncher.launch(requireContext(), name, author, bookUrl)
}

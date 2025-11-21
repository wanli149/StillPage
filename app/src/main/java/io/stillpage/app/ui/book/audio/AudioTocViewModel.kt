package io.stillpage.app.ui.book.audio

import android.app.Application
import io.stillpage.app.base.BaseViewModel
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter

class AudioTocViewModel(application: Application) : BaseViewModel(application) {

    fun loadChapterList(book: Book, callback: (List<BookChapter>) -> Unit) {
        execute {
            appDb.bookChapterDao.getChapterList(book.bookUrl)
        }.onSuccess { chapters ->
            callback(chapters)
        }.onError {
            callback(emptyList())
        }
    }
}

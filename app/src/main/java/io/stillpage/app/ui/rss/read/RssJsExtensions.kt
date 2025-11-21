package io.stillpage.app.ui.rss.read

import io.stillpage.app.data.entities.BaseSource
import io.stillpage.app.help.JsExtensions
import io.stillpage.app.ui.association.AddToBookshelfDialog
import io.stillpage.app.ui.book.search.SearchActivity
import io.stillpage.app.utils.showDialogFragment

@Suppress("unused")
class RssJsExtensions(private val activity: ReadRssActivity) : JsExtensions {

    override fun getSource(): BaseSource? {
        return activity.getSource()
    }

    fun searchBook(key: String) {
        SearchActivity.start(activity, key)
    }

    fun addBook(bookUrl: String) {
        activity.showDialogFragment(AddToBookshelfDialog(bookUrl))
    }

}

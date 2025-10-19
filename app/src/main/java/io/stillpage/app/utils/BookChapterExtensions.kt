package io.stillpage.app.utils

import io.stillpage.app.data.entities.BookChapter

fun BookChapter.internString() {
    title = title.intern()
    bookUrl = bookUrl.intern()
}

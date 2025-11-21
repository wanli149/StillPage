package io.stillpage.app.ui.book.manga.entities

import io.stillpage.app.data.entities.BookChapter

data class MangaChapter(
    val chapter: BookChapter,
    val pages: List<BaseMangaPage>,
    val imageCount: Int
)

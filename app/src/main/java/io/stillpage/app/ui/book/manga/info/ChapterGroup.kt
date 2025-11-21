package io.stillpage.app.ui.book.manga.info

import io.stillpage.app.data.entities.BookChapter

/**
 * 章节分组数据类
 * 用于支持章节列表的分组显示
 */
data class ChapterGroup(
    val title: String, // 分组标题（如卷名）
    val chapters: List<BookChapter>, // 该分组下的章节列表
    var isExpanded: Boolean = true // 分组是否展开
)
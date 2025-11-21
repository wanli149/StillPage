package io.stillpage.app.ui.book.source.manage

import io.stillpage.app.data.entities.BookSourcePart

/**
 * 书源分组项数据类
 */
sealed class BookSourceGroupItem {
    /**
     * 分组标题项
     */
    data class GroupHeader(
        val groupName: String,
        val isExpanded: Boolean = true,
        val sourceCount: Int = 0,
        val isContentTypeGroup: Boolean = false
    ) : BookSourceGroupItem()

    /**
     * 书源项
     */
    data class SourceItem(
        val bookSource: BookSourcePart
    ) : BookSourceGroupItem()
}
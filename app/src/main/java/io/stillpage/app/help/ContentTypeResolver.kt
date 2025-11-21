package io.stillpage.app.help

import io.stillpage.app.constant.BookSourceType
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.BookSourcePart
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType

/**
 * 统一内容类型解析：override > detector-hint > db-hint > heuristics
 */
object ContentTypeResolver {

    /**
     * 从完整书源解析内容类型（用于详情/信息展示等场景）
     */
    fun resolveFromSource(bookSource: BookSource): ContentType {
        // 1) 手动覆写
        when (bookSource.contentTypeOverride) {
            "TEXT" -> return ContentType.TEXT
            "AUDIO" -> return ContentType.AUDIO
            "IMAGE" -> return ContentType.IMAGE
            "MUSIC" -> return ContentType.MUSIC
            "DRAMA" -> return ContentType.DRAMA
            "FILE" -> return ContentType.FILE
        }

        // 2) 统一检测器的源类型提示（包含域名覆盖、书源类型与关键词）
        ContentTypeDetector.getSourceTypeHint(bookSource)?.let { return it }

        // 3) 显式类型（书源类型）——保底处理
        when (bookSource.bookSourceType) {
            BookSourceType.audio -> return ContentType.AUDIO
            BookSourceType.image -> return ContentType.IMAGE
            BookSourceType.file -> return ContentType.FILE
            else -> { /* default: continue */ }
        }

        // 4) 数据库提示（contentTypeHint）
        when (bookSource.contentTypeHint) {
            "TEXT" -> return ContentType.TEXT
            "AUDIO" -> return ContentType.AUDIO
            "IMAGE" -> return ContentType.IMAGE
            "MUSIC" -> return ContentType.MUSIC
            "DRAMA" -> return ContentType.DRAMA
            "FILE" -> return ContentType.FILE
        }

        // 5) 关键词轻量识别（名称/URL）
        return ContentTypeHeuristics.detectByNameAndUrl(bookSource.bookSourceName, bookSource.bookSourceUrl)
    }

    /**
     * 从 BookSourcePart 解析内容类型（管理列表场景）
     */
    fun resolveFromPart(part: BookSourcePart): ContentType {
        val bs = part.getBookSource()
        // 若能取到完整书源，统一走 resolveFromSource（确保分组与标签一致）
        if (bs != null) return resolveFromSource(bs)
        // 否则使用轻量识别（名称/URL）
        return ContentTypeHeuristics.detectByNameAndUrl(part.bookSourceName, part.bookSourceUrl)
    }
}
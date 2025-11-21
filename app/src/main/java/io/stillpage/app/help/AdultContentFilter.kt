package io.stillpage.app.help

import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.source.SourceHelp
import io.stillpage.app.utils.NetworkUtils

/**
 * 成人内容过滤策略集中化
 * - 统一关键词判定
 * - 统一 18+ 书源判定
 * - 尊重 AppConfig.enableAdultContent 开关
 */
object AdultContentFilter {
    // 关键词权重（按严重程度分级）
    private val keywordWeights: Map<String, Int> = mapOf(
        // 高权重关键词
        "r18" to 4, "18+" to 4, "18禁" to 4,
        "porn" to 4, "hentai" to 4, "xxx" to 4,
        // 中权重关键词
        "成人" to 3, "色情" to 3, "情色" to 3, "av" to 3,
        "sex" to 3, "性爱" to 3, "性愛" to 3, "性交" to 3,
        // 低权重关键词
        "激情" to 2, "福利" to 2, "黄书" to 2, "黃色" to 2,
        "h文" to 2, "h" to 2
    )

    // kind 语义桥接：常见分类到权重
    private val kindWeights: Map<String, Int> = mapOf(
        "成人" to 3, "情色" to 3, "色情" to 3,
        "同人r18" to 3, "r18" to 4,
        // 国际化常见分类
        "adult" to 3, "erotic" to 3, "ecchi" to 3, "hentai" to 4
    )

    // 域名信誉关键字（域名包含这些词语会加权）
    private val domainKeywords: Map<String, Int> = mapOf(
        "porn" to 5, "sex" to 5, "hentai" to 5, "xxx" to 5, "av" to 4
    )

    private fun textScore(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val t = text.lowercase()
        var score = 0
        keywordWeights.forEach { (k, w) ->
            if (t.contains(k)) score += w
        }
        return score
    }

    private fun kindScore(kind: String?): Int {
        if (kind.isNullOrBlank()) return 0
        val k = kind.lowercase()
        var score = 0
        kindWeights.forEach { (kw, w) ->
            if (k.contains(kw)) score += w
        }
        return score
    }

    private fun domainReputation(url: String?): Int {
        if (url.isNullOrBlank()) return 0
        // 强规则：命中 18+ 列表直接高权重
        if (SourceHelp.is18Plus(url)) return 10
        val host = NetworkUtils.getSubDomain(url).lowercase()
        var score = 0
        domainKeywords.forEach { (k, w) ->
            if (host.contains(k)) score += w
        }
        return score
    }

    /**
     * 计算成人分数：越高越可能为成人内容
     */
    private fun calcScore(name: String, kind: String?, intro: String?, origin: String?, source: BookSource?): Int {
        var score = 0
        score += textScore(name)
        score += textScore(intro)
        score += kindScore(kind)
        // 书源名与网址也纳入评分
        source?.let {
            score += textScore(it.bookSourceName)
            score += textScore(it.bookSourceUrl)
            score += domainReputation(it.bookSourceUrl)
        }
        // origin 域名信誉
        score += domainReputation(origin)
        return score
    }

    /**
     * 是否需要过滤：返回 true 表示该内容应被过滤掉（在开关关闭时）
     */
    fun shouldFilter(book: SearchBook, source: BookSource): Boolean {
        if (AppConfig.enableAdultContent) return false
        val score = calcScore(book.name, book.kind, book.intro, book.origin, source)
        return score >= AppConfig.adultScoreThreshold
    }

    /**
     * 便捷过滤：仅基于 SearchBook 自身与其 origin 进行判定
     */
    fun shouldFilter(book: SearchBook): Boolean {
        if (AppConfig.enableAdultContent) return false
        val score = calcScore(book.name, book.kind, book.intro, book.origin, null)
        return score >= AppConfig.adultScoreThreshold
    }

    /**
     * 书架 Book 过滤：基于 Book 与其 origin
     */
    fun shouldFilter(book: Book): Boolean {
        if (AppConfig.enableAdultContent) return false
        val score = calcScore(book.name, book.kind, book.intro, book.origin, null)
        return score >= AppConfig.adultScoreThreshold
    }

    /**
     * 是否属于成人内容（用于类型检测阶段）
     */
    fun isAdultContent(book: SearchBook, source: BookSource): Boolean {
        if (AppConfig.enableAdultContent) return false
        val score = calcScore(book.name, book.kind, book.intro, book.origin, source)
        return score >= AppConfig.adultScoreThreshold
    }
}
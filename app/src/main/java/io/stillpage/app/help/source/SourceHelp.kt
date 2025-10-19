package io.stillpage.app.help.source

import io.stillpage.app.constant.SourceType
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.BaseSource
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.BookSourcePart
import io.stillpage.app.data.entities.RssSource
import io.stillpage.app.help.AppCacheManager
import io.stillpage.app.help.config.SourceConfig
import io.stillpage.app.help.coroutine.Coroutine
import io.stillpage.app.model.AudioPlay
import io.stillpage.app.model.ReadBook
import io.stillpage.app.model.ReadManga
import io.stillpage.app.utils.EncoderUtils
import io.stillpage.app.utils.NetworkUtils
import io.stillpage.app.utils.splitNotBlank
import io.stillpage.app.utils.toastOnUi
import splitties.init.appCtx

object SourceHelp {

    private val list18Plus by lazy {
        try {
            return@lazy String(appCtx.assets.open("18PlusList.txt").readBytes())
                .splitNotBlank("\n").map {
                    EncoderUtils.base64Decode(it)
                }.toHashSet()
        } catch (_: Exception) {
            return@lazy emptySet()
        }
    }

    fun getSource(key: String?): BaseSource? {
        key ?: return null
        if (ReadBook.bookSource?.bookSourceUrl == key) {
            return ReadBook.bookSource
        } else if (AudioPlay.bookSource?.bookSourceUrl == key) {
            return AudioPlay.bookSource
        } else if (ReadManga.bookSource?.bookSourceUrl == key) {
            return ReadManga.bookSource
        }
        return appDb.bookSourceDao.getBookSource(key)
            ?: appDb.rssSourceDao.getByKey(key)
    }

    fun getSource(key: String?, @SourceType.Type type: Int): BaseSource? {
        key ?: return null
        return when (type) {
            SourceType.book -> appDb.bookSourceDao.getBookSource(key)
            SourceType.rss -> appDb.rssSourceDao.getByKey(key)
            else -> null
        }
    }

    fun deleteSource(key: String, @SourceType.Type type: Int) {
        when (type) {
            SourceType.book -> deleteBookSource(key)
            SourceType.rss -> deleteRssSource(key)
        }
    }

    fun deleteBookSourceParts(sources: List<BookSourcePart>) {
        appDb.runInTransaction {
            sources.forEach {
                deleteBookSourceInternal(it.bookSourceUrl)
            }
        }
        AppCacheManager.clearSourceVariables()
    }

    fun deleteBookSources(sources: List<BookSource>) {
        appDb.runInTransaction {
            sources.forEach {
                deleteBookSourceInternal(it.bookSourceUrl)
            }
        }
        AppCacheManager.clearSourceVariables()
    }

    private fun deleteBookSourceInternal(key: String) {
        appDb.bookSourceDao.delete(key)
        appDb.cacheDao.deleteSourceVariables(key)
        SourceConfig.removeSource(key)
    }

    fun deleteBookSource(key: String) {
        deleteBookSourceInternal(key)
        AppCacheManager.clearSourceVariables()
    }

    fun deleteRssSources(sources: List<RssSource>) {
        appDb.runInTransaction {
            sources.forEach {
                deleteRssSourceInternal(it.sourceUrl)
            }
        }
        AppCacheManager.clearSourceVariables()
    }

    private fun deleteRssSourceInternal(key: String) {
        appDb.rssSourceDao.delete(key)
        appDb.rssArticleDao.delete(key)
        appDb.cacheDao.deleteSourceVariables(key)
    }

    fun deleteRssSource(key: String) {
        deleteRssSourceInternal(key)
        AppCacheManager.clearSourceVariables()
    }

    fun enableSource(key: String, @SourceType.Type type: Int, enable: Boolean) {
        when (type) {
            SourceType.book -> appDb.bookSourceDao.enable(key, enable)
            SourceType.rss -> appDb.rssSourceDao.enable(key, enable)
        }
    }

    fun insertRssSource(vararg rssSources: RssSource) {
        val rssSourcesGroup = rssSources.groupBy {
            is18Plus(it.sourceUrl)
        }
        rssSourcesGroup[true]?.forEach {
            appCtx.toastOnUi("${it.sourceName}是18+网址,禁止导入.")
        }
        rssSourcesGroup[false]?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }
    }

    fun insertBookSource(vararg bookSources: BookSource) {
        // 在入库前为缺失的内容类型初判进行自动生成（不覆盖手动覆写与已存在的提示）
        bookSources.forEach { src ->
            if (src.contentTypeOverride.isNullOrBlank() && src.contentTypeHint.isNullOrBlank()) {
                val (hint, confidence) = io.stillpage.app.help.ContentTypeDetector.detectSourceTypeHintConfidence(src)
                src.contentTypeHint = hint?.name
                src.contentTypeConfidence = confidence.takeIf { it > 0f }
            }
        }

        val bookSourcesGroup = bookSources.groupBy {
            is18Plus(it.bookSourceUrl)
        }
        bookSourcesGroup[true]?.forEach {
            appCtx.toastOnUi("${it.bookSourceName}是18+网址,禁止导入.")
        }
        bookSourcesGroup[false]?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        }
        Coroutine.async {
            adjustSortNumber()
        }
    }

    fun is18Plus(url: String?): Boolean {
        if (list18Plus.isEmpty()) {
            return false
        }
        url ?: return false
        val baseUrl = NetworkUtils.getBaseUrl(url) ?: return false
        kotlin.runCatching {
            val host = baseUrl.split("//", ".").let {
                if (it.size > 2) "${it[it.lastIndex - 1]}.${it.last()}" else return false
            }
            return list18Plus.contains(host)
        }
        return false
    }

    /**
     * 调整排序序号
     */
    fun adjustSortNumber() {
        if (
            appDb.bookSourceDao.maxOrder > 99999
            || appDb.bookSourceDao.minOrder < -99999
            || appDb.bookSourceDao.hasDuplicateOrder
        ) {
            val sources = appDb.bookSourceDao.allPart
            sources.forEachIndexed { index, bookSource ->
                bookSource.customOrder = index
            }
            appDb.bookSourceDao.upOrder(sources)
        }
    }

}
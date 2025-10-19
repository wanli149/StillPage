package io.stillpage.app.ui.main.explore

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.stillpage.app.base.BaseViewModel
import io.stillpage.app.constant.BookSourceType
import io.stillpage.app.constant.BookType
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.constant.AppLog
import io.stillpage.app.help.coroutine.Coroutine
import io.stillpage.app.help.source.exploreKinds
import io.stillpage.app.help.ContentTypeDetector
import io.stillpage.app.help.SmartFetchStrategy
import io.stillpage.app.help.ExploreCacheManager
import io.stillpage.app.help.source.SourceHelp
import io.stillpage.app.help.AdultContentFilter
import io.stillpage.app.model.webBook.WebBook
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class ExploreNewViewModel(application: Application) : BaseViewModel(application) {

    // 内容类型枚举
    enum class ContentType(val displayName: String, val typeValue: Int) {
        TEXT("小说", BookType.text),
        AUDIO("听书", BookType.audio),
        IMAGE("漫画", BookType.image),
        MUSIC("音乐", -2), // 自定义类型
        DRAMA("短剧", -3), // 短剧类型
        FILE("文件", BookType.webFile),
        ALL("全部", -99)
    }

    // 排序键
    enum class SortKey(val displayName: String) {
        NAME_ASC("名称升序"),
        NAME_DESC("名称降序"),
        SOURCE_WEIGHT("来源权重"),
        CONTENT_TYPE("内容类型")
    }

    // 发现项数据类
    data class DiscoveryItem(
        val book: SearchBook,
        val bookSource: BookSource,
        val contentType: ContentType
    )

    val booksData = MutableLiveData<Map<ContentType, List<DiscoveryItem>>>()
    val currentContentType = MutableLiveData<ContentType>(ContentType.ALL)
    val currentSortKey = MutableLiveData<SortKey>(SortKey.NAME_ASC)
    val isLoading = MutableLiveData<Boolean>(false)
    val loadingMsg = MutableLiveData<String>("")
    val hasMore = MutableLiveData<Boolean>(true)

    private var loadingCoroutine: Coroutine<Unit>? = null
    private var currentPage = 1
    private var currentFetchConfig = SmartFetchStrategy.getSmartFetchConfig()
    private var lastLoadTimestamp: Long = 0L
    private val allItems = mutableListOf<DiscoveryItem>()
    private val processedSources = mutableSetOf<String>() // 已处理的书源

    /**
     * 加载发现数据（首次加载）
     */
    fun loadDiscoveryData() {
        if (isLoading.value == true) return

        // 重置状态
        currentPage = 1
        allItems.clear()
        processedSources.clear()
        hasMore.postValue(true)

        // 更新抓取配置
        currentFetchConfig = SmartFetchStrategy.getSmartFetchConfig()

        // 应用TTL默认配置（类型/书源），确保进入发现页即生效
        ExploreCacheManager.applyConfigDefaults()

        // 冷启动：尝试使用 latest 快照进行预渲染（按当前Tab类型优先）
        execute {
            try {
                val ct = currentContentType.value ?: ContentType.ALL
                val snapshot = ExploreCacheManager.getLatestSnapshot(ct)
                if (!snapshot.isNullOrEmpty()) {
                    preRenderFromSnapshot(snapshot)
                    AppLog.put("发现页：已使用latest快照预渲染 ${snapshot.size} 条")
                }
            } catch (e: Exception) {
                AppLog.put("发现页：预渲染latest快照失败", e)
            }
        }

        loadMoreData()
    }

    /**
     * 加载更多数据（分页加载）
     */
    fun loadMoreData() {
        if (isLoading.value == true) return
        // 分页配额保护与触发节流
        val now = System.currentTimeMillis()
        if (now - lastLoadTimestamp < currentFetchConfig.minLoadIntervalMs) {
            AppLog.put("发现页面：节流保护，忽略过于频繁的加载触发")
            return
        }
        lastLoadTimestamp = now

        loadingCoroutine?.cancel()
        loadingCoroutine = execute {
            isLoading.postValue(true)
            loadingMsg.postValue(if (currentPage == 1) "正在加载发现内容..." else "正在加载更多内容...")

            try {
                // 先尝试从缓存获取数据
                val cachedBooks = ExploreCacheManager.getCachedBooks(
                    ContentType.ALL,
                    currentPage,
                    emptyList(),
                    currentSortKey.value ?: SortKey.NAME_ASC,
                    io.stillpage.app.help.config.AppConfig.enableAdultContent
                )

                if (cachedBooks != null && cachedBooks.isNotEmpty()) {
                    AppLog.put("使用缓存数据，页面: $currentPage, 书籍数: ${cachedBooks.size}")
                    processCachedBooks(cachedBooks)
                    return@execute
                }

                val enabledSources = appDb.bookSourceDao.allEnabled
                if (enabledSources.isEmpty()) {
                    loadingMsg.postValue("没有启用的书源")
                    hasMore.postValue(false)
                    if (currentPage == 1) {
                        booksData.postValue(emptyMap())
                    }
                    return@execute
                }

                // 使用智能抓取策略获取优化的书源，优先使用已标记类型的书源，并过滤18+内容
                val perTypeQuota = SmartFetchStrategy.getSourcesPerPageForType(
                    currentFetchConfig,
                    currentContentType.value ?: ContentType.ALL
                )
                val sourcesToProcess = SmartFetchStrategy.getOptimizedSources(
                    currentPage, 
                    currentFetchConfig, 
                    currentContentType.value
                ).filter { !SourceHelp.is18Plus(it.bookSourceUrl) }
                    .take(perTypeQuota)

                if (sourcesToProcess.isEmpty()) {
                    // 没有更多书源了
                    hasMore.postValue(false)
                    loadingMsg.postValue("已加载全部内容")
                    return@execute
                }

                val newItems = mutableListOf<DiscoveryItem>()

                // 并发处理当前批次的书源，记录性能
                val sourceUrls = mutableListOf<String>()
                sourcesToProcess.map { source ->
                    async {
                        val startTime = System.currentTimeMillis()
                        try {
                            val items = exploreFromSource(source, currentPage)
                            val responseTime = System.currentTimeMillis() - startTime

                            sourceUrls.add(source.bookSourceUrl)

                            // 更新书源性能统计
                            SmartFetchStrategy.updateSourcePerformance(
                                source.bookSourceUrl,
                                responseTime,
                                true,
                                items.size
                            )

                            items
                        } catch (e: Exception) {
                            val responseTime = System.currentTimeMillis() - startTime
                            AppLog.put("发现页面加载书源失败: ${source.bookSourceName}", e)

                            // 记录失败的性能统计
                            SmartFetchStrategy.updateSourcePerformance(
                                source.bookSourceUrl,
                                responseTime,
                                false,
                                0
                            )

                            emptyList<DiscoveryItem>()
                        }
                    }
                }.awaitAll().forEach { items ->
                    newItems.addAll(items)
                }

                // 记录已处理的书源
                sourcesToProcess.forEach { processedSources.add(it.bookSourceUrl) }

                // 添加到总列表并去重
                allItems.addAll(newItems)
                val uniqueItems = deduplicateItems(allItems)
                val categorizedItems = categorizeItems(uniqueItems)

                // 缓存新数据
                val booksToCache = newItems.map { it.book }
                ExploreCacheManager.cacheBooks(
                    booksToCache,
                    ContentType.ALL,
                    currentPage,
                    sourceUrls,
                    currentSortKey.value ?: SortKey.NAME_ASC,
                    io.stillpage.app.help.config.AppConfig.enableAdultContent
                )

                booksData.postValue(categorizedItems)

                // 更新页码和状态
                currentPage++
                val totalProcessed = processedSources.size
                val totalSources = enabledSources.size
                hasMore.postValue(totalProcessed < totalSources)

                loadingMsg.postValue("已加载 $totalProcessed/$totalSources 个书源，共 ${uniqueItems.size} 本内容")

            } catch (e: Exception) {
                AppLog.put("发现页面加载失败", e)
                loadingMsg.postValue("加载失败: ${e.localizedMessage}")
            } finally {
                isLoading.postValue(false)
            }
        }
    }

    /**
     * 从单个书源获取发现内容
     */
    private suspend fun exploreFromSource(source: BookSource, page: Int = 1): List<DiscoveryItem> {
        val items = mutableListOf<DiscoveryItem>()

        try {
            val exploreKinds = source.exploreKinds()

            // 根据页码选择不同的分类，实现更好的内容分布
            val kindsToProcess = when {
                exploreKinds.isEmpty() -> emptyList()
                exploreKinds.size == 1 -> exploreKinds.take(1)
                page == 1 -> exploreKinds.take(2) // 第一页加载前2个分类
                else -> {
                    // 后续页面轮换加载其他分类
                    val startIndex = ((page - 1) * 2) % exploreKinds.size
                    val endIndex = minOf(startIndex + 2, exploreKinds.size)
                    if (startIndex < exploreKinds.size) {
                        exploreKinds.subList(startIndex, endIndex)
                    } else {
                        exploreKinds.take(2) // 回到开头
                    }
                }
            }

            kindsToProcess.forEach { exploreKind ->
                if (!exploreKind.url.isNullOrBlank()) {
                    try {
                        // 使用分页参数，每个分类获取更多书籍
                        val books = WebBook.exploreBookAwait(source, exploreKind.url!!, page)
                        books.take(currentFetchConfig.maxBooksPerSource / kindsToProcess.size).forEach { book ->
                            // 检查是否为成人内容
                            if (isInappropriateContent(book, source)) {
                                AppLog.put("发现页面：过滤掉不适当内容 - ${book.name}")
                                return@forEach
                            }
                            
                            val contentType = detectContentType(book, source)
                            items.add(DiscoveryItem(book, source, contentType))
                        }
                        AppLog.put("发现页面：${source.bookSourceName} - ${exploreKind.title} 获取到 ${books.size} 本书")
                    } catch (e: Exception) {
                        AppLog.put("发现页面抓取分类失败: ${source.bookSourceName} - ${exploreKind.title}", e)
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.put("发现页面处理书源失败: ${source.bookSourceName}", e)
        }

        return items
    }

    /**
     * 检查是否为不适当内容
     */
    private fun isInappropriateContent(book: SearchBook, source: BookSource): Boolean {
        return AdultContentFilter.shouldFilter(book, source)
    }

    /**
     * 处理缓存的书籍数据
     */
    private suspend fun processCachedBooks(cachedBooks: List<SearchBook>) {
        val items = mutableListOf<DiscoveryItem>()

        // 为缓存的书籍创建DiscoveryItem，需要获取对应的书源信息
        cachedBooks.forEach { book ->
            try {
                val source = appDb.bookSourceDao.getBookSource(book.origin)
                if (source != null) {
                    val contentType = ContentTypeDetector.detectContentType(book, source)
                    items.add(DiscoveryItem(book, source, contentType))
                }
            } catch (e: Exception) {
                AppLog.put("处理缓存书籍失败: ${book.name}", e)
            }
        }

        allItems.addAll(items)
        val uniqueItems = deduplicateItems(allItems)
        val categorizedItems = categorizeItems(uniqueItems)

        booksData.postValue(categorizedItems)
        currentPage++
        hasMore.postValue(true)

        loadingMsg.postValue("已从缓存加载 ${items.size} 本内容")
    }

    /**
     * 使用 latest 快照进行轻量预渲染（不影响分页状态）
     */
    private suspend fun preRenderFromSnapshot(snapshotBooks: List<SearchBook>) {
        val items = mutableListOf<DiscoveryItem>()
        snapshotBooks.forEach { book ->
            try {
                val source = appDb.bookSourceDao.getBookSource(book.origin)
                if (source != null) {
                    val contentType = ContentTypeDetector.detectContentType(book, source)
                    items.add(DiscoveryItem(book, source, contentType))
                }
            } catch (_: Exception) {
            }
        }
        val categorizedItems = categorizeItems(items)
        booksData.postValue(categorizedItems)
        loadingMsg.postValue("展示最近快照 ${items.size} 条，正在拉取最新内容...")
    }

    /**
     * 智能检测内容类型 - 优化版本
     */
    private fun detectContentType(book: SearchBook, bookSource: BookSource): ContentType {
        return try {
            // 优先使用优化的检测器
            io.stillpage.app.help.ContentTypeDetectorOptimized.detectContentType(book, bookSource)
        } catch (e: Exception) {
            AppLog.put("内容类型检测失败，使用默认检测器", e)
            // 降级到原检测器
            val sourceType = io.stillpage.app.help.ContentTypeResolver.resolveFromSource(bookSource)
            if (sourceType != ContentType.TEXT) sourceType
            else ContentTypeDetector.detectContentType(book, bookSource)
        }
    }

    /**
     * 去重处理
     */
    private fun deduplicateItems(items: List<DiscoveryItem>): List<DiscoveryItem> {
        fun normalizeBasic(s: String?): String {
            return (s ?: "")
                .lowercase()
                .replace("\u00A0", " ") // 不间断空格
                .replace("\u3000", " ") // 全角空格
                .replace("[\\s]+".toRegex(), " ")
                .trim()
        }

        fun canonicalKey(name: String?, author: String?): String {
            val n = normalizeBasic(name)
                .replace("[\\p{Punct}]".toRegex(), "") // 去除标点
                .replace("(全集|完结|连载中|最新)".toRegex(), "") // 去除常见后缀
                .trim()
            val a = normalizeBasic(author)
                .replace("[\\p{Punct}]".toRegex(), "")
                .trim()
            return if (a.isNotEmpty()) "$n-$a" else n
        }

        val seen = LinkedHashSet<String>()
        val result = ArrayList<DiscoveryItem>(items.size)
        for (item in items) {
            val key = canonicalKey(item.book.name, item.book.author)
            if (seen.add(key)) {
                result.add(item)
            }
        }
        return result
    }

    /**
     * 分类处理
     */
    private fun categorizeItems(items: List<DiscoveryItem>): Map<ContentType, List<DiscoveryItem>> {
        val comparator = when (currentSortKey.value ?: SortKey.NAME_ASC) {
            SortKey.NAME_ASC -> compareBy<DiscoveryItem> { it.book.name.lowercase() }
            SortKey.NAME_DESC -> compareByDescending<DiscoveryItem> { it.book.name.lowercase() }
            SortKey.SOURCE_WEIGHT -> compareByDescending<DiscoveryItem> { it.bookSource.customOrder ?: 0 }
            SortKey.CONTENT_TYPE -> compareBy<DiscoveryItem> { it.contentType.ordinal }
        }

        val categorized = items.groupBy { it.contentType }
            .mapValues { (_, list) -> list.sortedWith(comparator) }

        val allItems = items.sortedWith(comparator)
        return categorized + (ContentType.ALL to allItems)
    }

    /**
     * 切换内容类型
     */
    fun filterByContentType(contentType: ContentType) {
        currentContentType.value = contentType
    }

    /**
     * 切换排序
     */
    fun setSortKey(sortKey: SortKey) {
        if (currentSortKey.value == sortKey) return
        currentSortKey.value = sortKey
        // 触发重新排序展示，但不重新抓取
        booksData.value?.let { current ->
            val merged = current.values.flatten()
            val reCategorized = categorizeItems(merged)
            booksData.postValue(reCategorized)
        }
    }

    /**
     * 刷新数据
     */
    fun refreshData() {
        loadDiscoveryData()
    }

    /**
     * 检查是否在书架中
     */
    fun isInBookShelf(name: String, author: String): Boolean {
        return appDb.bookDao.getBook(name, author) != null
    }

    override fun onCleared() {
        super.onCleared()
        loadingCoroutine?.cancel()
    }
}

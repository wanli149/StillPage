package io.stillpage.app.help

import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType
import io.stillpage.app.constant.AppLog
import io.stillpage.app.utils.GSON
import io.stillpage.app.help.CacheManager
import io.stillpage.app.help.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 发现页面缓存管理器
 * 提供智能缓存策略，减少重复抓取，提升用户体验
 */
object ExploreCacheManager {

    // 缓存项
    data class CacheItem(
        val books: List<SearchBook>,
        val contentType: ContentType,
        val timestamp: Long,
        val page: Int,
        val sourceUrls: List<String>
    )

    // 缓存配置预设模板
    enum class CacheTemplate(
        val displayName: String,
        val maxCacheSize: Int,
        val defaultTtlMinutes: Int,
        val maxBooksPerPage: Int
    ) {
        FAST("快速模式", 30, 15, 80),      // 快速响应，较短缓存
        BALANCED("平衡模式", 50, 30, 100), // 默认平衡配置
        POWER("省电模式", 80, 60, 120),    // 长缓存，减少网络请求
        UNLIMITED("无限模式", 200, 120, 150) // 最大缓存，适合WiFi环境
    }

    // 缓存配置
    data class CacheConfig(
        val template: CacheTemplate = CacheTemplate.BALANCED,
        val maxCacheSize: Int = template.maxCacheSize,
        val cacheExpireTime: Long = template.defaultTtlMinutes * 60 * 1000L,
        val maxBooksPerPage: Int = template.maxBooksPerPage,
        val enableDiskCache: Boolean = true,
        val typeTtlMs: MutableMap<ContentType, Long> = mutableMapOf(),
        val sourceTtlMs: MutableMap<String, Long> = mutableMapOf()
    )

    private val memoryCache = ConcurrentHashMap<String, CacheItem>()
    private val cacheMutex = Mutex()
    private var config = CacheConfig()
    
    // 缓存访问频率统计（用于LRU + 频率算法）
    private val accessFrequency = ConcurrentHashMap<String, Int>()
    private val lastAccessTime = ConcurrentHashMap<String, Long>()
    
    // 缓存统计
    private var cacheHits = 0L
    private var cacheMisses = 0L
    
    // 动态缓存大小限制
    private val maxMemoryCacheSize: Int
        get() = config.maxCacheSize
    private val cacheCleanupThreshold: Int
        get() = (maxMemoryCacheSize * 0.8).toInt()

    init {
        // 应用默认缓存模板
        applyCacheTemplate(CacheTemplate.BALANCED)
    }
    
    /**
     * 应用缓存模板配置
     */
    fun applyCacheTemplate(template: CacheTemplate) {
        config = CacheConfig(template = template)
        
        // 根据模板设置不同内容类型的TTL
        val baseTtl = template.defaultTtlMinutes * 60 * 1000L
        config.typeTtlMs.clear()
        config.typeTtlMs.putAll(
            mapOf(
                ContentType.DRAMA to (baseTtl * 0.5).toLong(),  // 短剧更新快，减半
                ContentType.MUSIC to (baseTtl * 2.0).toLong(),  // 音乐更新慢，加倍
                ContentType.AUDIO to (baseTtl * 2.5).toLong(),  // 有声书更新最慢
                ContentType.IMAGE to (baseTtl * 2.0).toLong(),  // 漫画更新较慢
                ContentType.TEXT to baseTtl,                    // 小说使用基准时间
                ContentType.FILE to (baseTtl * 1.5).toLong(),   // 文件类适中
                ContentType.ALL to (baseTtl * 0.8).toLong()     // 汇总视图稍短
            )
        )
        
        AppLog.put("应用缓存模板: ${template.displayName}, 基础TTL: ${template.defaultTtlMinutes}分钟")
    }

    /**
     * 从 AppConfig 应用类型TTL与书源TTL的默认配置
     */
    fun applyConfigDefaults() {
        try {
            // 类型TTL（分钟 -> 毫秒）
            config.typeTtlMs[ContentType.ALL] = AppConfig.exploreTtlAllMin * 60_000L
            config.typeTtlMs[ContentType.DRAMA] = AppConfig.exploreTtlDramaMin * 60_000L
            config.typeTtlMs[ContentType.MUSIC] = AppConfig.exploreTtlMusicMin * 60_000L
            config.typeTtlMs[ContentType.AUDIO] = AppConfig.exploreTtlAudioMin * 60_000L
            config.typeTtlMs[ContentType.IMAGE] = AppConfig.exploreTtlImageMin * 60_000L
            config.typeTtlMs[ContentType.TEXT] = AppConfig.exploreTtlTextMin * 60_000L
            config.typeTtlMs[ContentType.FILE] = AppConfig.exploreTtlFileMin * 60_000L

            // 书源TTL映射：url=分钟，逗号或换行分隔
            val mapping = AppConfig.exploreSourceTtl
            if (!mapping.isNullOrBlank()) {
                mapping.split('\n', ',').forEach { raw ->
                    val kv = raw.trim()
                    if (kv.isEmpty()) return@forEach
                    val idx = kv.indexOf('=')
                    if (idx in 1 until kv.length) {
                        val url = kv.substring(0, idx).trim()
                        val minStr = kv.substring(idx + 1).trim()
                        val minutes = minStr.toIntOrNull()
                        if (minutes != null && url.isNotBlank()) {
                            setSourceTtl(url, minutes * 60_000L)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.put("应用发现页TTL默认配置失败", e)
        }
    }

    /**
     * 计算当前键的 TTL（毫秒）
     * 优先级：按源覆盖 > 按类型覆盖 > 默认 TTL
     */
    private fun getTtlMs(contentType: ContentType, sourceUrls: List<String>): Long {
        // 若任一源有自定义TTL，优先采用（取最小值以更保守）
        val sourceTtls = sourceUrls.mapNotNull { config.sourceTtlMs[it] }
        if (sourceTtls.isNotEmpty()) return sourceTtls.min()

        // 类型 TTL 覆盖
        config.typeTtlMs[contentType]?.let { return it }

        return config.cacheExpireTime
    }

    /**
     * 外部设置类型 TTL（毫秒）
     */
    fun setTypeTtl(type: ContentType, ttlMs: Long) {
        config.typeTtlMs[type] = ttlMs
    }

    /**
     * 外部设置某书源 TTL（毫秒）
     */
    fun setSourceTtl(sourceUrl: String, ttlMs: Long) {
        config.sourceTtlMs[sourceUrl] = ttlMs
    }

    /**
     * 生成缓存键
     */
    private fun generateCacheKey(
        contentType: ContentType,
        page: Int,
        sourceUrls: List<String>,
        sortKey: io.stillpage.app.ui.main.explore.ExploreNewViewModel.SortKey,
        adultEnabled: Boolean
    ): String {
        val sortedSources = sourceUrls.sorted().joinToString(",")
        val adultFlag = if (adultEnabled) "adult_on" else "adult_off"
        return "${contentType.name}_${page}_${sortedSources.hashCode()}_${sortKey.name}_$adultFlag"
    }

    /**
     * 获取缓存数据
     */
    suspend fun getCachedBooks(
        contentType: ContentType,
        page: Int,
        sourceUrls: List<String>,
        sortKey: io.stillpage.app.ui.main.explore.ExploreNewViewModel.SortKey,
        adultEnabled: Boolean
    ): List<SearchBook>? {
        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val cacheKey = generateCacheKey(contentType, page, sourceUrls, sortKey, adultEnabled)
                val cacheItem = memoryCache[cacheKey]
                
                if (cacheItem != null) {
                    // 检查缓存是否过期
                    val currentTime = System.currentTimeMillis()
                    val ttl = getTtlMs(contentType, sourceUrls)
                    if (currentTime - cacheItem.timestamp < ttl) {
                        // 更新访问统计
                        updateAccessStats(cacheKey)
                        cacheHits++
                        AppLog.put("发现页缓存命中: $cacheKey, 书籍数: ${cacheItem.books.size}")
                        return@withContext cacheItem.books
                    } else {
                        // 缓存过期，移除
                        memoryCache.remove(cacheKey)
                        accessFrequency.remove(cacheKey)
                        lastAccessTime.remove(cacheKey)
                        AppLog.put("发现页缓存过期: $cacheKey")
                    }
                }
                
                // 记录缓存未命中
                cacheMisses++

                // 尝试磁盘缓存
                if (config.enableDiskCache) {
                    try {
                        val diskKey = "explore_cache_$cacheKey"
                        CacheManager.get(diskKey)?.let { json ->
                            val arr = GSON.fromJson(json, Array<SearchBook>::class.java)
                            val books = arr?.toList()
                            if (books != null && books.isNotEmpty()) {
                                AppLog.put("发现页磁盘缓存命中: $cacheKey, 书籍数: ${books.size}")
                                memoryCache[cacheKey] = CacheItem(
                                    books = books,
                                    contentType = contentType,
                                    timestamp = System.currentTimeMillis(),
                                    page = page,
                                    sourceUrls = sourceUrls
                                )
                                return@withContext books
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.put("读取发现页磁盘缓存失败: $cacheKey", e)
                    }
                }
                
                null
            }
        }
    }

    /**
     * 缓存数据
     */
    suspend fun cacheBooks(
        books: List<SearchBook>,
        contentType: ContentType,
        page: Int,
        sourceUrls: List<String>,
        sortKey: io.stillpage.app.ui.main.explore.ExploreNewViewModel.SortKey,
        adultEnabled: Boolean
    ) {
        withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                try {
                    // 限制每页书籍数量
                    val limitedBooks = books.take(config.maxBooksPerPage)
                    
                    val cacheKey = generateCacheKey(contentType, page, sourceUrls, sortKey, adultEnabled)
                    val cacheItem = CacheItem(
                        books = limitedBooks,
                        contentType = contentType,
                        timestamp = System.currentTimeMillis(),
                        page = page,
                        sourceUrls = sourceUrls
                    )
                    
                    memoryCache[cacheKey] = cacheItem
                    
                    // 检查缓存大小，清理旧缓存
                    cleanupOldCache()
                    
                    AppLog.put("发现页数据已缓存: $cacheKey, 书籍数: ${limitedBooks.size}")

                    // 写入轻量快照，便于快速恢复与统计
                    saveSnapshot(contentType, page, sourceUrls, limitedBooks)
                    // 首页latest快照（仅第一页），用于冷启动预渲染
                    if (page == 1) {
                        saveLatestSnapshot(contentType, limitedBooks, sourceUrls)
                    }

                    // 写入磁盘缓存（用于跨重启命中）
                    if (config.enableDiskCache) {
                        try {
                            val diskKey = "explore_cache_$cacheKey"
                            val json = GSON.toJson(limitedBooks)
                            val ttlMs = getTtlMs(contentType, sourceUrls)
                            val saveSeconds = (ttlMs / 1000L).toInt()
                            CacheManager.put(diskKey, json, saveSeconds)
                            AppLog.put("发现页磁盘缓存写入: $cacheKey, 书籍数: ${limitedBooks.size}")
                        } catch (e: Exception) {
                            AppLog.put("写入发现页磁盘缓存失败: $cacheKey", e)
                        }
                    }
                } catch (e: Exception) {
                    AppLog.put("缓存发现页数据失败", e)
                }
            }
        }
    }

    /**
     * 按内容类型清理缓存（内存+磁盘）
     * 返回清理的内存项数量
     */
    suspend fun clearByType(type: ContentType): Int {
        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val prefix = "${type.name}_"
                val toRemove = memoryCache.keys.filter { it.startsWith(prefix) }
                toRemove.forEach { memoryCache.remove(it) }
                // 磁盘按前缀清理
                CacheManager.deleteByPrefix("explore_cache_${type.name}_")
                AppLog.put("发现页分类型清理: ${type.name}, 内存项: ${toRemove.size}")
                toRemove.size
            }
        }
    }

    /**
     * 按统一前缀清理磁盘缓存（用于批量清理）
     */
    fun clearDiskByPrefix(prefix: String) {
        CacheManager.deleteByPrefix(prefix)
        AppLog.put("发现页磁盘缓存前缀清理: $prefix")
    }

    /**
     * 更新访问统计
     */
    private fun updateAccessStats(cacheKey: String) {
        val currentTime = System.currentTimeMillis()
        accessFrequency[cacheKey] = accessFrequency.getOrDefault(cacheKey, 0) + 1
        lastAccessTime[cacheKey] = currentTime
    }
    
    /**
     * 清理旧缓存 - LRU + 访问频率优化算法
     */
    private fun cleanupOldCache() {
        if (memoryCache.size > cacheCleanupThreshold) {
            val currentTime = System.currentTimeMillis()
            val toRemove = mutableListOf<String>()
            
            // 首先移除过期的缓存
            memoryCache.entries.forEach { (key, item) ->
                val ttl = getTtlMs(item.contentType, item.sourceUrls)
                if (currentTime - item.timestamp > ttl) {
                    toRemove.add(key)
                }
            }
            
            // 如果还是太多，使用LRU + 访问频率算法
            if (memoryCache.size - toRemove.size > maxMemoryCacheSize) {
                val remaining = memoryCache.entries.filter { !toRemove.contains(it.key) }
                
                // 计算每个缓存项的优先级分数（访问频率 + 时间衰减）
                val scoredItems = remaining.map { (key, item) ->
                    val frequency = accessFrequency.getOrDefault(key, 0)
                    val lastAccess = lastAccessTime.getOrDefault(key, item.timestamp)
                    val timeSinceAccess = currentTime - lastAccess
                    
                    // 分数 = 访问频率 - 时间衰减（小时）
                    val score = frequency - (timeSinceAccess / (60 * 60 * 1000)).toInt()
                    Triple(key, item, score)
                }.sortedBy { it.third } // 按分数升序，分数低的先删除
                
                val additionalRemoveCount = (memoryCache.size - toRemove.size) - maxMemoryCacheSize + 10
                val additionalRemove = scoredItems.take(additionalRemoveCount)
                toRemove.addAll(additionalRemove.map { it.first })
            }
            
            // 执行清理
            toRemove.forEach { key ->
                memoryCache.remove(key)
                accessFrequency.remove(key)
                lastAccessTime.remove(key)
            }
            
            if (toRemove.isNotEmpty()) {
                AppLog.put("清理发现页缓存: ${toRemove.size} 项, 剩余: ${memoryCache.size}, 命中率: ${getCacheHitRate()}%")
            }
        }
    }
    
    /**
     * 获取缓存命中率
     */
    private fun getCacheHitRate(): Int {
        val total = cacheHits + cacheMisses
        return if (total > 0) ((cacheHits * 100) / total).toInt() else 0
    }

    /**
     * 清理过期缓存
     */
    suspend fun cleanupExpiredCache() {
        withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val currentTime = System.currentTimeMillis()
                val expiredKeys = memoryCache.entries
                    .filter { currentTime - it.value.timestamp > config.cacheExpireTime }
                    .map { it.key }
                
                expiredKeys.forEach { key ->
                    memoryCache.remove(key)
                }
                
                if (expiredKeys.isNotEmpty()) {
                    AppLog.put("清理发现页过期缓存: ${expiredKeys.size} 项")
                }
            }
        }
    }

    /**
     * 清空所有缓存
     */
    suspend fun clearAllCache() {
        withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val size = memoryCache.size
                memoryCache.clear()
                AppLog.put("清空发现页所有缓存: $size 项")
            }
        }
    }

    /**
     * 获取缓存统计信息
     */
    suspend fun getCacheStats(): CacheStats {
        return withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val currentTime = System.currentTimeMillis()
                val totalItems = memoryCache.size
                val expiredItems = memoryCache.values.count { 
                    currentTime - it.timestamp > config.cacheExpireTime 
                }
                val totalBooks = memoryCache.values.sumOf { it.books.size }
                
                CacheStats(
                    totalCacheItems = totalItems,
                    expiredItems = expiredItems,
                    totalCachedBooks = totalBooks,
                    memoryUsageKB = estimateMemoryUsage()
                )
            }
        }
    }

    /**
     * 估算内存使用量
     */
    private fun estimateMemoryUsage(): Long {
        // 简单估算，每本书大约1KB
        val totalBooks = memoryCache.values.sumOf { it.books.size }
        return totalBooks.toLong()
    }

    /**
     * 轻量快照项，仅保留必要字段
     */
    data class SnapshotItem(
        val name: String,
        val author: String?,
        val origin: String?,
        val ts: Long
    )

    /**
     * 保存发现页轻量快照到缓存前缀，方便快速展示与统计
     */
    private fun saveSnapshot(
        contentType: ContentType,
        page: Int,
        sourceUrls: List<String>,
        books: List<SearchBook>
    ) {
        try {
            val snapItems = books.map {
                SnapshotItem(
                    name = it.name ?: "",
                    author = it.author,
                    origin = it.origin,
                    ts = System.currentTimeMillis()
                )
            }
            val snapKey = "explore_snapshot_${contentType}_${page}_${sourceUrls.sorted().joinToString("_")}".take(128)
            val json = GSON.toJson(snapItems)
            // 快照保留时长较长：6小时
            CacheManager.put(snapKey, json, 6 * 60 * 60)
            AppLog.put("发现页快照写入: $snapKey, 条目数: ${snapItems.size}")
        } catch (e: Exception) {
            AppLog.put("写入发现页快照失败", e)
        }
    }

    /**
     * 保存 latest 快照（用于冷启动预渲染）
     * 直接保存精简后的 SearchBook 列表，优先选择第一页内容
     */
    private fun saveLatestSnapshot(
        contentType: ContentType,
        books: List<SearchBook>,
        sourceUrls: List<String>
    ) {
        try {
            val key = "explore_latest_${contentType.name}"
            val json = GSON.toJson(books.take(config.maxBooksPerPage))
            val ttlMs = getTtlMs(contentType, sourceUrls)
            val ttlSec = (ttlMs / 1000L).toInt()
            CacheManager.put(key, json, ttlSec)
            AppLog.put("发现页latest快照写入: $key, 条目数: ${books.size}")
        } catch (e: Exception) {
            AppLog.put("写入latest快照失败", e)
        }
    }

    /**
     * 读取 latest 快照（用于冷启动预渲染）
     */
    suspend fun getLatestSnapshot(contentType: ContentType): List<SearchBook>? {
        return withContext(Dispatchers.IO) {
            try {
                val key = "explore_latest_${contentType.name}"
                CacheManager.get(key)?.let { json ->
                    val arr = GSON.fromJson(json, Array<SearchBook>::class.java)
                    arr?.toList()
                }
            } catch (e: Exception) {
                AppLog.put("读取latest快照失败", e)
                null
            }
        }
    }

    /**
     * 预加载下一页
     */
    suspend fun preloadNextPage(
        contentType: ContentType,
        currentPage: Int,
        sourceUrls: List<String>,
        sortKey: io.stillpage.app.ui.main.explore.ExploreNewViewModel.SortKey,
        adultEnabled: Boolean,
        loadFunction: suspend (ContentType, Int, List<String>) -> List<SearchBook>
    ) {
        withContext(Dispatchers.IO) {
            try {
                val nextPage = currentPage + 1
                val cacheKey = generateCacheKey(contentType, nextPage, sourceUrls, sortKey, adultEnabled)
                
                // 检查是否已经缓存
                if (!memoryCache.containsKey(cacheKey)) {
                    AppLog.put("预加载发现页下一页: $nextPage")
                    val books = loadFunction(contentType, nextPage, sourceUrls)
                    cacheBooks(books, contentType, nextPage, sourceUrls, sortKey, adultEnabled)
                }
            } catch (e: Exception) {
                AppLog.put("预加载发现页下一页失败", e)
            }
        }
    }

    /**
     * 智能缓存策略
     * 根据用户行为调整缓存策略
     */
    suspend fun optimizeCacheStrategy(userBehavior: UserBehavior) {
        withContext(Dispatchers.IO) {
            // 根据用户行为调整缓存配置
            when (userBehavior.browsingPattern) {
                BrowsingPattern.FAST_SCROLL -> {
                    // 快速滚动，增加预加载
                    // 可以在这里调整预加载策略
                }
                BrowsingPattern.DETAILED_VIEW -> {
                    // 详细浏览，延长缓存时间
                    // 可以在这里调整缓存过期时间
                }
                BrowsingPattern.CATEGORY_FOCUSED -> {
                    // 专注特定分类，优化该分类的缓存
                    // 可以在这里调整分类相关的缓存策略
                }
            }
        }
    }

    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val totalCacheItems: Int,
        val expiredItems: Int,
        val totalCachedBooks: Int,
        val memoryUsageKB: Long
    )

    /**
     * 用户行为数据
     */
    data class UserBehavior(
        val browsingPattern: BrowsingPattern,
        val averageViewTime: Long,
        val preferredContentTypes: List<ContentType>
    )

    enum class BrowsingPattern {
        FAST_SCROLL,      // 快速滚动
        DETAILED_VIEW,    // 详细查看
        CATEGORY_FOCUSED  // 专注特定分类
    }
}
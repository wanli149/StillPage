package io.stillpage.app.help

import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.DiscoveryItem
import io.stillpage.app.constant.AppLog
import kotlin.math.max

/**
 * 内容去重器
 * 智能去重，考虑内容质量和来源可信度
 */
object ContentDeduplicator {

    // 质量评分权重
    private data class QualityWeights(
        val sourceWeight: Float = 0.3f,        // 书源权重
        val responseTime: Float = 0.2f,        // 响应时间
        val contentCompleteness: Float = 0.25f, // 内容完整性
        val updateFrequency: Float = 0.15f,     // 更新频率
        val userPreference: Float = 0.1f        // 用户偏好
    )

    // 相似度阈值配置
    private data class SimilarityThresholds(
        val exactMatch: Float = 0.95f,         // 精确匹配
        val highSimilarity: Float = 0.85f,     // 高相似度
        val mediumSimilarity: Float = 0.70f,   // 中等相似度
        val lowSimilarity: Float = 0.50f       // 低相似度
    )

    private val qualityWeights = QualityWeights()
    private val similarityThresholds = SimilarityThresholds()

    /**
     * 智能去重 - 主入口
     */
    fun deduplicateItems(items: List<DiscoveryItem>): List<DiscoveryItem> {
        if (items.isEmpty()) return items

        val startTime = System.currentTimeMillis()
        
        // 1. 按相似度分组
        val similarGroups = groupBySimilarity(items)
        
        // 2. 每组选择最佳项目
        val deduplicatedItems = similarGroups.map { group ->
            selectBestFromGroup(group)
        }
        
        val endTime = System.currentTimeMillis()
        AppLog.put("内容去重完成: ${items.size} -> ${deduplicatedItems.size}, 耗时: ${endTime - startTime}ms")
        
        return deduplicatedItems
    }

    /**
     * 按相似度分组
     */
    private fun groupBySimilarity(items: List<DiscoveryItem>): List<List<DiscoveryItem>> {
        val groups = mutableListOf<MutableList<DiscoveryItem>>()
        val processed = mutableSetOf<Int>()

        items.forEachIndexed { index, item ->
            if (processed.contains(index)) return@forEachIndexed

            val currentGroup = mutableListOf(item)
            processed.add(index)

            // 查找相似项目
            for (i in (index + 1) until items.size) {
                if (processed.contains(i)) continue

                val otherItem = items[i]
                val similarity = calculateSimilarity(item.book, otherItem.book)

                if (similarity >= similarityThresholds.mediumSimilarity) {
                    currentGroup.add(otherItem)
                    processed.add(i)
                }
            }

            groups.add(currentGroup)
        }

        return groups
    }

    /**
     * 计算相似度
     */
    private fun calculateSimilarity(book1: SearchBook, book2: SearchBook): Float {
        // 1. 标题相似度（主要指标）
        val titleSimilarity = calculateTextSimilarity(
            normalizeTitle(book1.name), 
            normalizeTitle(book2.name)
        ) * 0.6f

        // 2. 作者相似度
        val authorSimilarity = calculateTextSimilarity(
            normalizeAuthor(book1.author), 
            normalizeAuthor(book2.author)
        ) * 0.3f

        // 3. 简介相似度（辅助指标）
        val introSimilarity = if (!book1.intro.isNullOrBlank() && !book2.intro.isNullOrBlank()) {
            calculateTextSimilarity(
                normalizeText(book1.intro!!), 
                normalizeText(book2.intro!!)
            ) * 0.1f
        } else 0f

        return titleSimilarity + authorSimilarity + introSimilarity
    }

    /**
     * 标准化标题
     */
    private fun normalizeTitle(title: String?): String {
        return (title ?: "")
            .lowercase()
            .replace("\\s+".toRegex(), "")
            .replace("[\\p{Punct}]".toRegex(), "")
            .replace("(全集|完结|连载中|最新|精校版|典藏版|修订版)".toRegex(), "")
            .replace("第[一二三四五六七八九十\\d]+[部册卷集]".toRegex(), "")
            .trim()
    }

    /**
     * 标准化作者名
     */
    private fun normalizeAuthor(author: String?): String {
        return (author ?: "")
            .lowercase()
            .replace("\\s+".toRegex(), "")
            .replace("[\\p{Punct}]".toRegex(), "")
            .replace("(著|编著|译|主编|编)$".toRegex(), "")
            .trim()
    }

    /**
     * 标准化文本
     */
    private fun normalizeText(text: String): String {
        return text
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .replace("[\\p{Punct}]".toRegex(), "")
            .trim()
    }

    /**
     * 计算文本相似度（使用编辑距离算法）
     */
    private fun calculateTextSimilarity(text1: String, text2: String): Float {
        if (text1 == text2) return 1.0f
        if (text1.isEmpty() || text2.isEmpty()) return 0f

        val maxLength = max(text1.length, text2.length)
        val editDistance = calculateEditDistance(text1, text2)
        
        return 1.0f - (editDistance.toFloat() / maxLength)
    }

    /**
     * 计算编辑距离
     */
    private fun calculateEditDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        return dp[len1][len2]
    }

    /**
     * 从相似组中选择最佳项目
     */
    private fun selectBestFromGroup(group: List<DiscoveryItem>): DiscoveryItem {
        if (group.size == 1) return group[0]

        // 计算每个项目的质量分数
        val scoredItems = group.map { item ->
            val qualityScore = calculateQualityScore(item)
            Pair(item, qualityScore)
        }

        // 返回得分最高的项目
        val bestItem = scoredItems.maxByOrNull { it.second }?.first ?: group[0]
        
        AppLog.put("去重选择: 从${group.size}个相似项中选择 ${bestItem.book.name} (${bestItem.bookSource.bookSourceName})")
        
        return bestItem
    }

    /**
     * 计算质量分数
     */
    private fun calculateQualityScore(item: DiscoveryItem): Float {
        var score = 0f

        // 1. 书源权重分数
        val sourceWeightScore = (item.bookSource.weight.coerceIn(-100, 100) + 100) / 200f
        score += sourceWeightScore * qualityWeights.sourceWeight

        // 2. 响应时间分数（响应时间越短分数越高）
        val responseTimeScore = when {
            item.bookSource.respondTime < 3000 -> 1.0f
            item.bookSource.respondTime < 5000 -> 0.8f
            item.bookSource.respondTime < 10000 -> 0.6f
            item.bookSource.respondTime < 20000 -> 0.4f
            else -> 0.2f
        }
        score += responseTimeScore * qualityWeights.responseTime

        // 3. 内容完整性分数
        val completenessScore = calculateCompletenessScore(item.book)
        score += completenessScore * qualityWeights.contentCompleteness

        // 4. 更新频率分数
        val updateScore = calculateUpdateScore(item.bookSource)
        score += updateScore * qualityWeights.updateFrequency

        // 5. 用户偏好分数（基于书源的自定义排序）
        val preferenceScore = (item.bookSource.customOrder?.coerceIn(0, 100) ?: 50) / 100f
        score += preferenceScore * qualityWeights.userPreference

        return score
    }

    /**
     * 计算内容完整性分数
     */
    private fun calculateCompletenessScore(book: SearchBook): Float {
        var score = 0f
        var maxScore = 0f

        // 检查各个字段的完整性
        if (!book.name.isNullOrBlank()) {
            score += 0.3f
        }
        maxScore += 0.3f

        if (!book.author.isNullOrBlank()) {
            score += 0.2f
        }
        maxScore += 0.2f

        if (!book.intro.isNullOrBlank() && book.intro!!.length > 20) {
            score += 0.2f
        }
        maxScore += 0.2f

        if (!book.coverUrl.isNullOrBlank()) {
            score += 0.15f
        }
        maxScore += 0.15f

        if (!book.latestChapterTitle.isNullOrBlank()) {
            score += 0.15f
        }
        maxScore += 0.15f

        return if (maxScore > 0) score / maxScore else 0f
    }

    /**
     * 计算更新频率分数
     */
    private fun calculateUpdateScore(bookSource: BookSource): Float {
        val lastUpdateTime = bookSource.lastUpdateTime
        val currentTime = System.currentTimeMillis()
        val daysSinceUpdate = (currentTime - lastUpdateTime) / (24 * 60 * 60 * 1000)

        return when {
            daysSinceUpdate <= 1 -> 1.0f      // 1天内更新
            daysSinceUpdate <= 7 -> 0.8f      // 1周内更新
            daysSinceUpdate <= 30 -> 0.6f     // 1月内更新
            daysSinceUpdate <= 90 -> 0.4f     // 3月内更新
            else -> 0.2f                      // 超过3月未更新
        }
    }

    /**
     * 获取去重统计信息
     */
    data class DeduplicationStats(
        val originalCount: Int,
        val deduplicatedCount: Int,
        val removalRate: Float,
        val processingTimeMs: Long
    )

    /**
     * 执行去重并返回统计信息
     */
    fun deduplicateWithStats(items: List<DiscoveryItem>): Pair<List<DiscoveryItem>, DeduplicationStats> {
        val startTime = System.currentTimeMillis()
        val originalCount = items.size
        
        val deduplicatedItems = deduplicateItems(items)
        
        val endTime = System.currentTimeMillis()
        val stats = DeduplicationStats(
            originalCount = originalCount,
            deduplicatedCount = deduplicatedItems.size,
            removalRate = if (originalCount > 0) (originalCount - deduplicatedItems.size).toFloat() / originalCount else 0f,
            processingTimeMs = endTime - startTime
        )
        
        return Pair(deduplicatedItems, stats)
    }
}
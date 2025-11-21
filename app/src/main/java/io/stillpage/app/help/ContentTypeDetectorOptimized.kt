package io.stillpage.app.help

import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType
import io.stillpage.app.constant.AppLog
import java.util.concurrent.ConcurrentHashMap

/**
 * 优化的内容类型检测器
 * 使用多维度特征分析和机器学习辅助分类
 */
object ContentTypeDetectorOptimized {

    // 特征权重配置
    private data class FeatureWeights(
        val urlKeywords: Float = 0.4f,
        val nameKeywords: Float = 0.3f,
        val contentKeywords: Float = 0.2f,
        val sourcePattern: Float = 0.1f
    )

    // 检测结果缓存
    private val detectionCache = ConcurrentHashMap<String, ContentType>()
    private const val CACHE_SIZE_LIMIT = 1000

    // 关键词特征库 - 优化版本
    private val featureLibrary = mapOf(
        ContentType.AUDIO to FeatureSet(
            urlKeywords = mapOf(
                "audio" to 10f, "sound" to 8f, "listen" to 8f, "hear" to 6f,
                "mp3" to 12f, "m4a" to 10f, "wav" to 8f, "播放" to 8f,
                "tingbook" to 15f, "yousheng" to 12f, "audio" to 10f
            ),
            nameKeywords = mapOf(
                "有声书" to 15f, "听书" to 15f, "播讲" to 12f, "朗读" to 10f,
                "广播剧" to 12f, "相声" to 10f, "评书" to 10f, "音频" to 8f,
                "有声" to 8f, "主播" to 6f, "配音" to 6f, "演播" to 8f
            ),
            contentKeywords = mapOf(
                "播放时长" to 8f, "音质" to 6f, "声音" to 4f, "收听" to 6f,
                "试听" to 8f, "音频格式" to 10f, "比特率" to 8f
            ),
            sourcePatterns = listOf(
                ".*audio.*", ".*sound.*", ".*listen.*", ".*tingbook.*",
                ".*yousheng.*", ".*播客.*", ".*fm.*"
            )
        ),

        ContentType.MUSIC to FeatureSet(
            urlKeywords = mapOf(
                "music" to 12f, "song" to 10f, "album" to 10f, "artist" to 8f,
                "mp3" to 8f, "flac" to 10f, "音乐" to 10f, "歌曲" to 10f
            ),
            nameKeywords = mapOf(
                "音乐" to 12f, "歌曲" to 12f, "专辑" to 10f, "单曲" to 10f,
                "歌手" to 8f, "乐队" to 8f, "流行" to 6f, "摇滚" to 6f,
                "古典" to 6f, "民谣" to 6f, "说唱" to 6f, "电音" to 6f
            ),
            contentKeywords = mapOf(
                "作词" to 8f, "作曲" to 8f, "编曲" to 6f, "制作人" to 6f,
                "发行" to 4f, "唱片" to 6f, "MV" to 8f, "演唱会" to 6f
            ),
            sourcePatterns = listOf(
                ".*music.*", ".*song.*", ".*yinyue.*", ".*gequ.*",
                ".*fm.*", ".*radio.*"
            )
        ),

        ContentType.IMAGE to FeatureSet(
            urlKeywords = mapOf(
                "manga" to 15f, "comic" to 12f, "cartoon" to 10f, "image" to 8f,
                "manhua" to 12f, "dongman" to 10f, "漫画" to 12f, "图片" to 6f
            ),
            nameKeywords = mapOf(
                "漫画" to 15f, "连环画" to 10f, "绘本" to 8f, "插画" to 6f,
                "comic" to 10f, "manga" to 12f, "图文" to 6f, "画集" to 8f,
                "同人" to 8f, "条漫" to 10f, "四格" to 8f
            ),
            contentKeywords = mapOf(
                "作画" to 8f, "画风" to 6f, "分镜" to 8f, "彩页" to 6f,
                "连载" to 4f, "完结" to 4f, "更新" to 4f, "章节" to 4f
            ),
            sourcePatterns = listOf(
                ".*manga.*", ".*comic.*", ".*manhua.*", ".*dongman.*",
                ".*cartoon.*", ".*漫画.*"
            )
        ),

        ContentType.DRAMA to FeatureSet(
            urlKeywords = mapOf(
                "video" to 10f, "movie" to 12f, "drama" to 15f, "tv" to 8f,
                "film" to 10f, "cinema" to 8f, "duanju" to 15f, "shipin" to 10f,
                "m3u8" to 12f, "mp4" to 10f, "avi" to 8f, "mkv" to 8f
            ),
            nameKeywords = mapOf(
                "短剧" to 15f, "微剧" to 12f, "网剧" to 10f, "迷你剧" to 10f,
                "电影" to 12f, "电视剧" to 10f, "综艺" to 8f, "动漫" to 8f,
                "纪录片" to 8f, "视频" to 8f, "影视" to 8f, "影片" to 8f,
                "剧集" to 10f, "连续剧" to 8f, "系列" to 6f
            ),
            contentKeywords = mapOf(
                "时长" to 8f, "分钟" to 6f, "小时" to 6f, "导演" to 8f,
                "演员" to 8f, "主演" to 8f, "制片" to 6f, "出品" to 6f,
                "高清" to 6f, "HD" to 6f, "BD" to 6f, "在线播放" to 10f,
                "播放量" to 6f, "观看" to 4f, "剧情" to 6f
            ),
            sourcePatterns = listOf(
                ".*video.*", ".*movie.*", ".*drama.*", ".*film.*",
                ".*duanju.*", ".*shipin.*", ".*影视.*", ".*电影.*"
            )
        ),

        ContentType.TEXT to FeatureSet(
            urlKeywords = mapOf(
                "book" to 10f, "novel" to 12f, "text" to 8f, "read" to 8f,
                "xiaoshuo" to 12f, "shu" to 6f, "小说" to 10f, "书" to 6f
            ),
            nameKeywords = mapOf(
                "小说" to 10f, "文学" to 8f, "作品" to 4f, "故事" to 6f,
                "传记" to 8f, "散文" to 8f, "诗歌" to 8f, "随笔" to 6f,
                "玄幻" to 8f, "都市" to 6f, "言情" to 6f, "武侠" to 8f,
                "科幻" to 8f, "悬疑" to 6f, "推理" to 6f, "历史" to 6f
            ),
            contentKeywords = mapOf(
                "章节" to 8f, "字数" to 6f, "完本" to 6f, "连载" to 6f,
                "作者" to 4f, "简介" to 4f, "内容" to 2f, "阅读" to 4f
            ),
            sourcePatterns = listOf(
                ".*book.*", ".*novel.*", ".*xiaoshuo.*", ".*read.*",
                ".*文学.*", ".*小说.*"
            )
        ),

        ContentType.FILE to FeatureSet(
            urlKeywords = mapOf(
                "file" to 10f, "download" to 8f, "doc" to 8f, "pdf" to 10f,
                "zip" to 8f, "rar" to 8f, "wenjian" to 8f, "xiazai" to 8f
            ),
            nameKeywords = mapOf(
                "文件" to 10f, "资料" to 8f, "文档" to 8f, "资源" to 6f,
                "下载" to 8f, "压缩包" to 10f, "合集" to 6f, "打包" to 8f
            ),
            contentKeywords = mapOf(
                "大小" to 6f, "格式" to 6f, "下载链接" to 8f, "提取码" to 8f,
                "网盘" to 8f, "分享" to 4f, "文件夹" to 6f
            ),
            sourcePatterns = listOf(
                ".*file.*", ".*download.*", ".*pan.*", ".*disk.*",
                ".*网盘.*", ".*文件.*"
            )
        )
    )

    // 特征集合数据类
    private data class FeatureSet(
        val urlKeywords: Map<String, Float>,
        val nameKeywords: Map<String, Float>,
        val contentKeywords: Map<String, Float>,
        val sourcePatterns: List<String>
    )

    /**
     * 检测内容类型 - 主入口
     */
    fun detectContentType(book: SearchBook, bookSource: BookSource): ContentType {
        // 检查缓存
        val cacheKey = "${book.bookUrl}_${bookSource.bookSourceUrl}".hashCode().toString()
        detectionCache[cacheKey]?.let { return it }

        try {
            // 1. 优先使用书源明确标记的类型
            bookSource.contentTypeOverride?.let { override ->
                ContentType.values().find { it.name == override }?.let { type ->
                    cacheResult(cacheKey, type)
                    return type
                }
            }

            // 2. 使用统一解析规则
            val sourceType = io.stillpage.app.help.ContentTypeResolver.resolveFromSource(bookSource)
            if (sourceType != ContentType.TEXT) {
                cacheResult(cacheKey, sourceType)
                return sourceType
            }

            // 3. 多维度特征分析
            val detectedType = analyzeMultiDimensionalFeatures(book, bookSource)
            cacheResult(cacheKey, detectedType)
            return detectedType

        } catch (e: Exception) {
            AppLog.put("内容类型检测失败", e)
            return ContentType.TEXT
        }
    }

    /**
     * 多维度特征分析
     */
    private fun analyzeMultiDimensionalFeatures(book: SearchBook, bookSource: BookSource): ContentType {
        val weights = FeatureWeights()
        val scores = mutableMapOf<ContentType, Float>()

        // 准备分析文本
        val sourceUrl = bookSource.bookSourceUrl.lowercase()
        val sourceName = bookSource.bookSourceName.lowercase()
        val bookName = book.name.lowercase()
        val bookKind = book.kind?.lowercase() ?: ""
        val bookIntro = book.intro?.lowercase() ?: ""

        ContentType.values().forEach { contentType ->
            if (contentType == ContentType.ALL) return@forEach

            val featureSet = featureLibrary[contentType] ?: return@forEach
            var totalScore = 0f

            // 1. URL关键词分析
            val urlScore = analyzeKeywords(sourceUrl + sourceName, featureSet.urlKeywords)
            totalScore += urlScore * weights.urlKeywords

            // 2. 书名关键词分析
            val nameScore = analyzeKeywords(bookName + bookKind, featureSet.nameKeywords)
            totalScore += nameScore * weights.nameKeywords

            // 3. 内容关键词分析
            val contentScore = analyzeKeywords(bookIntro, featureSet.contentKeywords)
            totalScore += contentScore * weights.contentKeywords

            // 4. 源模式匹配
            val patternScore = analyzeSourcePatterns(sourceUrl, featureSet.sourcePatterns)
            totalScore += patternScore * weights.sourcePattern

            scores[contentType] = totalScore
        }

        // 返回得分最高的类型，如果得分太低则返回TEXT
        val maxEntry = scores.maxByOrNull { it.value }
        return if (maxEntry != null && maxEntry.value >= 3.0f) {
            AppLog.put("内容类型检测: ${book.name} -> ${maxEntry.key} (得分: ${maxEntry.value})")
            maxEntry.key
        } else {
            ContentType.TEXT
        }
    }

    /**
     * 关键词分析
     */
    private fun analyzeKeywords(text: String, keywords: Map<String, Float>): Float {
        var score = 0f
        keywords.forEach { (keyword, weight) ->
            if (text.contains(keyword)) {
                score += weight
            }
        }
        return score
    }

    /**
     * 源模式分析
     */
    private fun analyzeSourcePatterns(sourceUrl: String, patterns: List<String>): Float {
        var score = 0f
        patterns.forEach { pattern ->
            try {
                if (sourceUrl.matches(pattern.toRegex())) {
                    score += 10f
                }
            } catch (e: Exception) {
                // 忽略正则表达式错误
            }
        }
        return score
    }

    /**
     * 缓存检测结果
     */
    private fun cacheResult(key: String, type: ContentType) {
        if (detectionCache.size >= CACHE_SIZE_LIMIT) {
            // 清理一半缓存
            val keysToRemove = detectionCache.keys.take(CACHE_SIZE_LIMIT / 2)
            keysToRemove.forEach { detectionCache.remove(it) }
        }
        detectionCache[key] = type
    }

    /**
     * 清理缓存
     */
    fun clearCache() {
        detectionCache.clear()
        AppLog.put("内容类型检测缓存已清理")
    }

    /**
     * 获取缓存统计
     */
    fun getCacheStats(): Pair<Int, Int> {
        return Pair(detectionCache.size, CACHE_SIZE_LIMIT)
    }

    /**
     * 学习用户反馈（为未来的机器学习功能预留）
     */
    fun learnFromFeedback(book: SearchBook, bookSource: BookSource, correctType: ContentType) {
        // TODO: 实现用户反馈学习机制
        AppLog.put("用户反馈学习: ${book.name} -> $correctType")
    }
}
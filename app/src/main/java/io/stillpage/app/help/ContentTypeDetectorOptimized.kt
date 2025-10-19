package io.stillpage.app.help

import io.stillpage.app.constant.BookSourceType
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType
import io.stillpage.app.constant.AppLog
import java.util.regex.Pattern

/**
 * 优化的智能内容类型检测器
 * 解决分类错误问题，提供更准确的内容类型识别
 */
object ContentTypeDetectorOptimized {
    
    // 负向词典：当检测到这些词时，降低对应类型权重，避免误判
    private val negativeDramaTitleWords = listOf(
        "小说","书籍","目录","章节","卷","章","正文","电子书","txt","下载",
        "完本","连载","更新","作者","简介","评论","书评","推荐","排行",
        "阅读","在线阅读","免费阅读","全文","全集","合集","文库"
    )
    
    private val negativeDramaIntroWords = listOf(
        "连载","更新至","章节目录","本章","全文阅读","书评","作者简介",
        "字数","万字","完结","未完结","VIP章节","订阅","月票","推荐票",
        "起点","晋江","纵横","17k","创世","掌阅","咪咕","QQ阅读"
    )
    
    // 音频负向词典：避免音乐被误判为有声书
    private val negativeAudioWords = listOf(
        "歌词","作词","作曲","编曲","演唱","歌手","专辑","单曲","EP",
        "流行","摇滚","民谣","古典","爵士","电子","说唱","嘻哈"
    )
    
    // 音乐负向词典：避免有声书被误判为音乐
    private val negativeMusicWords = listOf(
        "播讲","朗读","主播","配音","广播剧","相声","评书","小说",
        "故事","讲述","演播","录制","有声版","听书版"
    )

    // 权重配置 - 平衡的权重系统
    private data class WeightConfig(
        val titleWeight: Double = 2.0,      // 降低标题权重
        val kindWeight: Double = 2.5,       // 分类权重最高
        val introWeight: Double = 1.2,      // 简介权重适中
        val sourceWeight: Double = 1.5,     // 书源权重
        val urlWeight: Double = 3.0,        // URL后缀权重最高
        val negativeWeight: Double = 1.5    // 负向词权重
    )
    
    private val weights = WeightConfig()

    /**
     * 检测内容类型 - 优化版本
     */
    fun detectContentType(book: SearchBook, bookSource: BookSource): ContentType {
        try {
            // 第一步：书源类型检测（优先级最高）
            val sourceTypeHint = getSourceTypeHint(bookSource)
            
            // 第二步：多维度内容分析
            val analysisResult = analyzeContentOptimized(book, bookSource)
            
            // 第三步：综合判断
            val finalType = makeFinalDecisionOptimized(sourceTypeHint, analysisResult, book, bookSource)
            
            AppLog.put("优化内容类型检测: ${book.name} -> $finalType (书源: $sourceTypeHint, 分析: ${analysisResult.maxByOrNull { it.value }})")
            
            return finalType
        } catch (e: Exception) {
            AppLog.put("内容类型检测失败: ${book.name}", e)
            return ContentType.TEXT
        }
    }

    /**
     * 书源类型检测 - 增强版本
     */
    private fun getSourceTypeHint(bookSource: BookSource): ContentType? {
        // 1) 明确的手动覆写优先
        val override = when (bookSource.contentTypeOverride) {
            "TEXT" -> ContentType.TEXT
            "AUDIO" -> ContentType.AUDIO
            "IMAGE" -> ContentType.IMAGE
            "MUSIC" -> ContentType.MUSIC
            "DRAMA" -> ContentType.DRAMA
            "FILE" -> ContentType.FILE
            else -> null
        }
        if (override != null) return override

        // 2) 书源类型标记
        val typeHint = when (bookSource.bookSourceType) {
            BookSourceType.audio -> ContentType.AUDIO
            BookSourceType.image -> ContentType.IMAGE
            BookSourceType.file -> ContentType.FILE
            else -> null
        }
        if (typeHint != null) return typeHint
        
        // 3) 书源名称和URL关键词检测
        val sourceName = bookSource.bookSourceName.lowercase()
        val sourceUrl = bookSource.bookSourceUrl.lowercase()
        val sourceInfo = "$sourceName $sourceUrl"
        
        return when {
            sourceInfo.contains("短剧") || sourceInfo.contains("drama") || 
            sourceInfo.contains("video") || sourceInfo.contains("movie") -> ContentType.DRAMA
            
            sourceInfo.contains("音乐") || sourceInfo.contains("music") || 
            sourceInfo.contains("song") || sourceInfo.contains("album") -> ContentType.MUSIC
            
            sourceInfo.contains("漫画") || sourceInfo.contains("comic") || 
            sourceInfo.contains("manga") || sourceInfo.contains("manhua") -> ContentType.IMAGE
            
            sourceInfo.contains("有声") || sourceInfo.contains("audio") || 
            sourceInfo.contains("听书") || sourceInfo.contains("podcast") -> ContentType.AUDIO
            
            else -> null
        }
    }

    /**
     * 优化的多维度内容分析
     */
    private fun analyzeContentOptimized(book: SearchBook, bookSource: BookSource): Map<ContentType, Int> {
        val scores = mutableMapOf<ContentType, Int>()

        // 准备分析文本
        val name = book.name.lowercase()
        val kind = book.kind?.lowercase() ?: ""
        val intro = book.intro?.lowercase() ?: ""
        val sourceUrl = bookSource.bookSourceUrl.lowercase()
        val sourceName = bookSource.bookSourceName.lowercase()

        // 分层分析
        analyzeTitle(name, scores)
        analyzeKind(kind, scores)
        analyzeIntro(intro, scores)
        analyzeSource(sourceUrl, sourceName, scores)
        analyzeUrls(book, bookSource, scores)
        
        // 特殊模式检测
        detectDurationPattern(name, kind, intro, book.wordCount?.lowercase(), scores)
        detectSeasonEpisodePattern(name, kind, intro, scores)

        return scores
    }

    /**
     * 分析标题 - 优化版本
     */
    private fun analyzeTitle(title: String, scores: MutableMap<ContentType, Int>) {
        // 短剧/影视关键词
        val dramaKeywords = mapOf(
            "短剧" to 20, "微短剧" to 20, "网剧" to 15, "迷你剧" to 15,
            "剧集" to 15, "连续剧" to 15, "电视剧" to 15, "网络剧" to 12,
            "影视" to 18, "电影" to 18, "影片" to 18, "视频" to 15,
            "纪录片" to 15, "综艺" to 12, "预告" to 10, "正片" to 12,
            "movie" to 18, "film" to 15, "tv" to 12, "episode" to 15,
            "hd" to 12, "4k" to 15, "1080p" to 15, "720p" to 12
        )

        // 有声书关键词
        val audioKeywords = mapOf(
            "有声书" to 20, "听书" to 20, "播讲" to 20, "朗读版" to 20,
            "广播剧" to 20, "相声" to 15, "评书" to 15, "小说朗读" to 20,
            "有声小说" to 20, "配音版" to 15, "主播" to 10, "朗诵" to 12
        )

        // 音乐关键词
        val musicKeywords = mapOf(
            "音乐专辑" to 20, "歌曲合集" to 20, "新歌榜" to 20, "排行榜" to 15,
            "金曲" to 15, "热歌" to 15, "流行歌曲" to 15, "经典歌曲" to 15,
            "歌手专辑" to 20, "乐队专辑" to 20, "单曲" to 12, "ep" to 15
        )

        // 漫画关键词
        val imageKeywords = mapOf(
            "漫画" to 20, "连环画" to 15, "绘本" to 15, "图文小说" to 20,
            "comic" to 15, "manga" to 15, "动漫" to 12, "插画集" to 12
        )

        // 应用正向关键词
        applyKeywords(title, dramaKeywords, ContentType.DRAMA, scores, weights.titleWeight)
        applyKeywords(title, audioKeywords, ContentType.AUDIO, scores, weights.titleWeight)
        applyKeywords(title, musicKeywords, ContentType.MUSIC, scores, weights.titleWeight)
        applyKeywords(title, imageKeywords, ContentType.IMAGE, scores, weights.titleWeight)

        // 应用负向关键词
        applyNegativeKeywords(title, negativeDramaTitleWords, ContentType.DRAMA, scores, weights.negativeWeight)
        applyNegativeKeywords(title, negativeAudioWords, ContentType.AUDIO, scores, weights.negativeWeight)
        applyNegativeKeywords(title, negativeMusicWords, ContentType.MUSIC, scores, weights.negativeWeight)
    }

    /**
     * 分析分类信息
     */
    private fun analyzeKind(kind: String, scores: MutableMap<ContentType, Int>) {
        val kindKeywords = mapOf(
            // 短剧/影视
            "短剧" to ContentType.DRAMA, "微短剧" to ContentType.DRAMA,
            "网剧" to ContentType.DRAMA, "剧集" to ContentType.DRAMA,
            "影视" to ContentType.DRAMA, "电影" to ContentType.DRAMA,
            "视频" to ContentType.DRAMA, "纪录片" to ContentType.DRAMA,
            
            // 有声书
            "有声书" to ContentType.AUDIO, "听书" to ContentType.AUDIO,
            "广播剧" to ContentType.AUDIO, "相声" to ContentType.AUDIO,
            "评书" to ContentType.AUDIO, "播客" to ContentType.AUDIO,
            
            // 音乐
            "音乐" to ContentType.MUSIC, "歌曲" to ContentType.MUSIC,
            "专辑" to ContentType.MUSIC, "流行音乐" to ContentType.MUSIC,
            
            // 漫画
            "漫画" to ContentType.IMAGE, "动漫" to ContentType.IMAGE,
            "连环画" to ContentType.IMAGE, "绘本" to ContentType.IMAGE
        )

        kindKeywords.forEach { (keyword, type) ->
            if (kind.contains(keyword)) {
                scores[type] = scores.getOrDefault(type, 0) + (15 * weights.kindWeight).toInt()
            }
        }
    }

    /**
     * 分析简介内容
     */
    private fun analyzeIntro(intro: String, scores: MutableMap<ContentType, Int>) {
        val introPatterns = mapOf(
            // 短剧特征
            "集数" to ContentType.DRAMA, "每集" to ContentType.DRAMA,
            "剧情" to ContentType.DRAMA, "演员" to ContentType.DRAMA,
            "导演" to ContentType.DRAMA, "片长" to ContentType.DRAMA,
            
            // 有声书特征
            "播音" to ContentType.AUDIO, "主播" to ContentType.AUDIO,
            "朗读" to ContentType.AUDIO, "配音" to ContentType.AUDIO,
            
            // 音乐特征
            "歌词" to ContentType.MUSIC, "作词" to ContentType.MUSIC,
            "作曲" to ContentType.MUSIC, "演唱" to ContentType.MUSIC,
            
            // 漫画特征
            "画风" to ContentType.IMAGE, "绘画" to ContentType.IMAGE,
            "插图" to ContentType.IMAGE, "漫画家" to ContentType.IMAGE
        )

        introPatterns.forEach { (keyword, type) ->
            if (intro.contains(keyword)) {
                scores[type] = scores.getOrDefault(type, 0) + (10 * weights.introWeight).toInt()
            }
        }

        // 负向词处理
        applyNegativeKeywords(intro, negativeDramaIntroWords, ContentType.DRAMA, scores, weights.negativeWeight)
    }

    /**
     * 分析书源信息
     */
    private fun analyzeSource(sourceUrl: String, sourceName: String, scores: MutableMap<ContentType, Int>) {
        val sourceText = "$sourceUrl $sourceName"
        
        val sourceKeywords = mapOf(
            // 短剧平台
            "短剧" to ContentType.DRAMA, "drama" to ContentType.DRAMA,
            "video" to ContentType.DRAMA, "movie" to ContentType.DRAMA,
            
            // 有声书平台
            "audio" to ContentType.AUDIO, "podcast" to ContentType.AUDIO,
            "听书" to ContentType.AUDIO, "有声" to ContentType.AUDIO,
            
            // 音乐平台
            "music" to ContentType.MUSIC, "song" to ContentType.MUSIC,
            "音乐" to ContentType.MUSIC,
            
            // 漫画平台
            "comic" to ContentType.IMAGE, "manga" to ContentType.IMAGE,
            "漫画" to ContentType.IMAGE
        )

        sourceKeywords.forEach { (keyword, type) ->
            if (sourceText.contains(keyword)) {
                scores[type] = scores.getOrDefault(type, 0) + (8 * weights.sourceWeight).toInt()
            }
        }
    }

    /**
     * 分析URL后缀
     */
    private fun analyzeUrls(book: SearchBook, bookSource: BookSource, scores: MutableMap<ContentType, Int>) {
        val urlBlob = (book.bookUrl + " " + book.tocUrl + " " + bookSource.bookSourceUrl).lowercase()
        
        val audioExt = listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".wav", ".ape")
        val videoExt = listOf(".mp4", ".m3u8", ".webm", ".ts", ".mpd", "/dash/")
        
        if (audioExt.any { urlBlob.contains(it) }) {
            scores[ContentType.MUSIC] = scores.getOrDefault(ContentType.MUSIC, 0) + (30 * weights.urlWeight).toInt()
        }
        if (videoExt.any { urlBlob.contains(it) }) {
            scores[ContentType.DRAMA] = scores.getOrDefault(ContentType.DRAMA, 0) + (30 * weights.urlWeight).toInt()
        }
    }

    /**
     * 检测时长模式
     */
    private fun detectDurationPattern(name: String, kind: String, intro: String, wordCount: String?, scores: MutableMap<ContentType, Int>) {
        val text = "$name $kind $intro ${wordCount ?: ""}"
        val regexes = listOf(
            Regex("\\b[0-9]{1,2}:[0-9]{2}\\b"),
            Regex("\\b[0-9]{1,2}:[0-9]{2}:[0-9]{2}\\b")
        )
        
        if (regexes.any { it.containsMatchIn(text) }) {
            val dramaPrior = scores.getOrDefault(ContentType.DRAMA, 0)
            if (dramaPrior >= 10) {
                scores[ContentType.DRAMA] = dramaPrior + 15
            } else {
                val musicPrev = scores.getOrDefault(ContentType.MUSIC, 0)
                scores[ContentType.MUSIC] = musicPrev + 8
            }
        }
    }

    /**
     * 检测季集模式
     */
    private fun detectSeasonEpisodePattern(name: String, kind: String, intro: String, scores: MutableMap<ContentType, Int>) {
        val text = "$name $kind $intro"
        val regexes = listOf(
            Regex("S[0-9]{1,2}E[0-9]{1,3}", RegexOption.IGNORE_CASE),
            Regex("第[一二三四五六七八九十0-9]+季"),
            Regex("第[一二三四五六七八九十0-9]+集")
        )
        
        if (regexes.any { it.containsMatchIn(text) }) {
            scores[ContentType.DRAMA] = scores.getOrDefault(ContentType.DRAMA, 0) + 20
        }
    }

    /**
     * 应用正向关键词
     */
    private fun applyKeywords(
        text: String,
        keywords: Map<String, Int>,
        type: ContentType,
        scores: MutableMap<ContentType, Int>,
        weight: Double
    ) {
        keywords.forEach { (keyword, score) ->
            if (text.contains(keyword)) {
                scores[type] = scores.getOrDefault(type, 0) + (score * weight).toInt()
            }
        }
    }

    /**
     * 应用负向关键词
     */
    private fun applyNegativeKeywords(
        text: String,
        negativeWords: List<String>,
        type: ContentType,
        scores: MutableMap<ContentType, Int>,
        weight: Double
    ) {
        val negCount = negativeWords.count { text.contains(it) }
        if (negCount > 0) {
            val prev = scores.getOrDefault(type, 0)
            val penalty = (10 * negCount * weight).toInt()
            scores[type] = (prev - penalty).coerceAtLeast(0)
        }
    }

    /**
     * 优化的综合判断
     */
    private fun makeFinalDecisionOptimized(
        sourceTypeHint: ContentType?,
        analysisResult: Map<ContentType, Int>,
        book: SearchBook,
        bookSource: BookSource
    ): ContentType {
        // 检查成人内容
        if (io.stillpage.app.help.AdultContentFilter.isAdultContent(book, bookSource)) {
            AppLog.put("检测到成人内容: ${book.name}，归类为TEXT类型")
            return ContentType.TEXT
        }

        // 书源类型优先，但允许强烈的内容分析结果覆盖
        if (sourceTypeHint != null && sourceTypeHint != ContentType.TEXT) {
            val maxScore = analysisResult.maxByOrNull { it.value }
            if (maxScore != null && maxScore.value >= 40 && maxScore.key != sourceTypeHint) {
                AppLog.put("内容类型冲突: 书源=${sourceTypeHint}, 分析=${maxScore.key}(${maxScore.value}), 采用分析结果")
                return maxScore.key
            }
            return sourceTypeHint
        }

        // 使用分析结果，提高判断阈值
        val maxScore = analysisResult.maxByOrNull { it.value }
        if (maxScore != null && maxScore.value >= 25) {
            return maxScore.key
        }

        return ContentType.TEXT
    }
}
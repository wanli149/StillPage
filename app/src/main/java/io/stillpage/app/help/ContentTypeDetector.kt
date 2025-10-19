package io.stillpage.app.help

import io.stillpage.app.constant.BookSourceType
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType
import io.stillpage.app.constant.AppLog
import java.util.regex.Pattern

/**
 * 智能内容类型检测器
 * 解决分类错误问题，提供更准确的内容类型识别
 */
object ContentTypeDetector {
    // 源域名/平台覆盖映射：若匹配则直接使用对应类型
    private val sourceDomainOverrides: Map<String, ContentType> = mapOf(
        // 视频/短剧平台
        "bilibili" to ContentType.DRAMA,
        "iqiyi" to ContentType.DRAMA,
        "youku" to ContentType.DRAMA,
        "v.qq.com" to ContentType.DRAMA,
        "tencentvideo" to ContentType.DRAMA,
        "youtube" to ContentType.DRAMA,
        // 音乐平台
        "spotify" to ContentType.MUSIC,
        "music.163.com" to ContentType.MUSIC,
        "qqmusic" to ContentType.MUSIC,
        // 有声/播客平台
        "ximalaya" to ContentType.AUDIO,
        "lizhi" to ContentType.AUDIO,
        "qingting" to ContentType.AUDIO,
        // 漫画平台
        "mangadex" to ContentType.IMAGE,
        "manhuadb" to ContentType.IMAGE,
        "manhua" to ContentType.IMAGE
    )

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

    /**
     * 检测内容类型
     */
    fun detectContentType(book: SearchBook, bookSource: BookSource): ContentType {
        // 第一步：书源类型检测（但不完全信任）
        val sourceTypeHint = getSourceTypeHint(bookSource)

        // 第二步：多维度内容分析
        val analysisResult = analyzeContent(book, bookSource)

        // 第三步：综合判断
        val finalType = makeFinalDecision(sourceTypeHint, analysisResult, book, bookSource)

        AppLog.put("内容类型检测: ${book.name} -> $finalType (书源类型: $sourceTypeHint, 分析结果: $analysisResult)")

        return finalType
    }

    /**
     * 书源类型检测 - 增强版本
     * 更准确地映射书源类型到内容类型
     */
    fun getSourceTypeHint(bookSource: BookSource): ContentType? {
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

        // 1.5) 平台/域名覆盖（若名称或URL包含特定平台标识）
        run {
            val sourceInfoQuick = (bookSource.bookSourceName + " " + bookSource.bookSourceUrl).lowercase()
            val domainMatch = sourceDomainOverrides.entries.firstOrNull { (key, _) ->
                sourceInfoQuick.contains(key)
            }
            if (domainMatch != null) return domainMatch.value
        }

        // 2) 书源类型标记次之
        return when (bookSource.bookSourceType) {
            BookSourceType.audio -> ContentType.AUDIO
            BookSourceType.image -> ContentType.IMAGE
            BookSourceType.file -> ContentType.FILE
            else -> {
                // 对于默认类型，检查书源名称和URL中的关键词
                val sourceName = bookSource.bookSourceName.lowercase()
                val sourceUrl = bookSource.bookSourceUrl.lowercase()
                val sourceInfo = "$sourceName $sourceUrl"
                
                when {
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
        }
    }

    /**
     * 计算书源类型提示与置信度
     * 规则：显式 bookSourceType → 0.9；关键词匹配 → 0.6；未知 → null/0.0
     */
    fun detectSourceTypeHintConfidence(bookSource: BookSource): Pair<ContentType?, Float> {
        // 手动覆写给最高置信度
        when (bookSource.contentTypeOverride) {
            "TEXT" -> return ContentType.TEXT to 1.0f
            "AUDIO" -> return ContentType.AUDIO to 1.0f
            "IMAGE" -> return ContentType.IMAGE to 1.0f
            "MUSIC" -> return ContentType.MUSIC to 1.0f
            "DRAMA" -> return ContentType.DRAMA to 1.0f
            "FILE" -> return ContentType.FILE to 1.0f
            else -> { /* no-op */ }
        }
        return when (bookSource.bookSourceType) {
            BookSourceType.audio -> ContentType.AUDIO to 0.9f
            BookSourceType.image -> ContentType.IMAGE to 0.9f
            BookSourceType.file -> ContentType.FILE to 0.9f
            else -> {
                val hint = getSourceTypeHint(bookSource)
                if (hint != null) hint to 0.6f else null to 0.0f
            }
        }
    }

    /**
     * 多维度内容分析
     */
    private fun analyzeContent(book: SearchBook, bookSource: BookSource): Map<ContentType, Int> {
        val scores = mutableMapOf<ContentType, Int>()

        // 准备分析文本
        val name = book.name.lowercase()
        val kind = book.kind?.lowercase() ?: ""
        val intro = book.intro?.lowercase() ?: ""
        val sourceUrl = bookSource.bookSourceUrl.lowercase()
        val sourceName = bookSource.bookSourceName.lowercase()

        // 分层分析：标题权重最高，其次是分类，再次是简介和书源信息
        analyzeTitle(name, scores)
        analyzeKind(kind, scores)
        analyzeIntro(intro, scores)
        analyzeSource(sourceUrl, sourceName, scores)
        // URL/后缀强信号：根据 tocUrl/bookUrl/sourceUrl 判定音视频
        analyzeUrls(book, bookSource, scores)
        // 时长模式（mm:ss / hh:mm:ss）仅在视频强指示时加分，避免音乐误判
        detectDurationPattern(name, kind, intro, book.wordCount?.lowercase(), scores)
        // 季/集 模式
        detectSeasonEpisodePattern(name, kind, intro, scores)

        return scores
    }

    /**
     * 分析标题（权重最高）- 优化版本
     */
    private fun analyzeTitle(title: String, scores: MutableMap<ContentType, Int>) {
        val titleWeight = 2.5 // 降低标题权重，避免过度依赖标题关键词

        // 短剧/影视/视频 检测（扩展）
        val dramaKeywords = mapOf(
            // 中文常见关键词
            "短剧" to 15, "微短剧" to 15, "网剧" to 12, "迷你剧" to 12,
            "剧集" to 12, "连续剧" to 12, "电视剧" to 12, "网络剧" to 10,
            "影视" to 15, "电影" to 15, "影片" to 15, "视频" to 12,
            "纪录片" to 12, "综艺" to 10, "预告" to 8, "正片" to 10, "花絮" to 8,
            "片段" to 8, "片长" to 10, "影院" to 8, "蓝光" to 10, "高清" to 10,
            // 英文/通用
            "movie" to 15, "film" to 12, "tv" to 10, "episode" to 12, "season" to 10,
            "trailer" to 8, "teaser" to 8, "clip" to 8, "cinema" to 8,
            // 分辨率/视频常见标识（小写匹配，已对 title lowercase）
            "hd" to 10, "uhd" to 10, "hdr" to 10, "4k" to 12, "8k" to 12,
            "1080p" to 12, "720p" to 10, "480p" to 8
        )

        // 有声书检测（精确化）
        val audioKeywords = mapOf(
            "有声书" to 15, "听书" to 15, "播讲" to 15, "朗读版" to 15,
            "广播剧" to 15, "相声" to 12, "评书" to 12, "小说朗读" to 15,
            "有声小说" to 15, "配音版" to 12, "主播" to 8, "朗诵" to 10
        )

        // 音乐检测（避免与有声书冲突）
        val musicKeywords = mapOf(
            "音乐专辑" to 15, "歌曲合集" to 15, "新歌榜" to 15, "排行榜" to 12,
            "金曲" to 12, "热歌" to 12, "流行歌曲" to 12, "经典歌曲" to 12,
            "歌手专辑" to 15, "乐队专辑" to 15, "单曲" to 10, "ep" to 12,
            // MV/视频版的音乐
        )

        // 漫画检测
        val imageKeywords = mapOf(
            "漫画" to 15, "连环画" to 12, "绘本" to 12, "图文小说" to 15,
            "comic" to 12, "manga" to 12, "动漫" to 10, "插画集" to 10
        )

        // 应用权重
        applyKeywords(title, dramaKeywords, ContentType.DRAMA, scores, titleWeight) // 短剧归类为 DRAMA
        applyKeywords(title, audioKeywords, ContentType.AUDIO, scores, titleWeight)
        applyKeywords(title, musicKeywords, ContentType.MUSIC, scores, titleWeight)
        applyKeywords(title, imageKeywords, ContentType.IMAGE, scores, titleWeight)

        // 负向词命中：降低 DRAMA 权重，避免“小说/章节/目录”等文本类标题误判为视频
        val negCountTitle = negativeDramaTitleWords.count { title.contains(it) }
        if (negCountTitle > 0) {
            val prev = scores.getOrDefault(ContentType.DRAMA, 0)
            val penalty = (8 * negCountTitle * titleWeight).toInt()
            scores[ContentType.DRAMA] = (prev - penalty).coerceAtLeast(0)
        }
    }

    /**
     * 分析分类信息
     */
    private fun analyzeKind(kind: String, scores: MutableMap<ContentType, Int>) {
        val kindWeight = 2.5 // 分类权重系数

        val kindKeywords = mapOf(
            // 短剧/影视/视频 -> DRAMA
            "短剧" to ContentType.DRAMA,
            "微短剧" to ContentType.DRAMA,
            "网剧" to ContentType.DRAMA,
            "剧集" to ContentType.DRAMA,
            "影视" to ContentType.DRAMA,
            "电影" to ContentType.DRAMA,
            "影片" to ContentType.DRAMA,
            "视频" to ContentType.DRAMA,
            "纪录片" to ContentType.DRAMA,
            "综艺" to ContentType.DRAMA,
            "预告" to ContentType.DRAMA,
            "正片" to ContentType.DRAMA,
            "花絮" to ContentType.DRAMA,

            // 有声书相关
            "有声书" to ContentType.AUDIO,
            "听书" to ContentType.AUDIO,
            "广播剧" to ContentType.AUDIO,
            "相声" to ContentType.AUDIO,
            "评书" to ContentType.AUDIO,
            "播客" to ContentType.AUDIO,
            "电台" to ContentType.AUDIO,

            // 音乐相关
            "音乐" to ContentType.MUSIC,
            "歌曲" to ContentType.MUSIC,
            "专辑" to ContentType.MUSIC,
            "流行音乐" to ContentType.MUSIC,
            "古典音乐" to ContentType.MUSIC,
            "MV" to ContentType.MUSIC,
            "单曲" to ContentType.MUSIC,

            // 漫画相关
            "漫画" to ContentType.IMAGE,
            "动漫" to ContentType.IMAGE,
            "连环画" to ContentType.IMAGE,
            "绘本" to ContentType.IMAGE
        )

        kindKeywords.forEach { (keyword, type) ->
            if (kind.contains(keyword)) {
                scores[type] = scores.getOrDefault(type, 0) + (12 * kindWeight).toInt()
            }
        }
    }

    /**
     * 分析简介内容
     */
    private fun analyzeIntro(intro: String, scores: MutableMap<ContentType, Int>) {
        val introWeight = 1.5 // 简介权重系数

        // 简介中的特征词汇
        val introPatterns = mapOf(
            // 短剧/影视特征 -> DRAMA
            "集数" to ContentType.DRAMA,
            "每集" to ContentType.DRAMA,
            "剧情" to ContentType.DRAMA,
            "演员" to ContentType.DRAMA,
            "导演" to ContentType.DRAMA,
            "电影" to ContentType.DRAMA,
            "影片" to ContentType.DRAMA,
            "视频" to ContentType.DRAMA,
            "片长" to ContentType.DRAMA,
            "时长" to ContentType.DRAMA,
            "上映" to ContentType.DRAMA,
            "收视" to ContentType.DRAMA,
            "评分" to ContentType.DRAMA,

            // 有声书/播客/电台特征
            "播音" to ContentType.AUDIO,
            "主播" to ContentType.AUDIO,
            "朗读" to ContentType.AUDIO,
            "配音" to ContentType.AUDIO,
            "播客" to ContentType.AUDIO,
            "电台" to ContentType.AUDIO,
            "有声" to ContentType.AUDIO,

            // 音乐特征
            "歌词" to ContentType.MUSIC,
            "作词" to ContentType.MUSIC,
            "作曲" to ContentType.MUSIC,
            "演唱" to ContentType.MUSIC,
            "专辑介绍" to ContentType.MUSIC,
            "无损" to ContentType.MUSIC,
            "flac" to ContentType.MUSIC,
            "ape" to ContentType.MUSIC,
            "wav" to ContentType.MUSIC,

            // 漫画特征
            "画风" to ContentType.IMAGE,
            "绘画" to ContentType.IMAGE,
            "插图" to ContentType.IMAGE,
            "漫画家" to ContentType.IMAGE
        )

        introPatterns.forEach { (keyword, type) ->
            if (intro.contains(keyword)) {
                scores[type] = scores.getOrDefault(type, 0) + (8 * introWeight).toInt()
            }
        }

        // 附加规则：当简介包含“改编/原著/Based on”类字样时，如果当前 DRAMA 分数不高则降低 DRAMA 权重，避免因一句“改编自小说”导致误判
        val adaptationHints = listOf("改编自", "原著", "based on", "原著小说", "改编自小说")
        if (adaptationHints.any { intro.contains(it) }) {
            val dramaPrev = scores.getOrDefault(ContentType.DRAMA, 0)
            if (dramaPrev < 20) {
                // 降低 DRAMA 分数，给 TEXT/BOOK 更多机会
                scores[ContentType.DRAMA] = (dramaPrev - 10).coerceAtLeast(0)
                AppLog.put("ContentTypeDetector: 检测到改编提示，弱化 DRAMA 分数 (当前: $dramaPrev)")
            }
        }

        // 负向词命中：简介出现文本信号，降低 DRAMA 权重
        val negCountIntro = negativeDramaIntroWords.count { intro.contains(it) }
        if (negCountIntro > 0) {
            val prev = scores.getOrDefault(ContentType.DRAMA, 0)
            val penalty = (6 * negCountIntro * introWeight).toInt()
            scores[ContentType.DRAMA] = (prev - penalty).coerceAtLeast(0)
        }
    }

    /**
     * 分析书源信息
     */
    private fun analyzeSource(sourceUrl: String, sourceName: String, scores: MutableMap<ContentType, Int>) {
        val sourceWeight = 1.0 // 书源权重系数
        val sourceText = "$sourceUrl $sourceName"

        val sourceKeywords = mapOf(
            // 短剧/影视/视频平台 -> DRAMA
            "短剧" to ContentType.DRAMA,
            "drama" to ContentType.DRAMA,
            "video" to ContentType.DRAMA,
            "movie" to ContentType.DRAMA,
            "film" to ContentType.DRAMA,
            "tv" to ContentType.DRAMA,
            "vod" to ContentType.DRAMA,
            "watch" to ContentType.DRAMA,
            "play" to ContentType.DRAMA,
            "series" to ContentType.DRAMA,
            "episode" to ContentType.DRAMA,
            "season" to ContentType.DRAMA,

            // 有声书平台/播客/电台
            "audio" to ContentType.AUDIO,
            "sound" to ContentType.AUDIO,
            "voice" to ContentType.AUDIO,
            "podcast" to ContentType.AUDIO,
            "radio" to ContentType.AUDIO,
            "listen" to ContentType.AUDIO,
            "听书" to ContentType.AUDIO,
            "有声" to ContentType.AUDIO,

            // 音乐平台
            "music" to ContentType.MUSIC,
            "song" to ContentType.MUSIC,
            "album" to ContentType.MUSIC,
            "mv" to ContentType.MUSIC,
            "音乐" to ContentType.MUSIC,
            // 漫画平台
            "comic" to ContentType.IMAGE,
            "manga" to ContentType.IMAGE,
            "manhua" to ContentType.IMAGE,
            "漫画" to ContentType.IMAGE
        )

        sourceKeywords.forEach { (keyword, type) ->
            if (sourceText.contains(keyword)) {
                scores[type] = scores.getOrDefault(type, 0) + (6 * sourceWeight).toInt()
            }
        }
    }


    /**
     * 额外：根据 URL 后缀/媒体清单判定音视频
     */
    private fun analyzeUrls(book: SearchBook, bookSource: BookSource, scores: MutableMap<ContentType, Int>) {
        val urlBlob = (book.bookUrl + " " + book.tocUrl + " " + bookSource.bookSourceUrl).lowercase()
        val audioExt = listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".wav", ".ape")
        val videoExt = listOf(".mp4", ".m3u8", ".webm", ".ts", ".mpd", "/dash/")
        if (audioExt.any { urlBlob.contains(it) }) {
            scores[ContentType.MUSIC] = scores.getOrDefault(ContentType.MUSIC, 0) + 25
        }
        if (videoExt.any { urlBlob.contains(it) }) {
            scores[ContentType.DRAMA] = scores.getOrDefault(ContentType.DRAMA, 0) + 25
        }
    }

    /**


     * 额外：检测 SxxExx、第x季第x集 等模式
     */
    private fun detectSeasonEpisodePattern(name: String, kind: String, intro: String, scores: MutableMap<ContentType, Int>) {
        val text = "$name $kind $intro"
        val regexes = listOf(
            Regex("S[0-9]{1,2}E[0-9]{1,3}", RegexOption.IGNORE_CASE),
            Regex("第[一二三四五六七八九十0-9]+季"),
            Regex("第[一二三四五六七八九十0-9]+集")
        )
        if (regexes.any { it.containsMatchIn(text) }) {
            val prev = scores.getOrDefault(ContentType.DRAMA, 0)
            scores[ContentType.DRAMA] = prev + 15
        }
    }


    /**
     * 额外：检测是否包含视频时长模式
     */
    private fun detectDurationPattern(name: String, kind: String, intro: String, wordCount: String?, scores: MutableMap<ContentType, Int>) {
        val text = "$name $kind $intro ${wordCount ?: ""}"
        // 匹配 06:13 / 1:23:45 等
        val regexes = listOf(
            Regex("\\b[0-9]{1,2}:[0-9]{2}\\b"),
            Regex("\\b[0-9]{1,2}:[0-9]{2}:[0-9]{2}\\b")
        )
        if (regexes.any { it.containsMatchIn(text) }) {
            // 仅当视频信号存在时才给 DRAMA 加分，避免音乐 mm:ss 误判
            val dramaPrior = scores.getOrDefault(ContentType.DRAMA, 0)
            val videoSignal = dramaPrior >= 10 // 先前因视频关键词/域名得到的一些分
            if (videoSignal) {
                scores[ContentType.DRAMA] = dramaPrior + 12
            } else {
                // 若无视频信号，而音乐已具备较高分，则给 MUSIC 少量加分
                val musicPrev = scores.getOrDefault(ContentType.MUSIC, 0)
                scores[ContentType.MUSIC] = musicPrev + 6
            }
        }
    }

    /**
     * 应用关键词检测
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
     * 综合判断最终类型 - 优化版本
     * 优先使用书源类型标记，减少误判
     */
    private fun makeFinalDecision(
        sourceTypeHint: ContentType?,
        analysisResult: Map<ContentType, Int>,
        book: SearchBook,
        bookSource: BookSource
    ): ContentType {
        // 首先检查是否为成人内容（集中化）
        if (io.stillpage.app.help.AdultContentFilter.isAdultContent(book, bookSource)) {
            AppLog.put("检测到成人内容: ${book.name}，归类为TEXT类型")
            return ContentType.TEXT
        }

        // 如果书源有明确类型标记，优先使用书源类型
        if (sourceTypeHint != null && sourceTypeHint != ContentType.TEXT) {
            // 只有当内容分析结果非常明确且与书源类型冲突时，才忽略书源类型
            val maxScore = analysisResult.maxByOrNull { it.value }
            if (maxScore != null && maxScore.value >= 30 && maxScore.key != sourceTypeHint) {
                // 内容分析结果非常强烈且与书源类型冲突，记录日志并采用内容分析结果
                AppLog.put("内容类型冲突: 书源标记为${sourceTypeHint}，但内容分析强烈指示为${maxScore.key}，采用内容分析结果")
                return maxScore.key
            }
            // 否则优先采用书源类型
            return sourceTypeHint
        }

        // 如果书源没有类型标记，使用传统的分析逻辑
        if (analysisResult.isEmpty()) {
            return ContentType.TEXT
        }

        val maxScore = analysisResult.maxByOrNull { it.value }

        // 提高判断阈值，减少误判
        if (maxScore != null && maxScore.value >= 20) {
            return maxScore.key
        }

        // 分数不够高时，返回默认类型
        return ContentType.TEXT
    }

    /**
     * 检测是否为成人内容
     */
    // 成人内容判定逻辑已集中到 AdultContentFilter
}

package io.stillpage.app.help

import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType

/**
 * 关键词轻量识别工具
 * 将旧发现页和管理列表中的简化内容类型识别统一到此处
 */
object ContentTypeHeuristics {

    /**
     * 基于名称与URL的轻量识别（管理列表 BookSourcePart 使用）
     */
    fun detectByNameAndUrl(name: String?, url: String?): ContentType {
        val info = ((name ?: "") + " " + (url ?: "")).lowercase()

        // 平台/域名覆盖（与统一检测器保持一致）
        run {
            val domainMap = mapOf(
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
            val hit = domainMap.entries.firstOrNull { (k, _) -> info.contains(k) }
            if (hit != null) return hit.value
        }

        return when {
            listOf("漫画", "comic", "manga", "manhua").any { info.contains(it) } -> ContentType.IMAGE
            listOf("有声", "audio", "听书", "podcast", "radio", "sound", "voice").any { info.contains(it) } -> ContentType.AUDIO
            listOf("音乐", "music", "song", "album", "mv").any { info.contains(it) } -> ContentType.MUSIC
            listOf("短剧", "drama", "video", "movie", "film", "tv", "vod", "watch", "play", "series", "episode", "season").any { info.contains(it) } -> ContentType.DRAMA
            info.contains("file") -> ContentType.FILE
            else -> ContentType.TEXT
        }
    }

    /**
     * 基于 SearchBook 文本与URL特征的轻量识别（旧发现页回退使用）
     */
    fun detectBasicFromBook(book: SearchBook): ContentType {
        val name = book.name.lowercase()
        val kind = (book.kind ?: "").lowercase()
        val intro = (book.intro ?: "").lowercase()
        val urls = (book.bookUrl + " " + (book.tocUrl ?: "") + " " + (book.coverUrl ?: "")).lowercase()

        // 漫画/图片类
        val imageHits = listOf("漫画", "连环画", "绘本", "comic", "manga", "manhua", "动漫")
        if (imageHits.any { name.contains(it) || kind.contains(it) || intro.contains(it) }) {
            return ContentType.IMAGE
        }

        // 有声书/音频类（避免与音乐冲突，优先命中有声书关键词）
        val audioHits = listOf("有声书", "听书", "播讲", "朗读", "广播剧", "podcast", "电台", "radio", "audio")
        if (audioHits.any { name.contains(it) || kind.contains(it) || intro.contains(it) }) {
            return ContentType.AUDIO
        }

        // 音乐类：关键词或音频后缀
        val musicHits = listOf("音乐", "歌曲", "专辑", "单曲", "mv", "music", "song", "album")
        val audioExt = listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".wav", ".ape")
        if (musicHits.any { name.contains(it) || kind.contains(it) || intro.contains(it) }) {
            return ContentType.MUSIC
        }
        if (audioExt.any { urls.contains(it) }) {
            // 带音频后缀但未命中有声书关键词，更可能是音乐
            return ContentType.MUSIC
        }

        // 短剧/影视类：关键词或明显的视频标记
        val dramaHits = listOf("短剧", "影视", "电影", "电视剧", "drama", "video", "movie", "film", "tv", "vod", "watch")
        if (dramaHits.any { name.contains(it) || kind.contains(it) || intro.contains(it) || urls.contains(it) }) {
            return ContentType.DRAMA
        }

        // 文件类：明显的文件描述或后缀
        val fileHits = listOf("pdf", "epub", "mobi", "azw", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
        val fileExt = listOf(".pdf", ".epub", ".mobi", ".azw", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx")
        if (fileHits.any { urls.contains(it) || intro.contains(it) }) {
            return ContentType.FILE
        }
        if (fileExt.any { urls.contains(it) }) {
            return ContentType.FILE
        }

        return ContentType.TEXT
    }
}
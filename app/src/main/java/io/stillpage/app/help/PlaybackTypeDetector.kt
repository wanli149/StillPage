package io.stillpage.app.help

import io.stillpage.app.data.entities.Book

/**
 * 播放形态检测：用于判断音乐内容是否为 MV（需要视频播放）。
 * 注意：不改变内容分类（ContentType 仍为 MUSIC），仅用于路由到合适的播放方式。
 */
object PlaybackTypeDetector {

    /**
     * 判定是否为 MV（音乐视频）。
     * 依据：标题/分类/简介中的 MV/音乐视频/music video 等关键词。
     * 如后续需要更强覆盖，可在此加入 URL 后缀/播放清单（.mp4/.m3u8/.webm/.ts 等）的判断。
     */
    fun isMusicVideo(book: Book): Boolean {
        val text = buildString {
            append(book.name)
            append(' ')
            append(book.kind ?: "")
            append(' ')
            append(book.intro ?: "")
        }.lowercase()

        val keywords = listOf(
            // 常见 MV 文案
            "mv", "音乐视频", "music video", "mv版", "视频版",
            // 发行/版本相关
            "官方版", "现场版", "live"
        )
        if (keywords.any { text.contains(it) }) return true

        // 可选：若目录或详情链接显式为媒体格式，可进一步提升置信度
        val urlText = (book.bookUrl + " " + book.tocUrl).lowercase()
        val videoHints = listOf(".mp4", ".m3u8", ".webm", ".ts", ".mpd")
        if (videoHints.any { urlText.contains(it) }) return true

        return false
    }
}


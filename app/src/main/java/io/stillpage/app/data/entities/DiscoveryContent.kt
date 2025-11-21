package io.stillpage.app.data.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 发现内容数据模型
 */
@Parcelize
data class DiscoveryContent(
    val id: String,                    // 唯一标识
    val title: String,                 // 标题
    val author: String,                // 作者
    val coverUrl: String?,             // 封面URL
    val contentType: ContentType,      // 内容类型
    val sourceUrl: String,             // 书源URL
    val detailUrl: String,             // 详情页URL
    val intro: String? = null,         // 简介
    val latestChapter: String? = null, // 最新章节
    val updateTime: String? = null,    // 更新时间
    val wordCount: String? = null,     // 字数
    val status: String? = null,        // 状态（连载/完结）
    val tags: List<String>? = null,    // 标签
    val rating: Float? = null,         // 评分
    val isInBookshelf: Boolean = false // 是否已在书架
) : Parcelable {
    
    /**
     * 获取显示用的封面
     */
    fun getDisplayCover(): String? {
        return coverUrl?.takeIf { it.isNotBlank() }
    }
    
    /**
     * 获取显示用的作者信息
     */
    fun getDisplayAuthor(): String {
        return author.ifBlank { "未知作者" }
    }
    
    /**
     * 获取显示用的简介
     */
    fun getDisplayIntro(): String {
        return intro?.takeIf { it.isNotBlank() } ?: "暂无简介"
    }
    
    /**
     * 获取显示用的最新章节
     */
    fun getDisplayLatestChapter(): String? {
        return latestChapter?.takeIf { it.isNotBlank() }
    }
    
    /**
     * 获取显示用的状态信息
     */
    fun getDisplayStatus(): String? {
        return status?.takeIf { it.isNotBlank() }
    }
    
    /**
     * 获取显示用的标签
     */
    fun getDisplayTags(): String? {
        return tags?.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

/**
 * 内容类型枚举
 */
@Parcelize
enum class ContentType(val displayName: String, val code: String) : Parcelable {
    BOOK("书籍", "book"),
    COMIC("漫画", "comic"),
    AUDIO("有声", "audio"),
    MUSIC("音乐", "music");
    
    companion object {
        fun fromCode(code: String): ContentType {
            return values().find { it.code == code } ?: BOOK
        }
        
        fun getAllTypes(): List<ContentType> {
            return values().toList()
        }
    }
}

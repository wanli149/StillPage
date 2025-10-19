package io.stillpage.app.ui.common

import io.stillpage.app.R
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType

object ContentTypeUi {
    fun label(type: ContentType): String = type.displayName

    fun backgroundRes(type: ContentType): Int = when (type) {
        ContentType.TEXT -> R.color.content_type_text
        ContentType.AUDIO -> R.color.content_type_audio
        ContentType.IMAGE -> R.color.content_type_image
        ContentType.MUSIC -> R.color.content_type_music
        ContentType.DRAMA -> R.color.content_type_drama
        ContentType.FILE -> R.color.content_type_file
        else -> R.color.content_type_text
    }
    
    /**
     * 智能检测类型的浅色背景（用于区分手动标记和智能检测）
     */
    fun backgroundResLight(type: ContentType): Int = when (type) {
        ContentType.TEXT -> R.color.content_type_text_light
        ContentType.AUDIO -> R.color.content_type_audio_light
        ContentType.IMAGE -> R.color.content_type_image_light
        ContentType.MUSIC -> R.color.content_type_music_light
        ContentType.DRAMA -> R.color.content_type_drama_light
        ContentType.FILE -> R.color.content_type_file_light
        else -> R.color.content_type_text_light
    }
}
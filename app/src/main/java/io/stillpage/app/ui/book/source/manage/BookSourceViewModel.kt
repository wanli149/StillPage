package io.stillpage.app.ui.book.source.manage

import android.app.Application
import android.text.TextUtils
import io.stillpage.app.R
import io.stillpage.app.base.BaseViewModel
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.BookSourcePart
import io.stillpage.app.data.entities.toBookSource
import io.stillpage.app.help.source.SourceHelp
import io.stillpage.app.utils.FileUtils
import io.stillpage.app.utils.GSON
import io.stillpage.app.utils.cnCompare
import io.stillpage.app.utils.outputStream
import io.stillpage.app.utils.splitNotBlank
import io.stillpage.app.utils.stackTraceStr
import io.stillpage.app.utils.toastOnUi
import io.stillpage.app.utils.writeToOutputStream
import splitties.init.appCtx
import java.io.File

/**
 * 书源管理数据修改
 * 修改数据要copy,直接修改会导致界面不刷新
 */
class BookSourceViewModel(application: Application) : BaseViewModel(application) {

    fun topSource(vararg sources: BookSourcePart) {
        execute {
            sources.sortBy { it.customOrder }
            val minOrder = appDb.bookSourceDao.minOrder - 1
            val array = sources.mapIndexed { index, it ->
                it.copy(customOrder = minOrder - index)
            }
            appDb.bookSourceDao.upOrder(array)
        }
    }

    fun bottomSource(vararg sources: BookSourcePart) {
        execute {
            sources.sortBy { it.customOrder }
            val maxOrder = appDb.bookSourceDao.maxOrder + 1
            val array = sources.mapIndexed { index, it ->
                it.copy(customOrder = maxOrder + index)
            }
            appDb.bookSourceDao.upOrder(array)
        }
    }

    fun del(sources: List<BookSourcePart>) {
        execute {
            SourceHelp.deleteBookSourceParts(sources)
        }
    }

    fun update(vararg bookSource: BookSource) {
        execute { appDb.bookSourceDao.update(*bookSource) }
    }
    
    /**
     * 根据内容类型自动分组书源
     */
    fun autoGroupByContentType() {
        execute {
            val allSources = appDb.bookSourceDao.all
            val updatedSources = mutableListOf<BookSource>()
            var groupedCount = 0
            
            allSources.forEach { source ->
                // 优先使用用户手动设置的内容类型，然后是系统检测的，最后是基于关键词检测的
                val contentType = source.contentTypeOverride 
                    ?: source.contentTypeHint 
                    ?: detectContentTypeFromSource(source)
                
                val targetGroup = getContentTypeGroup(contentType)
                
                // 只对有明确内容类型且当前未在对应分组中的书源进行分组
                if (targetGroup != null && !source.hasGroup(targetGroup) && 
                    (contentType != "TEXT" || hasSpecialKeywords(source))) {
                    val updatedSource = source.copy()
                    updatedSource.addGroup(targetGroup)
                    updatedSources.add(updatedSource)
                    groupedCount++
                }
            }
            
            if (updatedSources.isNotEmpty()) {
                appDb.bookSourceDao.update(*updatedSources.toTypedArray())
                context.toastOnUi("已自动分组 $groupedCount 个书源")
            } else {
                context.toastOnUi("没有需要自动分组的书源")
            }
        }
    }
    
    /**
     * 检查是否有特殊关键词（用于TEXT类型的进一步判断）
     */
    private fun hasSpecialKeywords(source: BookSource): Boolean {
        val name = source.bookSourceName.lowercase()
        val url = source.bookSourceUrl.lowercase()
        
        val specialKeywords = listOf(
            "有声", "听书", "audio", "podcast", "radio",
            "漫画", "comic", "manga", "音乐", "music", 
            "短剧", "drama", "video", "movie"
        )
        
        return specialKeywords.any { keyword ->
            name.contains(keyword) || url.contains(keyword)
        }
    }
    
    /**
     * 移动书源到指定分组
     */
    fun moveSourceToGroup(sources: List<BookSourcePart>, targetGroup: String) {
        execute {
            val updatedSources = sources.map { sourcePart ->
                val fullSource = appDb.bookSourceDao.getBookSource(sourcePart.bookSourceUrl)
                fullSource?.let { source ->
                    // 移除所有内容类型分组
                    val contentTypeGroups = listOf("小说", "听书", "音乐", "短剧", "漫画")
                    contentTypeGroups.forEach { group ->
                        source.removeGroup(group)
                    }
                    // 添加到新分组
                    source.addGroup(targetGroup)
                    source
                }
            }.filterNotNull()
            
            if (updatedSources.isNotEmpty()) {
                appDb.bookSourceDao.update(*updatedSources.toTypedArray())
            }
        }
    }
    
    private fun detectContentTypeFromSource(source: BookSource): String {
        val name = source.bookSourceName.lowercase()
        val url = source.bookSourceUrl.lowercase()
        
        return when {
            // 音频类关键词
            name.contains("有声") || name.contains("听书") || 
            name.contains("audio") || name.contains("podcast") || name.contains("radio") ||
            url.contains("audio") || url.contains("podcast") || url.contains("radio") -> "AUDIO"
            
            // 图片/漫画类关键词  
            name.contains("漫画") || name.contains("连环画") || name.contains("绘本") ||
            name.contains("comic") || name.contains("manga") ||
            url.contains("comic") || url.contains("manga") -> "IMAGE"
            
            // 音乐类关键词
            name.contains("音乐") || name.contains("music") || name.contains("song") ||
            name.contains("album") || name.contains("mv") ||
            url.contains("music") || url.contains("song") || url.contains("album") || url.contains("mv") -> "MUSIC"
            
            // 短剧/视频类关键词
            name.contains("短剧") || name.contains("drama") || name.contains("video") ||
            name.contains("movie") || name.contains("film") || name.contains("tv") ||
            name.contains("vod") || name.contains("watch") || name.contains("play") ||
            name.contains("series") || name.contains("episode") || name.contains("season") ||
            url.contains("drama") || url.contains("video") || url.contains("movie") ||
            url.contains("film") || url.contains("tv") || url.contains("vod") ||
            url.contains("watch") || url.contains("play") || url.contains("series") ||
            url.contains("episode") || url.contains("season") -> "DRAMA"
            
            // 文件类关键词
            name.contains("file") || url.contains("file") ||
            url.contains(".pdf") || url.contains(".epub") || url.contains(".mobi") ||
            url.contains(".azw") || url.contains(".doc") || url.contains(".xls") ||
            url.contains(".ppt") -> "FILE"
            
            // 默认为文本
            else -> "TEXT"
        }
    }
    


    fun upOrder(items: List<BookSourcePart>) {
        if (items.isEmpty()) return
        execute {
            appDb.bookSourceDao.upOrder(items)
        }
    }

    fun enable(enable: Boolean, items: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enable(enable, items)
        }
    }

    fun enableSelection(sources: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enable(true, sources)
        }
    }

    fun disableSelection(sources: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enable(false, sources)
        }
    }

    fun enableExplore(enable: Boolean, items: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enableExplore(enable, items)
        }
    }

    fun enableSelectExplore(sources: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enableExplore(true, sources)
        }
    }

    fun disableSelectExplore(sources: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enableExplore(false, sources)
        }
    }

    fun selectionAddToGroups(sources: List<BookSourcePart>, groups: String) {
        execute {
            val array = sources.map {
                it.copy().apply {
                    addGroup(groups)
                }
            }
            appDb.bookSourceDao.upGroup(array)
        }
    }

    fun selectionRemoveFromGroups(sources: List<BookSourcePart>, groups: String) {
        execute {
            val array = sources.map {
                it.copy().apply {
                    removeGroup(groups)
                }
            }
            appDb.bookSourceDao.upGroup(array)
        }
    }

    private fun saveToFile(sources: List<BookSource>, success: (file: File) -> Unit) {
        execute {
            val path = "${context.filesDir}/shareBookSource.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.outputStream().buffered().use {
                GSON.writeToOutputStream(it, sources)
            }
            file
        }.onSuccess {
            success.invoke(it)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun saveToFile(
        adapter: BookSourceAdapter,
        searchKey: String?,
        sortAscending: Boolean,
        sort: BookSourceSort,
        success: (file: File) -> Unit
    ) {
        execute {
            val selection = adapter.selection
            val selectedRate = selection.size.toFloat() / adapter.itemCount.toFloat()
            val sources = if (selectedRate == 1f) {
                getBookSources(searchKey, sortAscending, sort)
            } else if (selectedRate < 0.3) {
                selection.toBookSource()
            } else {
                val keys = selection.map { it.bookSourceUrl }.toHashSet()
                val bookSources = getBookSources(searchKey, sortAscending, sort)
                bookSources.filter {
                    keys.contains(it.bookSourceUrl)
                }
            }
            saveToFile(sources, success)
        }
    }

    private fun getBookSources(
        searchKey: String?,
        sortAscending: Boolean,
        sort: BookSourceSort
    ): List<BookSource> {
        return when {
            searchKey.isNullOrEmpty() -> {
                appDb.bookSourceDao.all
            }

            searchKey == appCtx.getString(R.string.enabled) -> {
                appDb.bookSourceDao.allEnabled
            }

            searchKey == appCtx.getString(R.string.disabled) -> {
                appDb.bookSourceDao.allDisabled
            }

            searchKey == appCtx.getString(R.string.need_login) -> {
                appDb.bookSourceDao.allLogin
            }

            searchKey == appCtx.getString(R.string.no_group) -> {
                appDb.bookSourceDao.allNoGroup
            }

            searchKey == appCtx.getString(R.string.enabled_explore) -> {
                appDb.bookSourceDao.allEnabledExplore
            }

            searchKey == appCtx.getString(R.string.disabled_explore) -> {
                appDb.bookSourceDao.allDisabledExplore
            }

            searchKey.startsWith("group:") -> {
                val key = searchKey.substringAfter("group:")
                appDb.bookSourceDao.groupSearch(key)
            }

            else -> {
                appDb.bookSourceDao.search(searchKey)
            }
        }.let { data ->
            if (sortAscending) when (sort) {
                BookSourceSort.Weight -> data.sortedBy { it.weight }
                BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                    o1.bookSourceName.cnCompare(o2.bookSourceName)
                }

                BookSourceSort.Url -> data.sortedBy { it.bookSourceUrl }
                BookSourceSort.Update -> data.sortedByDescending { it.lastUpdateTime }
                BookSourceSort.Respond -> data.sortedBy { it.respondTime }
                BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                    var sortNum = -o1.enabled.compareTo(o2.enabled)
                    if (sortNum == 0) {
                        sortNum = o1.bookSourceName.cnCompare(o2.bookSourceName)
                    }
                    sortNum
                }

                else -> data
            }
            else when (sort) {
                BookSourceSort.Weight -> data.sortedByDescending { it.weight }
                BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                    o2.bookSourceName.cnCompare(o1.bookSourceName)
                }

                BookSourceSort.Url -> data.sortedByDescending { it.bookSourceUrl }
                BookSourceSort.Update -> data.sortedBy { it.lastUpdateTime }
                BookSourceSort.Respond -> data.sortedByDescending { it.respondTime }
                BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                    var sortNum = o1.enabled.compareTo(o2.enabled)
                    if (sortNum == 0) {
                        sortNum = o1.bookSourceName.cnCompare(o2.bookSourceName)
                    }
                    sortNum
                }

                else -> data.reversed()
            }
        }
    }

    fun addGroup(group: String) {
        execute {
            val sources = appDb.bookSourceDao.noGroup
            sources.forEach { source ->
                source.bookSourceGroup = group
            }
            appDb.bookSourceDao.update(*sources.toTypedArray())
        }
    }

    fun upGroup(oldGroup: String, newGroup: String?) {
        execute {
            val sources = appDb.bookSourceDao.getByGroup(oldGroup)
            sources.forEach { source ->
                source.bookSourceGroup?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(oldGroup)
                    if (!newGroup.isNullOrEmpty())
                        it.add(newGroup)
                    source.bookSourceGroup = TextUtils.join(",", it)
                }
            }
            appDb.bookSourceDao.update(*sources.toTypedArray())
        }
    }

    fun delGroup(group: String) {
        execute {
            val sources = appDb.bookSourceDao.getByGroup(group)
            sources.forEach { source ->
                source.removeGroup(group)
            }
            appDb.bookSourceDao.update(*sources.toTypedArray())
        }
    }

    /**
     * 删除分组（用于分组管理对话框）
     */
    suspend fun deleteGroup(groupName: String) {
        val sources = appDb.bookSourceDao.getByGroup(groupName)
        sources.forEach { source ->
            source.removeGroup(groupName)
        }
        appDb.bookSourceDao.update(*sources.toTypedArray())
    }

    /**
     * 获取指定分组的书源数量
     */
    suspend fun getSourceCountByGroup(groupName: String): Int {
        return appDb.bookSourceDao.getByGroup(groupName).size
    }

    /**
     * 获取所有分组
     */
    suspend fun getAllGroups(): List<String> {
        return appDb.bookSourceDao.allGroups()
    }

    /**
     * 更新听书分组映射
     */
    private fun getContentTypeGroup(contentType: String?): String? {
        return when (contentType) {
            "TEXT", "FILE" -> "小说"
            "AUDIO" -> "听书"  // 听书单独分组
            "MUSIC" -> "音乐"
            "DRAMA" -> "短剧"
            "IMAGE" -> "漫画"
            else -> null
        }
    }

}
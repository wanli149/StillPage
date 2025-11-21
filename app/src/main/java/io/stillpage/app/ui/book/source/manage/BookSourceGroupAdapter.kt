package io.stillpage.app.ui.book.source.manage

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.stillpage.app.R
import io.stillpage.app.data.entities.BookSourcePart
import io.stillpage.app.databinding.ItemBookSourceBinding
import io.stillpage.app.databinding.ItemBookSourceGroupHeaderBinding
import io.stillpage.app.lib.theme.backgroundColor
import io.stillpage.app.model.Debug
import io.stillpage.app.ui.common.ContentTypeUi
import io.stillpage.app.ui.login.SourceLoginActivity
import io.stillpage.app.ui.widget.recycler.ItemTouchCallback
import io.stillpage.app.utils.ColorUtils
import io.stillpage.app.utils.gone
import io.stillpage.app.utils.invisible
import io.stillpage.app.utils.startActivity
import io.stillpage.app.utils.visible

/**
 * 按内容类型分组折叠的书源适配器
 */
class BookSourceGroupAdapter(
    private val context: Context,
    private val callBack: CallBack
) : ListAdapter<BookSourceGroupAdapter.GroupItem, RecyclerView.ViewHolder>(DiffCallback()),
    ItemTouchCallback.Callback {

    companion object {
        private const val TYPE_GROUP_HEADER = 0
        private const val TYPE_BOOK_SOURCE = 1
        
        // 内容类型分组映射
        private val CONTENT_TYPE_GROUPS = mapOf(
            "TEXT" to "小说",
            "AUDIO" to "听书",
            "MUSIC" to "音乐",
            "DRAMA" to "短剧",
            "IMAGE" to "漫画",
            "FILE" to "文件"
        )
        
        // 分组显示顺序
        private val GROUP_ORDER = listOf("小说", "听书", "音乐", "短剧", "漫画", "文件")
    }

    private val selected = linkedSetOf<BookSourcePart>()
    private val collapsedGroups = mutableSetOf<String>()
    private val finalMessageRegex = Regex("成功|失败")

    val selection: List<BookSourcePart>
        get() = currentList.filterIsInstance<GroupItem.SourceItem>()
            .map { it.source }
            .filter { selected.contains(it) }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GroupItem.GroupHeader -> TYPE_GROUP_HEADER
            is GroupItem.SourceItem -> TYPE_BOOK_SOURCE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_GROUP_HEADER -> {
                val binding = ItemBookSourceGroupHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                GroupHeaderViewHolder(binding)
            }
            TYPE_BOOK_SOURCE -> {
                val binding = ItemBookSourceBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SourceViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GroupItem.GroupHeader -> {
                (holder as GroupHeaderViewHolder).bind(item)
            }
            is GroupItem.SourceItem -> {
                (holder as SourceViewHolder).bind(item.source)
            }
        }
    }

    /**
     * 更新书源列表，按内容类型分组组织
     */
    fun updateSources(sources: List<BookSourcePart>) {
        val groupedSources = sources.groupBy { getContentTypeGroup(it) }
        val items = mutableListOf<GroupItem>()

        // 按预定义顺序添加分组
        GROUP_ORDER.forEach { groupName ->
            val groupSources = groupedSources[groupName] ?: emptyList()
            if (groupSources.isNotEmpty()) {
                // 添加分组头部
                items.add(GroupItem.GroupHeader(groupName, groupSources.size, !collapsedGroups.contains(groupName)))
                
                // 如果分组未折叠，添加书源项
                if (!collapsedGroups.contains(groupName)) {
                    groupSources.forEach { source ->
                        items.add(GroupItem.SourceItem(source))
                    }
                }
            }
        }

        submitList(items)
    }

    /**
     * 切换分组折叠状态
     */
    fun toggleGroup(groupName: String) {
        if (collapsedGroups.contains(groupName)) {
            // 当前是折叠状态，点击后展开
            collapsedGroups.remove(groupName)
        } else {
            // 当前是展开状态，点击后折叠
            collapsedGroups.add(groupName)
        }
        
        // 重新构建列表，保持原始数据
        val allSources = callBack.getAllSources()
        updateSources(allSources)
    }

    /**
     * 根据书源获取内容类型分组名称
     */
    private fun getContentTypeGroup(source: BookSourcePart): String {
        val type = io.stillpage.app.help.ContentTypeResolver.resolveFromPart(source)
        return CONTENT_TYPE_GROUPS[type.name] ?: "小说"
    }

    /**
     * 分组头部ViewHolder
     */
    inner class GroupHeaderViewHolder(
        private val binding: ItemBookSourceGroupHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: GroupItem.GroupHeader) {
            binding.apply {
                tvGroupName.text = header.groupName
                tvSourceCount.text = "${header.sourceCount}个书源"
                
                // 根据展开状态设置图标旋转
                ivExpand.rotation = if (header.isExpanded) 180f else 0f
                
                root.setOnClickListener {
                    toggleGroup(header.groupName)
                }
            }
        }
    }

    /**
     * 书源项ViewHolder
     */
    inner class SourceViewHolder(
        private val binding: ItemBookSourceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(source: BookSourcePart) {
            binding.run {
                root.setBackgroundColor(ColorUtils.withAlpha(context.backgroundColor, 0.5f))
                cbBookSource.text = source.getDisPlayNameGroup()
                swtEnabled.isChecked = source.enabled
                cbBookSource.isChecked = selected.contains(source)
                upCheckSourceMessage(binding, source)
                upShowExplore(ivExplore, source)
                upContentTypeLabel(tvContentType, source)

                // 设置监听器
                swtEnabled.setOnCheckedChangeListener { view, checked ->
                    if (view.isPressed) {
                        source.enabled = checked
                        callBack.enable(checked, source)
                    }
                }
                
                cbBookSource.setOnCheckedChangeListener { view, checked ->
                    if (view.isPressed) {
                        if (checked) {
                            selected.add(source)
                        } else {
                            selected.remove(source)
                        }
                        callBack.upCountView()
                    }
                }
                
                ivEdit.setOnClickListener {
                    callBack.edit(source)
                }
                
                ivMenuMore.setOnClickListener {
                    showMenu(ivMenuMore, source)
                }
            }
        }

        private fun showMenu(view: View, source: BookSourcePart) {
            val popupMenu = PopupMenu(context, view)
            popupMenu.inflate(R.menu.book_source_item)
            popupMenu.menu.findItem(R.id.menu_top).isVisible = callBack.sort == BookSourceSort.Default
            popupMenu.menu.findItem(R.id.menu_bottom).isVisible = callBack.sort == BookSourceSort.Default
            
            val qyMenu = popupMenu.menu.findItem(R.id.menu_enable_explore)
            if (!source.hasExploreUrl) {
                qyMenu.isVisible = false
            } else {
                if (source.enabledExplore) {
                    qyMenu.setTitle(R.string.disable_explore)
                } else {
                    qyMenu.setTitle(R.string.enable_explore)
                }
            }
            
            val loginMenu = popupMenu.menu.findItem(R.id.menu_login)
            loginMenu.isVisible = source.hasLoginUrl
            
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_top -> callBack.toTop(source)
                    R.id.menu_bottom -> callBack.toBottom(source)
                    R.id.menu_login -> context.startActivity<SourceLoginActivity> {
                        putExtra("type", "bookSource")
                        putExtra("key", source.bookSourceUrl)
                    }
                    R.id.menu_search -> callBack.searchBook(source)
                    R.id.menu_debug_source -> callBack.debug(source)
                    R.id.menu_del -> {
                        callBack.del(source)
                        selected.remove(source)
                    }
                    R.id.menu_enable_explore -> {
                        callBack.enableExplore(!source.enabledExplore, source)
                    }
                    R.id.menu_move_to_novel -> callBack.moveToGroup(source, "小说")
                    R.id.menu_move_to_music -> callBack.moveToGroup(source, "音乐")
                    R.id.menu_move_to_drama -> callBack.moveToGroup(source, "短剧")
                    R.id.menu_move_to_manga -> callBack.moveToGroup(source, "漫画")
                }
                true
            }
            popupMenu.show()
        }

        private fun upShowExplore(iv: ImageView, source: BookSourcePart) {
            when {
                !source.hasExploreUrl -> {
                    iv.invisible()
                }
                source.enabledExplore -> {
                    iv.setColorFilter(Color.GREEN)
                    iv.visible()
                    iv.contentDescription = context.getString(R.string.tag_explore_enabled)
                }
                else -> {
                    iv.setColorFilter(Color.RED)
                    iv.visible()
                    iv.contentDescription = context.getString(R.string.tag_explore_disabled)
                }
            }
        }

        private fun upContentTypeLabel(tv: TextView, part: BookSourcePart) {
            val bookSource = part.getBookSource()
            val type = io.stillpage.app.help.ContentTypeResolver.resolveFromPart(part)
            
            val isManuallyTagged = bookSource?.contentTypeOverride != null
            
            tv.text = if (isManuallyTagged) {
                "${ContentTypeUi.label(type)}✓"
            } else {
                ContentTypeUi.label(type)
            }
            
            tv.visible()
            
            val backgroundColorRes = if (isManuallyTagged) {
                ContentTypeUi.backgroundRes(type)
            } else {
                ContentTypeUi.backgroundResLight(type)
            }
            
            kotlin.runCatching {
                val backgroundColor = ContextCompat.getColor(tv.context, backgroundColorRes)
                val drawable = ContextCompat.getDrawable(tv.context, R.drawable.shape_badge_bg)?.mutate()
                drawable?.setTint(backgroundColor)
                tv.background = drawable
            }
            
            tv.alpha = if (isManuallyTagged) 1.0f else 0.7f
            tv.setTextColor(ContextCompat.getColor(tv.context, android.R.color.white))
        }

        private fun upCheckSourceMessage(binding: ItemBookSourceBinding, item: BookSourcePart) = binding.run {
            val msg = Debug.debugMessageMap[item.bookSourceUrl] ?: ""
            ivDebugText.text = msg
            val isEmpty = msg.isEmpty()
            var isFinalMessage = msg.contains(finalMessageRegex)
            if (!Debug.isChecking && !isFinalMessage) {
                Debug.updateFinalMessage(item.bookSourceUrl, "校验失败")
                ivDebugText.text = Debug.debugMessageMap[item.bookSourceUrl] ?: ""
                isFinalMessage = true
            }
            ivDebugText.visibility = if (!isEmpty) View.VISIBLE else View.GONE
            ivProgressBar.visibility = if (isFinalMessage || isEmpty || !Debug.isChecking) View.GONE else View.VISIBLE
        }
    }

    /**
     * 分组项数据类
     */
    sealed class GroupItem {
        data class GroupHeader(val groupName: String, val sourceCount: Int, val isExpanded: Boolean) : GroupItem()
        data class SourceItem(val source: BookSourcePart) : GroupItem()
    }

    /**
     * DiffCallback
     */
    private class DiffCallback : DiffUtil.ItemCallback<GroupItem>() {
        override fun areItemsTheSame(oldItem: GroupItem, newItem: GroupItem): Boolean {
            return when {
                oldItem is GroupItem.GroupHeader && newItem is GroupItem.GroupHeader -> 
                    oldItem.groupName == newItem.groupName
                oldItem is GroupItem.SourceItem && newItem is GroupItem.SourceItem -> 
                    oldItem.source.bookSourceUrl == newItem.source.bookSourceUrl
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: GroupItem, newItem: GroupItem): Boolean {
            return oldItem == newItem
        }
    }

    // ItemTouchCallback.Callback 实现（简化版，分组模式下禁用拖拽）
    override fun swap(srcPosition: Int, targetPosition: Int): Boolean = false
    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {}

    interface CallBack {
        val sort: BookSourceSort
        val sortAscending: Boolean
        fun del(bookSource: BookSourcePart)
        fun edit(bookSource: BookSourcePart)
        fun toTop(bookSource: BookSourcePart)
        fun toBottom(bookSource: BookSourcePart)
        fun searchBook(bookSource: BookSourcePart)
        fun debug(bookSource: BookSourcePart)
        fun upOrder(items: List<BookSourcePart>)
        fun enable(enable: Boolean, bookSource: BookSourcePart)
        fun enableExplore(enable: Boolean, bookSource: BookSourcePart)
        fun upCountView()
        fun getSourceHost(origin: String): String
        fun moveToGroup(bookSource: BookSourcePart, group: String)
        fun getAllSources(): List<BookSourcePart>
    }
}
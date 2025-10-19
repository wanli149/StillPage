package io.stillpage.app.ui.book.source.manage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.stillpage.app.R
import io.stillpage.app.databinding.ItemBookSourceGroupManageBinding

/**
 * 分组管理适配器
 */
class GroupManageAdapter(
    private val onItemClick: (GroupActionItem) -> Unit
) : ListAdapter<GroupManageItem, GroupManageAdapter.ViewHolder>(DiffCallback()) {

    private val expandedGroups = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookSourceGroupManageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun toggleGroup(groupName: String) {
        if (expandedGroups.contains(groupName)) {
            expandedGroups.remove(groupName)
        } else {
            expandedGroups.add(groupName)
        }
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemBookSourceGroupManageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GroupManageItem) {
            binding.apply {
                tvGroupName.text = item.groupName
                tvSourceCount.text = "${item.sourceCount}个书源"
                
                // 内容类型分组显示指示点
                viewContentTypeIndicator.isVisible = item.isContentTypeGroup
                
                // 展开/折叠图标
                val isExpanded = expandedGroups.contains(item.groupName)
                ivExpand.rotation = if (isExpanded) 180f else 0f
                
                // 点击展开/折叠
                root.setOnClickListener {
                    onItemClick(GroupActionItem(item.groupName, Action.TOGGLE_EXPAND))
                }
                
                // 显示/隐藏按钮
                ivVisibility.setOnClickListener {
                    onItemClick(GroupActionItem(item.groupName, Action.TOGGLE_VISIBILITY))
                }
                
                // 删除按钮（内容类型分组不能删除）
                ivDelete.isVisible = !item.isContentTypeGroup
                ivDelete.setOnClickListener {
                    onItemClick(GroupActionItem(item.groupName, Action.DELETE_GROUP))
                }
            }
        }
    }

    enum class Action {
        TOGGLE_EXPAND,
        TOGGLE_VISIBILITY,
        DELETE_GROUP
    }

    data class GroupActionItem(
        val groupName: String,
        val action: Action
    )

    private class DiffCallback : DiffUtil.ItemCallback<GroupManageItem>() {
        override fun areItemsTheSame(oldItem: GroupManageItem, newItem: GroupManageItem): Boolean {
            return oldItem.groupName == newItem.groupName
        }

        override fun areContentsTheSame(oldItem: GroupManageItem, newItem: GroupManageItem): Boolean {
            return oldItem == newItem
        }
    }
}
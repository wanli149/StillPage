package io.stillpage.app.ui.book.source.manage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.stillpage.app.R
import io.stillpage.app.base.BaseDialogFragment
import io.stillpage.app.databinding.DialogGroupManageBinding
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

/**
 * 书源分组管理对话框
 */
class BookSourceGroupManageDialog : BaseDialogFragment(R.layout.dialog_group_manage) {
    
    private val binding by viewBinding(DialogGroupManageBinding::bind)
    private val viewModel by activityViewModels<BookSourceViewModel>()
    private lateinit var adapter: GroupManageAdapter
    
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initData()
    }
    
    private fun initView() {
        binding.apply {
            tvTitle.text = "分组管理"
            
            // 设置适配器
            adapter = GroupManageAdapter { groupItem ->
                when (groupItem.action) {
                    GroupManageAdapter.Action.TOGGLE_EXPAND -> {
                        // 切换展开/折叠状态
                        adapter.toggleGroup(groupItem.groupName)
                    }
                    GroupManageAdapter.Action.TOGGLE_VISIBILITY -> {
                        // 切换可见性
                        // TODO: 实现可见性切换逻辑
                    }
                    GroupManageAdapter.Action.DELETE_GROUP -> {
                        // 删除分组
                        lifecycleScope.launch {
                            viewModel.deleteGroup(groupItem.groupName)
                        }
                    }
                }
            }
            
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
            
            // 关闭按钮
            btnClose.setOnClickListener {
                dismiss()
            }
            
            // 添加分组按钮
            btnAddGroup.setOnClickListener {
                // TODO: 实现添加分组功能
            }
            
            // 自动分组按钮
            btnAutoGroup.setOnClickListener {
                lifecycleScope.launch {
                    viewModel.autoGroupByContentType()
                    // 刷新数据
                    initData()
                }
            }
        }
    }
    
    private fun initData() {
        lifecycleScope.launch {
            // 获取所有分组
            val allGroups = viewModel.getAllGroups()
            
            // 获取每个分组的书源数量
            val groupItems = allGroups.map { groupName ->
                val sourceCount = viewModel.getSourceCountByGroup(groupName)
                val isContentTypeGroup = isContentTypeGroup(groupName)
                GroupManageItem(
                    groupName = groupName,
                    sourceCount = sourceCount,
                    isExpanded = true, // 默认展开
                    isContentTypeGroup = isContentTypeGroup
                )
            }
            adapter.submitList(groupItems)
        }
    }
    
    private fun isContentTypeGroup(groupName: String): Boolean {
        return groupName in listOf("小说", "听书", "音乐", "短剧", "漫画")
    }
}

/**
 * 分组管理项数据类
 */
data class GroupManageItem(
    val groupName: String,
    val sourceCount: Int,
    val isExpanded: Boolean,
    val isContentTypeGroup: Boolean
)
package io.stillpage.app.ui.book.group

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.stillpage.app.R
import io.stillpage.app.base.BaseDialogFragment
import io.stillpage.app.base.adapter.ItemViewHolder
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.BookGroup
import io.stillpage.app.databinding.DialogBookGroupPickerBinding
import io.stillpage.app.databinding.ItemGroupSelectBinding
import io.stillpage.app.lib.theme.accentColor
import io.stillpage.app.lib.theme.backgroundColor
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.ui.widget.recycler.ItemTouchCallback
import io.stillpage.app.ui.widget.recycler.VerticalDivider
import io.stillpage.app.utils.applyTint
import io.stillpage.app.utils.setLayout
import io.stillpage.app.utils.showDialogFragment
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch


class GroupSelectDialog() : BaseDialogFragment(R.layout.dialog_book_group_picker),
    Toolbar.OnMenuItemClickListener {

    constructor(groupId: Long, requestCode: Int = -1) : this() {
        arguments = Bundle().apply {
            putLong("groupId", groupId)
            putInt("requestCode", requestCode)
        }
    }

    private val binding by viewBinding(DialogBookGroupPickerBinding::bind)
    private var requestCode: Int = -1
    private val viewModel: GroupViewModel by viewModels()
    private val adapter by lazy { GroupAdapter(requireContext()) }
    private val callBack get() = (activity as? CallBack)
    private var groupId: Long = 0

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        arguments?.let {
            groupId = it.getLong("groupId")
            requestCode = it.getInt("requestCode", -1)
        }
        initView()
        initData()
    }

    private fun initView() {
        binding.toolBar.title = getString(R.string.group_select)
        binding.toolBar.inflateMenu(R.menu.book_group_manage)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
        binding.tvCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.tvOk.setTextColor(requireContext().accentColor)
        binding.tvOk.setOnClickListener {
            callBack?.upGroup(requestCode, groupId)
            dismissAllowingStateLoss()
        }
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.bookGroupDao.flowSelect().conflate().collect {
                adapter.setItems(it)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> showDialogFragment(
                GroupEditDialog()
            )
        }
        return true
    }

    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<BookGroup, ItemGroupSelectBinding>(context),
        ItemTouchCallback.Callback {

        private var isMoved: Boolean = false

        override fun getViewBinding(parent: ViewGroup): ItemGroupSelectBinding {
            return ItemGroupSelectBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemGroupSelectBinding,
            item: BookGroup,
            payloads: MutableList<Any>
        ) {
            binding.run {
                root.setBackgroundColor(context.backgroundColor)
                cbGroup.text = item.groupName
                cbGroup.isChecked = (groupId and item.groupId) > 0
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemGroupSelectBinding) {
            binding.run {
                cbGroup.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (buttonView.isPressed) {
                        getItem(holder.layoutPosition)?.let {
                            groupId = if (isChecked) {
                                groupId + it.groupId
                            } else {
                                groupId - it.groupId
                            }
                        }
                    }
                }
                tvEdit.setOnClickListener {
                    showDialogFragment(
                        GroupEditDialog(getItem(holder.layoutPosition))
                    )
                }
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            swapItem(srcPosition, targetPosition)
            isMoved = true
            return true
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            if (isMoved) {
                for ((index, item) in getItems().withIndex()) {
                    item.order = index + 1
                }
                viewModel.upGroup(*getItems().toTypedArray())
            }
            isMoved = false
        }
    }

    interface CallBack {
        fun upGroup(requestCode: Int, groupId: Long)
    }
}
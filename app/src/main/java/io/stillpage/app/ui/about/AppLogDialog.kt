package io.stillpage.app.ui.about

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import io.stillpage.app.R
import io.stillpage.app.base.BaseDialogFragment
import io.stillpage.app.base.adapter.ItemViewHolder
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.constant.AppLog
import io.stillpage.app.databinding.DialogRecyclerViewBinding
import io.stillpage.app.databinding.ItemAppLogBinding
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.ui.widget.dialog.TextDialog
import io.stillpage.app.utils.LogUtils
import io.stillpage.app.utils.setLayout
import io.stillpage.app.utils.showDialogFragment
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import splitties.views.onClick
import java.util.*

class AppLogDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy {
        LogAdapter(requireContext())
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.setTitle(R.string.log)
            toolBar.inflateMenu(R.menu.app_log)
            toolBar.setOnMenuItemClickListener(this@AppLogDialog)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        adapter.setItems(AppLog.logs)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_clear -> {
                AppLog.clear()
                adapter.clearItems()
            }
        }
        return true
    }

    inner class LogAdapter(context: Context) :
        RecyclerAdapter<Triple<Long, String, Throwable?>, ItemAppLogBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemAppLogBinding {
            return ItemAppLogBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAppLogBinding,
            item: Triple<Long, String, Throwable?>,
            payloads: MutableList<Any>
        ) {
            binding.textTime.text = LogUtils.logTimeFormat.format(Date(item.first))
            binding.textMessage.text = item.second
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAppLogBinding) {
            binding.root.onClick {
                getItem(holder.layoutPosition)?.let { item ->
                    item.third?.let {
                        showDialogFragment(TextDialog("Log", it.stackTraceToString()))
                    }
                }
            }
        }

    }

}
package io.stillpage.app.ui.book.changecover

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import io.stillpage.app.R
import io.stillpage.app.base.BaseDialogFragment
import io.stillpage.app.databinding.DialogChangeCoverBinding
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.utils.applyTint
import io.stillpage.app.utils.setLayout
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * 换封面
 */
class ChangeCoverDialog() : BaseDialogFragment(R.layout.dialog_change_cover),
    Toolbar.OnMenuItemClickListener,
    CoverAdapter.CallBack {

    constructor(name: String, author: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
        }
    }

    private val binding by viewBinding(DialogChangeCoverBinding::bind)
    private val callBack: CallBack? get() = activity as? CallBack
    private val viewModel: ChangeCoverViewModel by viewModels()
    private val adapter by lazy { CoverAdapter(requireContext(), this) }

    private val startStopMenuItem: MenuItem?
        get() = binding.toolBar.menu.findItem(R.id.menu_start_stop)

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.change_cover_source)
        viewModel.initData(arguments)
        initMenu()
        initView()
        initData()
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.change_cover)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
    }

    private fun initView() {
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter
    }

    private fun initData() {
        lifecycleScope.launch {
            repeatOnLifecycle(STARTED) {
                viewModel.dataFlow.conflate().collect {
                    adapter.setItems(it)
                    delay(1000)
                }
            }
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        viewModel.searchStateData.observe(viewLifecycleOwner) {
            binding.refreshProgressBar.isAutoLoading = it
            if (it) {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_stop_black_24dp)
                    item.setTitle(R.string.stop)
                }
            } else {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_refresh_black_24dp)
                    item.setTitle(R.string.refresh)
                }
            }
            binding.toolBar.menu.applyTint(requireContext())
        }

    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_start_stop -> viewModel.startOrStopSearch()
        }
        return false
    }

    override fun changeTo(coverUrl: String) {
        callBack?.coverChangeTo(coverUrl)
        dismissAllowingStateLoss()
    }

    interface CallBack {
        fun coverChangeTo(coverUrl: String)
    }
}
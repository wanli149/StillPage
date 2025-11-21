package io.stillpage.app.ui.rss.source.debug

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.databinding.ActivitySourceDebugBinding
import io.stillpage.app.lib.theme.accentColor
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.ui.widget.dialog.TextDialog
import io.stillpage.app.utils.applyNavigationBarPadding
import io.stillpage.app.utils.gone
import io.stillpage.app.utils.setEdgeEffectColor
import io.stillpage.app.utils.showDialogFragment
import io.stillpage.app.utils.toastOnUi
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch


class RssSourceDebugActivity : VMBaseActivity<ActivitySourceDebugBinding, RssSourceDebugModel>() {

    override val binding by viewBinding(ActivitySourceDebugBinding::inflate)
    override val viewModel by viewModels<RssSourceDebugModel>()

    private val adapter by lazy { RssSourceDebugAdapter(this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        viewModel.observe { state, msg ->
            lifecycleScope.launch {
                adapter.addItem(msg)
                if (state == -1 || state == 1000) {
                    binding.rotateLoading.gone()
                }
            }
        }
        viewModel.initData(intent.getStringExtra("key")) {
            startDebug()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_source_debug, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_list_src -> showDialogFragment(TextDialog("Html", viewModel.listSrc))
            R.id.menu_content_src -> showDialogFragment(TextDialog("Html", viewModel.contentSrc))
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        binding.rotateLoading.loadingColor = accentColor
    }

    private fun initSearchView() {
        binding.titleBar.findViewById<SearchView>(R.id.search_view).gone()
    }

    private fun startDebug() {
        adapter.clearItems()
        viewModel.rssSource?.let {
            binding.rotateLoading.visible()
            viewModel.startDebug(it)
        } ?: toastOnUi(R.string.error_no_source)
    }
}
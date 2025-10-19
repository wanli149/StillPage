package io.stillpage.app.ui.book.explore

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.PreferKey
import io.stillpage.app.constant.BookType
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.help.book.isAudio
import io.stillpage.app.help.AdultContentFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.stillpage.app.databinding.ActivityExploreShowBinding
import io.stillpage.app.databinding.ViewLoadMoreBinding
import io.stillpage.app.ui.book.info.BookInfoActivity
import io.stillpage.app.utils.startBookInfoActivity
import io.stillpage.app.ui.widget.recycler.LoadMoreView
import io.stillpage.app.ui.widget.recycler.VerticalDivider
import io.stillpage.app.utils.applyNavigationBarPadding
import io.stillpage.app.utils.startActivity
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import io.stillpage.app.utils.observeEvent
import io.stillpage.app.help.config.AppConfig

/**
 * 发现列表
 */
class ExploreShowActivity : VMBaseActivity<ActivityExploreShowBinding, ExploreShowViewModel>(),
    ExploreShowAdapter.CallBack {
    override val binding by viewBinding(ActivityExploreShowBinding::inflate)
    override val viewModel by viewModels<ExploreShowViewModel>()

    private val adapter by lazy { ExploreShowAdapter(this, this) }
    private val loadMoreView by lazy { LoadMoreView(this) }
    private var lastBooks: List<SearchBook> = emptyList()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = intent.getStringExtra("exploreName")
        initRecyclerView()
        viewModel.booksData.observe(this) { upData(it) }
        viewModel.initData(intent)
        viewModel.errorLiveData.observe(this) {
            loadMoreView.error(it)
        }
        viewModel.upAdapterLiveData.observe(this) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, bundleOf(it to null))
        }

        // 监听成人内容开关变化，立即重算并刷新列表
        observeEvent<Boolean>(PreferKey.enableAdultContent) {
            val filtered = lastBooks.filterNot { sb -> AdultContentFilter.shouldFilter(sb) }
            val displayList = if (AppConfig.enableAdultContent) lastBooks else filtered
            adapter.setItems(displayList)
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        adapter.addFooterView {
            ViewLoadMoreBinding.bind(loadMoreView)
        }
        loadMoreView.startLoad()
        loadMoreView.setOnClickListener {
            if (!loadMoreView.isLoading) {
                scrollToBottom(true)
            }
        }
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!recyclerView.canScrollVertically(1)) {
                    scrollToBottom()
                }
            }
        })
    }

    private fun scrollToBottom(forceLoad: Boolean = false) {
        if ((loadMoreView.hasMore && !loadMoreView.isLoading) || forceLoad) {
            loadMoreView.hasMore()
            viewModel.explore()
        }
    }

    private fun upData(books: List<SearchBook>) {
        loadMoreView.stopLoad()
        // 记录最新数据，并应用成人内容过滤
        lastBooks = books
        val filtered = books.filterNot { sb -> AdultContentFilter.shouldFilter(sb) }
        val displayList = if (AppConfig.enableAdultContent) books else filtered

        if (displayList.isEmpty() && adapter.isEmpty()) {
            loadMoreView.noMore(getString(R.string.empty))
        } else if (adapter.getActualItemCount() == displayList.size) {
            loadMoreView.noMore()
        } else {
            adapter.setItems(displayList)
        }
    }

    override fun isInBookshelf(name: String, author: String): Boolean {
        return viewModel.isInBookShelf(name, author)
    }

    override fun showBookInfo(book: Book) {
        AppLog.put("发现页面：显示书籍详情 - ${book.name}")
        lifecycleScope.launch {
            startBookInfoActivity(book)
        }
    }

    override fun getBookSource(origin: String, onResult: (io.stillpage.app.data.entities.BookSource?) -> Unit) {
        lifecycleScope.launch(IO) {
            val source = kotlin.runCatching { appDb.bookSourceDao.getBookSource(origin) }.getOrNull()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(source)
            }
        }
    }
}

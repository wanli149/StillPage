package io.stillpage.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import io.stillpage.app.ui.widget.recycler.VerticalDivider
import io.stillpage.app.utils.observeEvent
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseFragment
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.PreferKey
import io.stillpage.app.constant.BookType
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.databinding.FragmentExploreNewBinding
import io.stillpage.app.lib.theme.accentColor
import io.stillpage.app.ui.book.audio.info.AudioBookInfoActivity
import io.stillpage.app.ui.book.info.BookInfoActivity

import io.stillpage.app.ui.book.music.info.MusicInfoActivity
import io.stillpage.app.ui.book.manga.info.MangaInfoActivity
import io.stillpage.app.ui.main.MainFragmentInterface
import io.stillpage.app.ui.widget.recycler.LoadMoreView
import io.stillpage.app.utils.startActivity
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExploreNewFragment() : VMBaseFragment<ExploreNewViewModel>(R.layout.fragment_explore_new),
    MainFragmentInterface,
    DiscoveryAdapterGrid.CallBack,
    DiscoveryAdapterList.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<ExploreNewViewModel>()
    private val binding by viewBinding(FragmentExploreNewBinding::bind)

    private val gridAdapter by lazy { DiscoveryAdapterGrid(requireContext(), this) }
    private val listAdapter by lazy { DiscoveryAdapterList(requireContext(), this) }
    private var isGridMode = true
    private val loadMoreView by lazy { LoadMoreView(requireContext()) }

    // 骨架屏适配器
    private var skeletonAdapter: io.stillpage.app.ui.widget.SkeletonAdapter? = null
    private var isShowingSkeleton = false

    // 新增：RecyclerView 是否已完成一次初始化（避免重复添加监听 / footer）
    private var recyclerInitialized = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initViews()
        initObservers()
        viewModel.loadDiscoveryData()
    }

    private fun initViews() {
        setupRecyclerView()
        setupContentTypeTabs()
        setupRefreshLayout()
        setupTitleBar()
    }

    private fun setupRecyclerView() {
        val rv = binding.rvContent

        // 每次都设置 layoutManager 与 adapter（保证切换后 UI 立即生效）
        rv.layoutManager = if (isGridMode) {
            GridLayoutManager(context, 3)
        } else {
            LinearLayoutManager(context)
        }

        // 初始显示骨架屏
        showSkeletonScreen()
        
        // 性能与观感优化
        rv.setHasFixedSize(true)
        rv.setItemViewCacheSize(16)

        // 第一次初始化时，添加 footer 与一次性注册监听/回调
        if (!recyclerInitialized) {
            // 为两个 adapter 添加 footer（避免其中一个在切换时缺失 footer）
            gridAdapter.addFooterView {
                io.stillpage.app.databinding.ViewLoadMoreBinding.bind(loadMoreView)
            }
            listAdapter.addFooterView {
                io.stillpage.app.databinding.ViewLoadMoreBinding.bind(loadMoreView)
            }

            // 列表模式下添加分隔线
            if (!isGridMode) {
                rv.addItemDecoration(VerticalDivider(requireContext()))
            }

            // 设置LoadMoreView点击事件（一次性注册）
            loadMoreView.setOnClickListener {
                if (!loadMoreView.isLoading && viewModel.hasMore.value == true) {
                    viewModel.loadMoreData()
                }
            }

            // 添加滚动监听，实现自动加载更多（一次性注册）
            rv.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (!recyclerView.canScrollVertically(1) &&
                        viewModel.hasMore.value == true &&
                        viewModel.isLoading.value != true) {
                        viewModel.loadMoreData()
                    }
                }
            })

            recyclerInitialized = true
        }
    }

    private fun setupContentTypeTabs() {
        binding.tabLayoutContentType.apply {
            // 初始化一次模式与重力
            tabMode = com.google.android.material.tabs.TabLayout.MODE_SCROLLABLE
            tabGravity = com.google.android.material.tabs.TabLayout.GRAVITY_CENTER
            // 设置主题色
            setSelectedTabIndicatorColor(accentColor)
            setTabTextColors(
                resources.getColor(R.color.tv_text_summary, null),
                accentColor
            )

            // 添加所有内容类型标签
            ExploreNewViewModel.ContentType.values().forEach { contentType ->
                val tab = newTab()
                tab.text = contentType.displayName
                tab.tag = contentType
                addTab(tab)
            }

            // 默认选中"全部"
            selectTab(getTabAt(ExploreNewViewModel.ContentType.ALL.ordinal))

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val contentType = tab?.tag as? ExploreNewViewModel.ContentType
                        ?: ExploreNewViewModel.ContentType.ALL
                    viewModel.filterByContentType(contentType)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    private fun setupRefreshLayout() {
        binding.refreshLayout.apply {
            setColorSchemeColors(accentColor)
            setOnRefreshListener {
                viewModel.refreshData()
            }
        }
    }

    private fun setupTitleBar() {
        binding.titleBar.setTitle(R.string.discovery)
        // 绑定 Toolbar 以启用菜单
        setSupportToolbar(binding.titleBar.toolbar)
        
        // 设置搜索功能
        setupSearchView()
    }
    
    private fun setupSearchView() {
        try {
            val searchView = binding.titleBar.findViewById<androidx.appcompat.widget.SearchView>(R.id.search_view)
            searchView?.let { sv ->
                sv.queryHint = "搜索书籍、作者..."
                sv.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        if (!query.isNullOrBlank()) {
                            // 跳转到搜索结果页面
                            startActivity<io.stillpage.app.ui.book.search.SearchActivity> {
                                putExtra("key", query.trim())
                            }
                            sv.clearFocus()
                        }
                        return true
                    }
                    
                    override fun onQueryTextChange(newText: String?): Boolean {
                        // 可以在这里实现实时搜索建议
                        return false
                    }
                })
                
                // 设置搜索图标点击事件
                sv.setOnSearchClickListener {
                    // 搜索框获得焦点时的处理
                }
            }
        } catch (e: Exception) {
            AppLog.put("设置搜索功能失败", e)
        }
    }

    private fun initObservers() {
        viewModel.booksData.observe(viewLifecycleOwner) { contentMap ->
            updateTabBadges(contentMap)
            updateCurrentContent(contentMap)
            updateEmptyState(contentMap)
        }

        viewModel.currentContentType.observe(viewLifecycleOwner) { contentType ->
            selectTab(contentType)
            // 当分类切换时，立即更新内容
            val currentData = viewModel.booksData.value
            if (currentData != null) {
                updateCurrentContent(currentData)
                updateEmptyState(currentData)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.refreshLayout.isRefreshing = isLoading
            if (isLoading) {
                loadMoreView.startLoad()
                // 首次加载时显示骨架屏
                if (viewModel.booksData.value?.values?.all { it.isEmpty() } != false) {
                    showSkeletonScreen()
                }
            } else {
                loadMoreView.stopLoad()
                // 加载完成后隐藏骨架屏
                if (viewModel.booksData.value?.values?.any { it.isNotEmpty() } == true) {
                    hideSkeletonScreen()
                }
            }
        }

        viewModel.hasMore.observe(viewLifecycleOwner) { hasMore ->
            if (!hasMore) {
                loadMoreView.noMore()
            } else {
                loadMoreView.hasMore()
            }
        }

        viewModel.loadingMsg.observe(viewLifecycleOwner) { msg ->
            if (msg.isNotEmpty()) {
                // 可以在这里显示加载状态消息，比如Toast或者状态栏
                AppLog.put("新发现页面：$msg")
            }
        }

        // 监听成人内容开关变化，立即刷新数据
        observeEvent<Boolean>(PreferKey.enableAdultContent) {
            viewModel.refreshData()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.explore_new_sort, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_sort_name_asc -> viewModel.setSortKey(ExploreNewViewModel.SortKey.NAME_ASC)
            R.id.menu_sort_name_desc -> viewModel.setSortKey(ExploreNewViewModel.SortKey.NAME_DESC)
            R.id.menu_sort_source_weight -> viewModel.setSortKey(ExploreNewViewModel.SortKey.SOURCE_WEIGHT)
            R.id.menu_sort_content_type -> viewModel.setSortKey(ExploreNewViewModel.SortKey.CONTENT_TYPE)
            R.id.menu_refresh -> {
                viewModel.refreshData()
                requireContext().let { context ->
                    android.widget.Toast.makeText(context, "正在刷新发现内容...", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            R.id.menu_clear_cache -> {
                lifecycleScope.launch {
                    try {
                        io.stillpage.app.help.ExploreCacheManager.clearAllCache()
                        requireContext().let { context ->
                            android.widget.Toast.makeText(context, "缓存已清理", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        viewModel.refreshData()
                    } catch (e: Exception) {
                        AppLog.put("清理缓存失败", e)
                        requireContext().let { context ->
                            android.widget.Toast.makeText(context, "清理缓存失败: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun updateTabBadges(contentMap: Map<ExploreNewViewModel.ContentType, List<ExploreNewViewModel.DiscoveryItem>>) {
        // 统计有内容的分类数量
        val categoriesWithContent = contentMap.filter { it.value.isNotEmpty() }

        // 如果只有一个分类有内容，或者没有内容，隐藏TabLayout
        if (categoriesWithContent.size <= 1) {
            binding.tabLayoutContentType.visibility = View.GONE
            return
        } else {
            binding.tabLayoutContentType.visibility = View.VISIBLE
        }

        // 重建标签，按有内容的类型添加
        binding.tabLayoutContentType.removeAllTabs()

        val currentType = viewModel.currentContentType.value ?: ExploreNewViewModel.ContentType.ALL
        var tabToSelect: TabLayout.Tab? = null

        ExploreNewViewModel.ContentType.values().forEach { contentType ->
            val items = contentMap[contentType] ?: emptyList()
            if (items.isNotEmpty()) {
                val tab = binding.tabLayoutContentType.newTab()
                tab.text = "${contentType.displayName}(${items.size})"
                tab.tag = contentType
                binding.tabLayoutContentType.addTab(tab)
                if (contentType == currentType) {
                    tabToSelect = tab
                }
            }
        }

        // 默认选择当前类型存在的标签，否则选择第一个
        if (tabToSelect != null) {
            binding.tabLayoutContentType.selectTab(tabToSelect)
        } else if (binding.tabLayoutContentType.tabCount > 0) {
            binding.tabLayoutContentType.selectTab(binding.tabLayoutContentType.getTabAt(0))
        }
    }

    private fun updateCurrentContent(contentMap: Map<ExploreNewViewModel.ContentType, List<ExploreNewViewModel.DiscoveryItem>>) {
        val currentType = viewModel.currentContentType.value ?: ExploreNewViewModel.ContentType.ALL
        val items = contentMap[currentType] ?: emptyList()

        // 如果有数据，隐藏骨架屏
        if (items.isNotEmpty()) {
            hideSkeletonScreen()
        }

        if (isGridMode) {
            gridAdapter.setItems(items)
        } else {
            listAdapter.setItems(items)
        }
    }

    private fun updateEmptyState(contentMap: Map<ExploreNewViewModel.ContentType, List<ExploreNewViewModel.DiscoveryItem>>) {
        val currentType = viewModel.currentContentType.value ?: ExploreNewViewModel.ContentType.ALL
        val items = contentMap[currentType] ?: emptyList()

        binding.tvEmptyMsg.visibility = if (items.isEmpty() && viewModel.isLoading.value != true) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun selectTab(contentType: ExploreNewViewModel.ContentType) {
        // 根据 tag 查找匹配的 Tab，避免索引耦合
        for (i in 0 until binding.tabLayoutContentType.tabCount) {
            val tab = binding.tabLayoutContentType.getTabAt(i)
            val tagType = tab?.tag as? ExploreNewViewModel.ContentType
            if (tagType == contentType) {
                if (binding.tabLayoutContentType.selectedTabPosition != i) {
                    binding.tabLayoutContentType.selectTab(tab)
                }
                break
            }
        }
    }

    // 实现适配器回调接口
    override fun showBookInfo(book: SearchBook) {
        lifecycleScope.launch {
            try {
                // 先将SearchBook保存到数据库，确保BookInfoActivity能找到它
                withContext(Dispatchers.IO) {
                    // 检查是否已存在，避免重复插入
                    val existingBook = appDb.searchBookDao.getSearchBook(book.bookUrl)
                    if (existingBook == null) {
                        appDb.searchBookDao.insert(book)
                        AppLog.put("新发现页面：保存SearchBook到数据库 - ${book.name}")
                    }
                }

                // 获取书源信息和内容类型用于判断跳转页面
                val (bookSource, contentType) = withContext(Dispatchers.IO) {
                    val source = appDb.bookSourceDao.getBookSource(book.origin)
                    val type = detectContentTypeFromBook(book, source)
                    Pair(source, type)
                }

                // 根据内容类型跳转到对应的详情页
                when (contentType) {
                    ExploreNewViewModel.ContentType.AUDIO -> {
                        AppLog.put("新发现页面：跳转到音频书籍详情页 - ${book.name}")
                        startActivity<AudioBookInfoActivity> {
                            putExtra("name", book.name)
                            putExtra("author", book.author)
                            putExtra("bookUrl", book.bookUrl)
                        }
                    }



                    ExploreNewViewModel.ContentType.MUSIC -> {
                        AppLog.put("新发现页面：跳转到音乐详情页 - ${book.name}")
                        startActivity<MusicInfoActivity> {
                            putExtra("name", book.name)
                            putExtra("author", book.author)
                            putExtra("bookUrl", book.bookUrl)
                        }
                    }

                    ExploreNewViewModel.ContentType.IMAGE -> {
                        AppLog.put("新发现页面：跳转到漫画详情页 - ${book.name}")
                        startActivity<MangaInfoActivity> {
                            putExtra("name", book.name)
                            putExtra("author", book.author)
                            putExtra("bookUrl", book.bookUrl)
                        }
                    }

                    ExploreNewViewModel.ContentType.DRAMA -> {
                        AppLog.put("新发现页面：跳转到短剧详情页 - ${book.name}")
                        startActivity<io.stillpage.app.ui.book.drama.info.DramaInfoActivity> {
                            putExtra("name", book.name)
                            putExtra("author", book.author)
                            putExtra("bookUrl", book.bookUrl)
                        }
                    }

                    else -> {
                        AppLog.put("新发现页面：跳转到普通书籍详情页 - ${book.name}")
                        startActivity<BookInfoActivity> {
                            putExtra("name", book.name)
                            putExtra("author", book.author)
                            putExtra("bookUrl", book.bookUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLog.put("新发现页面：显示书籍详情失败", e)
                requireContext().let { context ->
                    androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle("错误")
                        .setMessage("无法打开书籍详情：${e.localizedMessage}")
                        .setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }
        }
    }



    override fun showBookMenu(book: SearchBook) {
        // 默认实现，可以在这里添加长按菜单功能
    }

    /**
     * 检测书籍内容类型
     * 复用ExploreNewViewModel中的逻辑
     */
    private fun detectContentTypeFromBook(
        book: SearchBook,
        bookSource: io.stillpage.app.data.entities.BookSource?
    ): ExploreNewViewModel.ContentType {
        if (bookSource == null) {
            return ExploreNewViewModel.ContentType.TEXT
        }

        // 先用统一解析规则：若解析到非 TEXT 类型，直接返回
        val sourceType = io.stillpage.app.help.ContentTypeResolver.resolveFromSource(bookSource)
        if (sourceType != ExploreNewViewModel.ContentType.TEXT) return sourceType

        // 基于内容特征的智能识别
        val name = book.name.lowercase()
        val kind = book.kind?.lowercase() ?: ""
        val intro = book.intro?.lowercase() ?: ""
        val sourceUrl = bookSource.bookSourceUrl.lowercase()
        val sourceName = bookSource.bookSourceName.lowercase()
        val allText = "$name $kind $intro $sourceUrl $sourceName"

        // 使用评分系统
        val scores = mutableMapOf<ExploreNewViewModel.ContentType, Int>()

        // 音频内容检测
        val audioKeywords = mapOf(
            "有声书" to 10, "听书" to 10, "播讲" to 10, "朗读" to 8,
            "广播剧" to 10, "相声" to 10, "评书" to 10, "音频" to 8,
            "有声" to 6, "主播" to 6, "配音" to 6, "mp3" to 8
        )
        audioKeywords.forEach { (keyword, score) ->
            if (allText.contains(keyword)) {
                scores[ExploreNewViewModel.ContentType.AUDIO] =
                    scores.getOrDefault(ExploreNewViewModel.ContentType.AUDIO, 0) + score
            }
        }



        // 音乐内容检测
        val musicKeywords = mapOf(
            "音乐" to 8, "歌曲" to 8, "专辑" to 8, "单曲" to 8,
            "歌手" to 6, "乐队" to 6, "流行" to 4, "摇滚" to 4,
            "古典" to 4, "民谣" to 4, "说唱" to 4
        )
        musicKeywords.forEach { (keyword, score) ->
            if (allText.contains(keyword)) {
                scores[ExploreNewViewModel.ContentType.MUSIC] =
                    scores.getOrDefault(ExploreNewViewModel.ContentType.MUSIC, 0) + score
            }
        }



        // 漫画内容检测
        val imageKeywords = mapOf(
            "漫画" to 10, "连环画" to 8, "绘本" to 8, "插画" to 6,
            "comic" to 8, "manga" to 8, "图文" to 4
        )
        imageKeywords.forEach { (keyword, score) ->
            if (allText.contains(keyword)) {
                scores[ExploreNewViewModel.ContentType.IMAGE] =
                    scores.getOrDefault(ExploreNewViewModel.ContentType.IMAGE, 0) + score
            }
        }

        // 视频/短剧内容检测 - 新增
        val dramaKeywords = mapOf(
            "短剧" to 10, "微剧" to 10, "网剧" to 8, "迷你剧" to 8,
            "电影" to 8, "电视剧" to 8, "综艺" to 6, "动漫" to 6,
            "纪录片" to 6, "视频" to 8, "影视" to 8, "影片" to 6,
            "AV" to 15, "番号" to 15, "伦理" to 8, "情色" to 10,
            "m3u8" to 12, "mp4" to 10, "avi" to 8, "mkv" to 8,
            "在线播放" to 10, "高清" to 6, "HD" to 6, "BD" to 6,
            "时长" to 8, "分钟" to 6, "小时" to 6, "导演" to 8,
            "演员" to 8, "主演" to 8, "制片" to 6, "出品" to 6
        )
        dramaKeywords.forEach { (keyword, score) ->
            if (allText.contains(keyword)) {
                scores[ExploreNewViewModel.ContentType.DRAMA] =
                    scores.getOrDefault(ExploreNewViewModel.ContentType.DRAMA, 0) + score
            }
        }

        // 返回最高分数的类型
        val maxScore = scores.maxByOrNull { it.value }
        return if (maxScore != null && maxScore.value >= 8) {
            maxScore.key
        } else {
            ExploreNewViewModel.ContentType.TEXT
        }
    }

    override fun isInBookShelf(name: String, author: String): Boolean {
        return viewModel.isInBookShelf(name, author)
    }

    // 适配器不再需要拉取书源进行类型检测，保留接口但返回 null
    override fun getBookSource(origin: String): BookSource? {
        return null
    }

    // 切换视图模式（只切换 layoutManager 与 adapter，不重复初始化监听）
    fun toggleViewMode() {
        isGridMode = !isGridMode

        // 只替换 layoutManager 与 adapter，避免重复注册监听/添加 footer
        binding.rvContent.layoutManager = if (isGridMode) {
            GridLayoutManager(requireContext(), 3)
        } else {
            LinearLayoutManager(requireContext())
        }

        binding.rvContent.adapter = if (isGridMode) gridAdapter else listAdapter
        binding.rvContent.setHasFixedSize(true)
        binding.rvContent.setItemViewCacheSize(16)
        if (!isGridMode) {
            // 列表模式下添加分隔线（若尚未添加）
            if (binding.rvContent.itemDecorationCount == 0) {
                binding.rvContent.addItemDecoration(VerticalDivider(requireContext()))
            }
        }

        // 重新设置当前数据（如果已有数据）
        val currentData = viewModel.booksData.value
        if (currentData != null) {
            updateCurrentContent(currentData)
        }
    }

    /**
     * 显示骨架屏
     */
    private fun showSkeletonScreen() {
        if (isShowingSkeleton) return
        
        isShowingSkeleton = true
        val skeletonType = if (isGridMode) {
            io.stillpage.app.ui.widget.SkeletonView.SkeletonType.GRID_ITEM
        } else {
            io.stillpage.app.ui.widget.SkeletonView.SkeletonType.LIST_ITEM
        }
        
        skeletonAdapter = io.stillpage.app.ui.widget.SkeletonAdapter(
            requireContext(), 
            skeletonType, 
            if (isGridMode) 9 else 6 // 网格显示更多项
        )
        
        binding.rvContent.adapter = skeletonAdapter
        binding.tvEmptyMsg.visibility = View.GONE
    }
    
    /**
     * 隐藏骨架屏，显示实际内容
     */
    private fun hideSkeletonScreen() {
        if (!isShowingSkeleton) return
        
        isShowingSkeleton = false
        skeletonAdapter?.stopAllAnimations()
        skeletonAdapter = null
        
        // 恢复正常适配器
        binding.rvContent.adapter = if (isGridMode) gridAdapter else listAdapter
    }

    fun gotoTop() {
        binding.rvContent.scrollToPosition(0)
    }

    // MainFragmentInterface 实现
    fun onBackPressed(): Boolean {
        return false
    }
}

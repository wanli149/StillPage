package io.stillpage.app.ui.book.manga.info

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.Theme
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.databinding.ActivityMangaInfoBinding
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.config.ReadBookConfig
import io.stillpage.app.help.glide.ImageLoader
import io.stillpage.app.ui.book.changesource.ChangeBookSourceDialog
import io.stillpage.app.ui.book.changecover.ChangeCoverDialog
import io.stillpage.app.ui.book.group.GroupSelectDialog
import io.stillpage.app.ui.book.info.BookInfoViewModel
import io.stillpage.app.ui.book.read.ReadBookActivity
import io.stillpage.app.ui.book.search.SearchActivity
import io.stillpage.app.ui.book.toc.TocActivityResult
import io.stillpage.app.ui.login.SourceLoginActivity
import io.stillpage.app.ui.widget.dialog.PhotoDialog
import io.stillpage.app.ui.widget.dialog.VariableDialog
import io.stillpage.app.utils.*
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 漫画详情页面
 * 复用BookInfoViewModel的所有功能，提供漫画专用的界面设计
 */
class MangaInfoActivity :
    VMBaseActivity<ActivityMangaInfoBinding, BookInfoViewModel>(toolBarTheme = Theme.Auto),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    ChapterAdapter.CallBack, // 保留原有接口以保持兼容性
    VariableDialog.Callback {

    override val binding by viewBinding(ActivityMangaInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()

    override val oldBook: Book?
        get() = viewModel.bookData.value

    private var book: Book? = null
    private var isIntroExpanded = false
    private var isChapterReversed = false
    private var isChapterExpanded = true
    private val pagedChapterAdapter by lazy { PagedChapterAdapter(this, this) } // 新的分页适配器
    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let { (chapterIndex, chapterPos) ->
            viewModel.getBook()?.let { book ->
                startMangaRead(book, chapterIndex)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // 墨水屏模式下设置状态栏图标颜色
        if (AppConfig.isEInkMode) {
            setLightStatusBar(ReadBookConfig.durConfig.curStatusIconDark())
        }
        
        initData()
        initViews()
        initObservers()
        initEvents()
    }

    // 添加编辑菜单项引用
    private var editMenuItem: MenuItem? = null

    // ========== 菜单相关 ==========
    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        // 保留对编辑按钮的引用
        editMenuItem = menu.findItem(R.id.menu_edit)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !viewModel.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_set_source_variable)?.isVisible = viewModel.bookSource != null
        menu.findItem(R.id.menu_set_book_variable)?.isVisible = viewModel.bookSource != null
        // 编辑按钮只在书籍在书架上时可见
        editMenuItem?.isVisible = viewModel.inBookshelf
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit -> {
                viewModel.getBook()?.let { book ->
                    val intent = Intent(this, io.stillpage.app.ui.book.info.edit.BookInfoEditActivity::class.java).apply {
                        putExtra("bookUrl", book.bookUrl)
                    }
                    startActivity(intent)
                }
            }
            R.id.menu_share_it -> {
                viewModel.getBook()?.let { book ->
                    shareWithQr("${book.name}\n${book.author}\n${book.bookUrl}")
                }
            }
            R.id.menu_refresh -> {
                viewModel.getBook()?.let { book ->
                    viewModel.refreshBook(book)
                }
            }
            R.id.menu_login -> viewModel.bookSource?.let {
                val intent = Intent(this, SourceLoginActivity::class.java).apply {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
                startActivity(intent)
            }
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_set_book_variable -> setBookVariable()
            R.id.menu_copy_book_url -> viewModel.getBook()?.bookUrl?.let {
                sendToClip(it)
                toastOnUi("已复制书籍链接")
            }
            R.id.menu_copy_toc_url -> viewModel.getBook()?.tocUrl?.let {
                sendToClip(it)
                toastOnUi("已复制目录链接")
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun setVariable(key: String, variable: String?) {
        when (key) {
            viewModel.bookSource?.getKey() -> viewModel.bookSource?.setVariable(variable)
            viewModel.bookData.value?.bookUrl -> viewModel.bookData.value?.let {
                it.putCustomVariable(variable)
                viewModel.saveBook(it)
            }
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val comment =
                source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(IO) { source.getVariable() }
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment
                )
            )
        }
    }

    private fun setBookVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val book = viewModel.getBook() ?: return@launch
            val variable = withContext(IO) { book.getCustomVariable() }
            val comment = source.getDisplayVariableComment(
                """书籍变量可在js中通过book.getVariable("custom")获取"""
            )
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_book_variable),
                    book.bookUrl,
                    variable,
                    comment
                )
            )
        }
    }

    private fun initData() {
        AppLog.put("MangaInfoActivity: 初始化漫画详情页面")
        viewModel.initData(intent)
    }

    private fun initViews() = binding.run {
        // 设置章节列表
        rvChapters.layoutManager = LinearLayoutManager(this@MangaInfoActivity)
        // 使用新的分页适配器
        rvChapters.adapter = pagedChapterAdapter
        
        // 添加滚动监听器以实现分页加载
        rvChapters.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                
                // 当滚动到接近底部时加载更多
                if (totalItemCount <= lastVisibleItem + 5) {
                    loadMoreChapters()
                }
            }
        })
    }

    private fun initObservers() {
        viewModel.bookData.observe(this) { book ->
            if (book != null) {
                this.book = book
                showBook(book)
            }
        }

        viewModel.chapterListData.observe(this) { chapters ->
            if (chapters != null) {
                updateChapterList(chapters)
            }
        }

        // 注意：bookSource是属性而不是LiveData，所以不需要observe
    }

    private fun initEvents() = binding.run {
        // 封面点击事件 - 支持更换封面
        ivCover.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeCoverDialog(book.name, book.author))
            }
        }

        // 开始阅读按钮
        btnRead.setOnClickListener {
            viewModel.getBook()?.let { book ->
                // 检查是否有阅读进度，如果有则从上次阅读位置开始
                val chapterIndex = if (book.durChapterIndex > 0 || book.durChapterPos > 0) {
                    book.durChapterIndex
                } else {
                    0 // 从第一话开始阅读
                }
                startMangaRead(book, chapterIndex)
            }
        }

        // 收藏按钮
        btnCollect.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (viewModel.inBookshelf) {
                    deleteBook()
                } else {
                    viewModel.addToBookshelf {
                        updateCollectButton()
                    }
                }
            }
        }

        // 简介展开/收起
        tvExpand.setOnClickListener {
            toggleIntroExpansion()
        }

        // 作者点击搜索
        tvAuthor.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                val intent = Intent(this@MangaInfoActivity, SearchActivity::class.java).apply {
                    putExtra("key", book.author)
                }
                startActivity(intent)
            }
        }

        // 来源点击换源
        tvOrigin.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(
                    ChangeBookSourceDialog(book.name, book.author)
                )
            }
        }

        // 章节排序
        tvChapterSort.setOnClickListener {
            toggleChapterSort()
        }
        
        // 章节展开/收起
        tvChapterExpand.setOnClickListener {
            toggleChapterExpand()
        }
    }

    private fun showBook(book: Book) = binding.run {
        AppLog.put("MangaInfoActivity显示漫画信息: ${book.name}")

        showCover(book)
        tvName.text = book.name
        
        // 作者信息
        tvAuthor.text = "作者：${book.getRealAuthor()}"
        
        // 来源信息
        tvOrigin.text = "来源：${book.originName}"
        
        // 状态信息（根据最新章节判断）
        tvStatus.text = when {
            book.latestChapterTitle.isNullOrEmpty() -> "状态：未知"
            book.canUpdate -> "状态：连载中"
            else -> "状态：已完结"
        }
        
        // 最新话数信息
        tvLatest.text = "最新：${book.latestChapterTitle ?: "未知"}"
        
        // 更新时间
        tvTime.text = "时间：${formatTime(book.latestChapterTime)}"
        
        // 简介
        tvIntro.text = book.getDisplayIntro()

        updateCollectButton()
        updateReadButton(book)
    }
    
    private fun updateReadButton(book: Book) = binding.run {
        // 检查是否有阅读进度
        if (book.durChapterIndex > 0 || book.durChapterPos > 0) {
            // 有阅读进度，显示"继续阅读"
            btnRead.text = "继续阅读"
        } else {
            // 没有阅读进度，显示"开始阅读"
            btnRead.text = "开始阅读"
        }
    }

    private fun showCover(book: Book) {
        book.getDisplayCover().let { coverUrl ->
            ImageLoader.load(this, coverUrl)
                .placeholder(R.drawable.image_cover_default)
                .error(R.drawable.image_cover_default)
                .into(binding.ivCover)
            
            // 添加封面图片的缩放动画效果 - 优化性能
            binding.ivCover.scaleX = 0.8f
            binding.ivCover.scaleY = 0.8f
            
            binding.ivCover.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(300)
                .start()
        }
    }

    private fun updateChapterList(chapters: List<BookChapter>) = binding.run {
        AppLog.put("MangaInfoActivity更新章节列表: ${chapters.size}话")
        
        // 更新话数统计
        tvChapterCount.text = chapters.size.toString()
        
        // 生成模拟的阅读量和收藏数
        val readCount = generateReadCount(chapters.size)
        val collectCount = generateCollectCount(chapters.size)
        
        tvReadCount.text = formatCount(readCount)
        tvCollectCount.text = formatCount(collectCount)
        
        // 使用新的分页适配器更新章节列表
        val displayChapters = if (isChapterReversed) chapters.reversed() else chapters
        pagedChapterAdapter.setChapters(displayChapters)
    }
    
    /**
     * 加载更多章节（分页加载）
     */
    private fun loadMoreChapters() {
        // 这里可以实现分页加载逻辑
        // 目前我们只是简单地记录日志
        AppLog.put("加载更多章节")
    }

    private fun toggleIntroExpansion() = binding.run {
        isIntroExpanded = !isIntroExpanded
        // 使用过渡动画实现平滑的高度变化
        val transition = AutoTransition()
        transition.duration = 200 // 动画持续时间
        TransitionManager.beginDelayedTransition(root as ViewGroup, transition)
        
        if (isIntroExpanded) {
            tvIntro.maxLines = Int.MAX_VALUE
            tvExpand.text = "收起"
        } else {
            tvIntro.maxLines = 3
            tvExpand.text = "展开"
        }
    }

    private fun toggleChapterSort() = binding.run {
        isChapterReversed = !isChapterReversed
        tvChapterSort.text = if (isChapterReversed) "倒序" else "正序"
        
        // 重新设置章节列表
        viewModel.chapterListData.value?.let { chapters ->
            val displayChapters = if (isChapterReversed) chapters.reversed() else chapters
            pagedChapterAdapter.setChapters(displayChapters)
        }
    }

    private fun updateCollectButton() = binding.run {
        if (viewModel.inBookshelf) {
            btnCollect.text = "已收藏"
            btnCollect.setTextColor(getColor(R.color.secondaryText))
        } else {
            btnCollect.text = "收藏"
            btnCollect.setTextColor(getColor(R.color.accent))
        }
        
        // 同时更新阅读按钮状态
        book?.let { updateReadButton(it) }
    }

    private fun startMangaRead(book: Book, chapterIndex: Int) {
        AppLog.put("MangaInfoActivity启动漫画阅读: ${book.name}, 章节: $chapterIndex")
        if (!viewModel.inBookshelf) {
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    launchMangaRead(book, chapterIndex)
                }
            }
        } else {
            launchMangaRead(book, chapterIndex)
        }
    }

    private fun launchMangaRead(book: Book, chapterIndex: Int) {
        val intent = Intent(this, ReadBookActivity::class.java).apply {
            putExtra("bookUrl", book.bookUrl)
            putExtra("inBookshelf", viewModel.inBookshelf)
            // 始终传递章节索引，即使是0也要传递
            putExtra("chapterIndex", chapterIndex)
        }
        startActivity(intent)
    }

    private fun deleteBook() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.sure_del)
            .setMessage("确定要删除这部漫画吗？")
            .setPositiveButton("确定") { _, _ ->
                viewModel.delBook {
                    finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun formatTime(time: Long): String {
        if (time <= 0) return "未知"
        val now = System.currentTimeMillis()
        val diff = now - time
        
        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
        }
    }

    private fun generateReadCount(chapterCount: Int): Int {
        // 根据章节数生成模拟的阅读量
        return (chapterCount * 1000..chapterCount * 5000).random()
    }

    private fun generateCollectCount(chapterCount: Int): Int {
        // 根据章节数生成模拟的收藏数
        return (chapterCount * 10..chapterCount * 100).random()
    }

    private fun formatCount(count: Int): String {
        return when {
            count >= 10000 -> "${count / 10000}万"
            count >= 1000 -> "${count / 1000}k"
            else -> count.toString()
        }
    }

    private fun toggleChapterExpand() = binding.run {
        isChapterExpanded = !isChapterExpanded
        tvChapterExpand.text = if (isChapterExpanded) "收起" else "展开"
        
        // 通知适配器切换所有分组的展开/收起状态
        pagedChapterAdapter.toggleAllGroups(isChapterExpanded)
    }

    // 实现GroupSelectDialog.CallBack接口
    override fun upGroup(requestCode: Int, groupId: Long) {
        viewModel.getBook()?.let { book ->
            book.group = groupId
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            } else if (groupId > 0) {
                viewModel.addToBookshelf {
                    updateCollectButton()
                }
            }
        }
    }

    // 实现ChangeBookSourceDialog.CallBack接口
    override fun changeTo(source: io.stillpage.app.data.entities.BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
    }

    // 实现ChangeCoverDialog.CallBack接口
    override fun coverChangeTo(coverUrl: String) {
        viewModel.bookData.value?.let { book ->
            book.customCoverUrl = coverUrl
            showCover(book)
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            }
        }
    }

    // 实现ChapterAdapter.CallBack接口
    override fun onChapterClick(chapter: BookChapter, position: Int) {
        viewModel.getBook()?.let { book ->
            startMangaRead(book, position)
        }
    }

    override fun onChapterMoreClick(chapter: BookChapter, position: Int) {
        // 显示更多操作菜单
        // 可以实现下载、分享等功能
    }

    private fun openChapterList() {
        viewModel.getBook()?.let { book ->
            tocActivityResult.launch(book.bookUrl)
        }
    }

    override fun observeLiveBus() {
        // 可以在这里添加EventBus监听
    }
}

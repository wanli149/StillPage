package io.stillpage.app.ui.book.drama.info

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResult
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.Theme
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.databinding.ActivityDramaInfoBinding
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.config.ReadBookConfig
import io.stillpage.app.utils.setLightStatusBar
import io.stillpage.app.help.glide.ImageLoader
import io.stillpage.app.ui.book.changesource.ChangeBookSourceDialog
import io.stillpage.app.ui.book.drama.adapter.DramaEpisodeAdapter
import io.stillpage.app.ui.book.group.GroupSelectDialog
import io.stillpage.app.ui.book.info.BookInfoViewModel
import io.stillpage.app.ui.book.info.edit.BookInfoEditActivity
import io.stillpage.app.ui.book.source.edit.BookSourceEditActivity
import io.stillpage.app.ui.book.toc.TocActivityResult
import io.stillpage.app.ui.book.changecover.ChangeCoverDialog
import io.stillpage.app.ui.widget.dialog.PhotoDialog
import io.stillpage.app.ui.widget.dialog.VariableDialog
import io.stillpage.app.utils.sendToClip
import io.stillpage.app.utils.shareWithQr
import io.stillpage.app.utils.gone
import io.stillpage.app.utils.showDialogFragment
import io.stillpage.app.utils.startActivity
import io.stillpage.app.utils.toastOnUi
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import io.stillpage.app.utils.visible
import io.stillpage.app.data.appDb
import io.stillpage.app.ui.browser.WebViewActivity
import io.stillpage.app.ui.widget.TitleBar
import io.stillpage.app.model.webBook.WebBook
import io.stillpage.app.ui.book.read.ReadBookActivity
import io.stillpage.app.ui.book.audio.AudioPlayActivity
import io.stillpage.app.help.book.isDrama
import io.stillpage.app.help.book.isWebFile
import io.stillpage.app.help.book.isAudio
import io.stillpage.app.help.book.addType
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.lib.dialogs.selector
import io.stillpage.app.utils.openFileUri
import io.stillpage.app.utils.setStatusBarColorAuto
import io.stillpage.app.lib.theme.primaryTextColor
import android.net.Uri
import io.stillpage.app.help.http.okHttpClient
import io.stillpage.app.model.analyzeRule.AnalyzeUrl
import okhttp3.Request
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 短剧详情页面
 * 基于BookInfoViewModel，专门为短剧内容优化的详情页面
 */
class DramaInfoActivity :
    VMBaseActivity<ActivityDramaInfoBinding, BookInfoViewModel>(fullScreen = false, toolBarTheme = Theme.Auto),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    VariableDialog.Callback,
    DramaEpisodeAdapter.CallBack {

    override val binding by viewBinding(ActivityDramaInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()

    private lateinit var episodeAdapter: DramaEpisodeAdapter
    private var isIntroExpanded = false
    private var chapterChanged = false
    private var isGridView = true // 默认网格视图
    private var playRequested = false // 目录加载完成后是否自动播放

    // 章节选择结果处理
    private val tocActivityResult = registerForActivityResult(TocActivityResult()) { result: Triple<Int, Int, Boolean>? ->
        result?.let {
            viewModel.getBook(false)?.let { book ->
                lifecycleScope.launch {
                    withContext(IO) {
                        book.durChapterIndex = it.first
                        book.durChapterPos = it.second
                        chapterChanged = it.third
                        appDb.bookDao.update(book)
                    }
                    startDramaPlay(book, it.first)
                }
            }
        } ?: let {
            if (!viewModel.inBookshelf) {
                viewModel.delBook()
            }
        }
    }

    // 书源编辑结果处理
    private val sourceEditActivity = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            // 书源编辑完成，重新加载书籍信息
            viewModel.upEditBook()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        setupTitleBar()
        // 提升辨识度：使用主题强调色作为状态栏颜色，并关闭沉浸式
        runCatching {
            val accent = io.stillpage.app.lib.theme.ThemeStore.accentColor(this)
            setStatusBarColorAuto(accent, isTransparent = false, fullScreen = false)
        }
        
        // 墨水屏模式下设置状态栏图标颜色
        if (AppConfig.isEInkMode) {
            setLightStatusBar(ReadBookConfig.durConfig.curStatusIconDark())
        }
        
        initView()
        initData()
        initEvent()
    }
    
    /**
     * 设置标题栏
     */
    private fun setupTitleBar() {
        binding.titleBar.setNavigationOnClickListener {
            finish()
        }
        // 根据当前主题的主色亮度，自动设置标题栏图标颜色（黑/白）
        val tintColor = this.primaryTextColor
        binding.titleBar.setColorFilter(tintColor)
    }

    private fun initView() {
        // 初始化集数适配器
        episodeAdapter = DramaEpisodeAdapter(this, this)
        
        // 设置RecyclerView - 根据屏幕宽度动态调整列数
        setupEpisodeRecyclerView()
        
        // 设置视图切换按钮
        binding.btnToggleView.setOnClickListener {
            toggleEpisodeView()
        }

        // 设置简介展开/收起状态
        updateIntroExpandState()
    }

    private fun initData() {
        // 之前要求同时存在 name 和 author 才初始化，导致某些启动场景未调用 initData
        // 直接调用 initData(intent) 以保持与书籍详情页一致，确保 viewModel 可正确加载书籍/书源/章节数据
        viewModel.initData(intent)
    }

    private fun initEvent() {
        // 工具栏返回按钮已在setupTitleBar中设置

        // 封面点击 - 更换封面
        binding.ivCover.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeCoverDialog(book.name, book.author))
            }
        }

        // 封面长按 - 查看大图
        binding.ivCover.setOnLongClickListener {
            viewModel.getBook()?.getDisplayCover()?.let { path ->
                showDialogFragment(PhotoDialog(path))
            }
            true
        }

        // 简介展开/收起
        binding.tvExpand.setOnClickListener {
            toggleIntroExpansion()
        }

        // 收藏按钮
        binding.btnCollect.setOnClickListener {
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

        // 立即播放按钮
        binding.btnPlay.setOnClickListener {
            viewModel.getBook()?.let { book ->
                // 如果本条目实际上是书籍（非短剧），直接进入阅读逻辑（与 BookInfoActivity 保持一致）
                if (!book.isDrama) {
                    // 书籍：进入阅读流程（如果是 webFile 则提示下载）
                    if (book.isWebFile) {
                        // 与书籍详情一致的处理
                        // 使用相同的行为：弹出下载/导入提示后再进入阅读
                        // 这里只调用同名方法（实现位于本类下方）
                        showWebFileDownloadAlert { readBook(it) }
                    } else {
                        readBook(book)
                    }
                    return@let
                }

                // 短剧：如果章节列表为空，先加载章节信息
                if (episodeAdapter.itemCount == 0) {
                    AppLog.put("DramaInfoActivity: 章节列表为空，开始加载章节信息")
                    playRequested = true
                    AppLog.put("DramaInfoActivity: 已设置播放请求标志，目录加载完成后自动播放")
                    viewModel.loadBookInfo(book, runPreUpdateJs = false)
                    toastOnUi("正在加载章节信息，请稍候...")
                } else {
                    startDramaPlay(book, book.durChapterIndex)
                }
            }
        }
    }

    /**
     * 跳转到阅读页面（复用 BookInfoActivity 的行为）
     */
    private fun readBook(book: Book) {
        if (!viewModel.inBookshelf) {
            book.addType(io.stillpage.app.constant.BookType.notShelf)
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startReadActivity(book)
                }
            }
        } else {
            viewModel.saveBook(book) {
                startReadActivity(book)
            }
        }
    }

    private fun startReadActivity(book: Book) {
        when {
            book.isAudio -> {
                startActivity(Intent(this, AudioPlayActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf))
            }

            else -> {
                startActivity(Intent(this, ReadBookActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
                    .putExtra("chapterChanged", chapterChanged))
            }
        }
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        
        viewModel.bookData.observe(this) { book ->
            if (book != null) {
                showBook(book)
                updateCollectButton()
                // 若当前没有章节数据，主动触发目录加载，确保选集有数据
                val toc = viewModel.chapterListData.value
                if (toc == null || toc.isEmpty()) {
                    viewModel.loadBookInfo(book, runPreUpdateJs = false)
                }
            }
        }

        viewModel.chapterListData.observe(this) { chapters ->
            AppLog.put("DramaInfoActivity: 更新剧集列表，共${chapters.size}集")
            
            // 更新适配器数据
            episodeAdapter.setItems(chapters)
            
            // 更新当前播放集数
            viewModel.getBook()?.let { book ->
                val currentEpisode = book.durChapterIndex
                episodeAdapter.setCurrentEpisode(currentEpisode)
            }
            
            // 如果章节列表加载完成，异步检查是否有本地章节内容已就绪，若就绪则尝试播放
            if (chapters.isNotEmpty()) {
                viewModel.getBook()?.let { book ->
                    if (playRequested) {
                        AppLog.put("DramaInfoActivity: 章节列表加载完成，触发自动播放")
                        playRequested = false
                        startDramaPlay(book, book.durChapterIndex)
                    } else {
                        lifecycleScope.launch {
                            val chapterExists = withContext(IO) {
                                // 在 IO 线程访问数据库，避免主线程阻塞
                                appDb.bookChapterDao.getChapter(book.bookUrl, book.durChapterIndex) != null
                            }
                            if (chapterExists) {
                                AppLog.put("DramaInfoActivity: 章节已加载，可以重新尝试播放")
                                startDramaPlay(book, book.durChapterIndex)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 显示短剧信息
     */
    private fun showBook(book: Book) = binding.run {
        AppLog.put("DramaInfoActivity显示短剧信息: ${book.name}")

        // 封面
        showCover(book)
        
        // 基本信息
        tvName.text = book.name
        tvActor.text = book.getRealAuthor() // 演员信息使用作者字段
        tvOrigin.text = book.originName
        tvLatest.text = book.latestChapterTitle ?: "暂无更新"
        tvUpdateTime.text = formatUpdateTime(book.latestChapterTime)
        
        // 简介
        tvIntro.text = book.getDisplayIntro()
        
        // 来源标签
        updateOriginTag(book)
    }

    /**
     * 显示封面
     */
    private fun showCover(book: Book) {
        book.getDisplayCover().let { coverUrl ->
            ImageLoader.load(this, coverUrl)
                .placeholder(R.drawable.image_cover_default)
                .error(R.drawable.image_cover_default)
                .into(binding.ivCover)
        }
    }

    /**
     * 格式化更新时间
     */
    private fun formatUpdateTime(time: Long): String {
        return if (time > 0) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
        } else {
            "未知"
        }
    }

    /**
     * 更新来源标签
     */
    private fun updateOriginTag(book: Book) {
        // 根据书源质量显示不同标签
        val tag = when {
            book.originName.contains("优质") || book.originName.contains("推荐") -> "[推荐]"
            book.originName.contains("高清") || book.originName.contains("HD") -> "[高清]"
            else -> ""
        }
        
        if (tag.isNotEmpty()) {
            binding.tvOriginTag.text = tag
            binding.tvOriginTag.visible()
        } else {
            binding.tvOriginTag.gone()
        }
    }

    /**
     * 切换简介展开状态
     */
    private fun toggleIntroExpansion() {
        isIntroExpanded = !isIntroExpanded
        updateIntroExpandState()
    }

    /**
     * 更新简介展开状态
     */
    private fun updateIntroExpandState() {
        binding.apply {
            if (isIntroExpanded) {
                tvIntro.maxLines = Int.MAX_VALUE
                tvExpand.text = "收起"
            } else {
                tvIntro.maxLines = 3
                tvExpand.text = "展开"
            }
        }
    }

    /**
     * 更新收藏按钮状态
     */
    private fun updateCollectButton() {
        binding.btnCollect.apply {
            if (viewModel.inBookshelf) {
                text = "已收藏"
                setIconResource(R.drawable.ic_favorite)
                iconTint = ContextCompat.getColorStateList(this@DramaInfoActivity, R.color.content_type_drama)
            } else {
                text = "收藏"
                setIconResource(R.drawable.ic_favorite_border)
                iconTint = ContextCompat.getColorStateList(this@DramaInfoActivity, R.color.textColorPrimary)
            }
        }
    }

    /**
     * 删除书籍
     */
    private fun deleteBook() {
        viewModel.delBook {
            finish()
        }
    }

    /**
     * 设置剧集RecyclerView
     */
    private fun setupEpisodeRecyclerView() {
        if (isGridView) {
            // 网格模式 - 根据屏幕宽度动态调整列数
            val screenWidth = resources.displayMetrics.widthPixels
            val itemWidth = resources.getDimensionPixelSize(R.dimen.drama_episode_item_width)
            val spanCount = maxOf(3, screenWidth / itemWidth)
            binding.rvEpisodes.layoutManager = GridLayoutManager(this, spanCount)
        } else {
            // 列表模式
            binding.rvEpisodes.layoutManager = LinearLayoutManager(this)
        }
        binding.rvEpisodes.adapter = episodeAdapter
    }

    /**
     * 切换剧集视图模式
     */
    private fun toggleEpisodeView() {
        isGridView = !isGridView
        
        // 更新按钮图标
        binding.btnToggleView.setImageResource(
            if (isGridView) R.drawable.ic_list_view else R.drawable.ic_grid_view
        )
        
        // 重新设置布局管理器
        setupEpisodeRecyclerView()
        
        // 通知适配器切换视图模式
        episodeAdapter.setViewMode(isGridView)
    }

    /**
     * 开始播放短剧（增强容错）
     */
    private fun startDramaPlay(book: Book, episode: Int) {
        AppLog.put("DramaInfoActivity: 请求播放短剧 ${book.name} 第${episode + 1}集")

        // 使用 WebView 播放短剧视频（HTML5 视频播放）
        lifecycleScope.launch {
            // 先持久化播放进度到数据库，确保书架依据历史继续播放
            withContext(IO) {
                book.durChapterIndex = episode
                book.durChapterPos = 0
                appDb.bookDao.update(book)
            }
            val chapter = withContext(IO) {
                // 获取当前章节信息（从数据库尝试读取）
                appDb.bookChapterDao.getChapter(book.bookUrl, episode)
            }
            // 数据库未命中时，尝试使用内存中的章节列表，优先播放
            val memChapter = chapter ?: viewModel.chapterListData.value?.getOrNull(episode)
            if (memChapter != null) {
                val src = if (chapter != null) "数据库" else "内存"
                AppLog.put("DramaInfoActivity: 找到章节（$src），开始播放 ${memChapter.url ?: "无播放链接"}")
                playChapterInWebView(memChapter)
                return@launch
            }

            if (chapter != null) {
                // 章节已存在，继续播放流程
                AppLog.put("DramaInfoActivity: 找到章节，开始播放 ${chapter.url ?: "无播放链接"}")
                // （调用已有播放逻辑，例如打开 WebView 或播放器）
                playChapterInWebView(chapter)
                return@launch
            }

            // 若未找到章节，使用 ViewModel 正式触发目录加载（调用公开方法）
            AppLog.put("DramaInfoActivity: 本地未找到章节，调用 ViewModel.loadBookInfo 加载目录")
            toastOnUi(R.string.toc_updateing)
            // 触发目录加载；加载完成后将通过 LiveData 回调到 observe 中再调用 startDramaPlay
            // 使用位置参数以避免命名参数在不同签名上的兼容问题
            viewModel.loadBookInfo(book, true, false)
        }
    }

    // 将章节在内置 WebView 中播放（复用已有浏览器页面实现）
    private fun playChapterInWebView(chapter: BookChapter) {
        val book = viewModel.getBook() ?: return
        lifecycleScope.launch {
            var url = chapter.getAbsoluteURL()
            // 如果链接不是视频直链或为空，尝试解析正文提取真实视频链接
            if (!isDirectVideoUrl(url) || url.isBlank()) {
                viewModel.bookSource?.let { source ->
                    val content = withContext(IO) {
                        io.stillpage.app.model.webBook.WebBook.getContentAwait(
                            source,
                            book,
                            chapter,
                            nextChapterUrl = null,
                            needSave = false
                        )
                    }
                    val extracted = extractVideoUrl(content)
                    if (isDirectVideoUrl(extracted)) {
                        url = extracted
                    } else if (url.isNotBlank()) {
                        // 回退1：通过 AnalyzeUrl 使用 WebView 渲染抓取页面再提取直链
                        val renderedHtml = fetchRenderedPageContent(url, book.bookUrl)
                        var candidate = extractVideoUrl(renderedHtml)
                        if (!isDirectVideoUrl(candidate)) {
                            // 回退2：直接抓取页面HTML并尝试提取直链
                            val pageHtml = fetchPageContent(url)
                            candidate = extractVideoUrl(pageHtml)
                        }
                        if (isDirectVideoUrl(candidate)) {
                            url = candidate
                        }
                    }
                }
            }

            if (url.isBlank()) {
                toastOnUi("未找到有效播放链接")
                return@launch
            }

            val title = "${book.name} 第${chapter.index + 1}集"
            AppLog.put("DramaInfoActivity: playChapterInWebView -> $url")

            // 回归旧逻辑：统一走内置 WebView 播放/展示
            val intent = Intent(this@DramaInfoActivity, WebViewActivity::class.java).apply {
                putExtra("title", title)
                putExtra("url", url)
                putExtra("sourceName", book.originName)
                putExtra("sourceOrigin", book.origin)
                putExtra("sourceType", io.stillpage.app.constant.SourceType.book)
            }
            startActivity(intent)
        }
    }

    private suspend fun fetchPageContent(url: String): String {
        return withContext(IO) {
            try {
                val req = Request.Builder().url(url).get().build()
                okHttpClient.newCall(req).execute().use { resp ->
                    resp.body?.string().orEmpty()
                }
            } catch (e: Exception) {
                AppLog.put("DramaInfoActivity: 页面抓取失败", e, true)
                ""
            }
        }
    }

    private suspend fun fetchRenderedPageContent(url: String, baseUrl: String): String {
        return withContext(IO) {
            try {
                val analyze = AnalyzeUrl(
                    mUrl = url,
                    baseUrl = baseUrl,
                    source = viewModel.bookSource
                )
                val res = analyze.getStrResponseAwait(useWebView = true)
                res.body ?: ""
            } catch (e: Exception) {
                AppLog.put("DramaInfoActivity: WebView渲染抓取失败", e, true)
                ""
            }
        }
    }

    /**
     * 从内容中提取视频播放链接
     */
    private fun extractVideoUrl(content: String): String {
        AppLog.put("DramaInfoActivity: 开始提取视频播放链接")

        // 检测到“播放直链：”提示但无实际链接时直接返回空，避免误判
        if (content.contains("播放直链：") && !content.contains("http")) {
            AppLog.put("DramaInfoActivity: 检测到播放直链标识但无实际链接，跳过")
            return ""
        }

        // 常见的视频URL模式
        val urlPatterns = listOf(
            // 直接的视频链接
            Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8|webm|ts)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE),
            // M3U8播放列表
            Regex("""https?://[^\s"'<>]*\.m3u8[^\s"'<>]*""", RegexOption.IGNORE_CASE),
            // 通用的媒体链接（包含video/play/stream关键词）
            Regex("""https?://[^\s"'<>]*(?:video|play|stream|media)[^\s"'<>]*""", RegexOption.IGNORE_CASE),
            // 任何HTTP链接（作为最后的尝试）
            Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        )

        for ((index, pattern) in urlPatterns.withIndex()) {
            val matches = pattern.findAll(content)
            for (match in matches) {
                val url = match.value
                AppLog.put("DramaInfoActivity: 模式${index + 1}找到链接 - $url")

                // 过滤掉明显不是媒体直链的URL
                if (isDirectVideoUrl(url)) {
                    AppLog.put("DramaInfoActivity: 确认为有效视频链接 - $url")
                    return url
                }
            }
        }

        AppLog.put("DramaInfoActivity: 未找到有效的视频播放链接")
        return ""
    }

    /**
     * 判断是否为有效的视频URL
     */
    private fun isDirectVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        // 排除明显非媒体协议/资源
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") ||
            lower.startsWith("tel:") || lower.startsWith("sms:")) return false
        val excludeExt = listOf(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico",
            ".html", ".htm", ".php", ".asp", ".jsp"
        )
        if (excludeExt.any { lower.contains(it) }) return false
        // 更宽泛的识别逻辑：覆盖更多直链与提示关键词
        val videoHints = listOf(
            ".mp4", ".m3u8", ".mpd", ".webm", ".ts",
            "/dash/", "application/dash+xml",
            "application/vnd.apple.mpegurl",
            "mime_type=video", "mime_type=video_mp4",
            "video/mp4", "video/mpeg",
            "play", "stream"
        )
        return videoHints.any { lower.contains(it) }
    }

    // ========== 菜单相关 ==========

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible =
            !viewModel.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_set_source_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_set_book_variable)?.isVisible =
            viewModel.bookSource != null
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit -> {
                viewModel.getBook()?.let { book ->
                    startActivity<BookInfoEditActivity> {
                        putExtra("name", book.name)
                        putExtra("author", book.author)
                    }
                }
            }
            R.id.menu_copy_book_url -> {
                viewModel.getBook()?.bookUrl?.let { url ->
                    sendToClip(url)
                    toastOnUi("已复制链接")
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
                startActivity<io.stillpage.app.ui.login.SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
            }
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_set_book_variable -> setBookVariable()
            R.id.menu_copy_toc_url -> viewModel.getBook()?.tocUrl?.let {
                sendToClip(it)
                toastOnUi("已复制目录链接")
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    // ========== 接口实现 ==========

    override fun onEpisodeClick(chapter: BookChapter, episode: Int) {
        viewModel.getBook()?.let { book ->
            // 更新当前章节
            lifecycleScope.launch {
                withContext(IO) {
                    book.durChapterIndex = episode
                    book.durChapterPos = 0
                    appDb.bookDao.update(book)
                }
                // 更新适配器显示
                episodeAdapter.setCurrentEpisode(episode)
                // 开始播放
                startDramaPlay(book, episode)
            }
        }
    }

    override val oldBook: Book?
        get() = viewModel.bookData.value

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
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

    override fun coverChangeTo(coverUrl: String) {
        viewModel.getBook()?.let { book ->
            book.customCoverUrl = coverUrl
            viewModel.saveBook(book)
            showCover(book)
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        viewModel.getBook()?.let { book ->
            book.group = groupId
            viewModel.saveBook(book)
        }
    }

    private fun showWebFileDownloadAlert(
        onClick: ((Book) -> Unit)? = null,
    ) {
        val webFiles = viewModel.webFiles
        if (webFiles.isEmpty()) {
            toastOnUi("Unexpected webFileData")
            return
        }
        selector(
            R.string.download_and_import_file,
            webFiles
        ) { _, webFile, _ ->
            if (webFile.isSupported) {
                /* import */
                viewModel.importOrDownloadWebFile<Book>(webFile) {
                    onClick?.invoke(it)
                }
            } else if (webFile.isSupportDecompress) {
                /* 解压筛选后再选择导入项 */
                viewModel.importOrDownloadWebFile<Uri>(webFile) { uri ->
                    viewModel.getArchiveFilesName(uri) { fileNames ->
                        if (fileNames.size == 1) {
                            viewModel.importArchiveBook(uri, fileNames[0]) {
                                onClick?.invoke(it)
                            }
                        } else {
                            showDecompressFileImportAlert(uri, fileNames, onClick)
                        }
                    }
                }
            } else {
                alert(
                    title = getString(R.string.draw),
                    message = getString(R.string.file_not_supported, webFile.name)
                ) {
                    neutralButton(R.string.open_fun) {
                        /* download only */
                        viewModel.importOrDownloadWebFile<Uri>(webFile) {
                            openFileUri(it, "*/*")
                        }
                    }
                    noButton()
                }
            }
        }
    }

    private fun showDecompressFileImportAlert(
        archiveFileUri: Uri,
        fileNames: List<String>,
        success: ((Book) -> Unit)? = null,
    ) {
        if (fileNames.isEmpty()) {
            toastOnUi(R.string.unsupport_archivefile_entry)
            return
        }
        selector(
            R.string.import_select_book,
            fileNames
        ) { _, name, _ ->
            viewModel.importArchiveBook(archiveFileUri, name) {
                success?.invoke(it)
            }
        }
    }
}

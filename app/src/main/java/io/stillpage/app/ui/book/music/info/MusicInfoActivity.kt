package io.stillpage.app.ui.book.music.info

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.Theme
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.databinding.ActivityMusicInfoBinding
import io.stillpage.app.help.book.isLocal
import io.stillpage.app.ui.book.changesource.ChangeBookSourceDialog
import io.stillpage.app.ui.book.group.GroupSelectDialog
import io.stillpage.app.ui.book.info.BookInfoViewModel
import io.stillpage.app.ui.book.music.play.MusicPlayActivity
import io.stillpage.app.ui.book.search.SearchActivity
import io.stillpage.app.ui.book.toc.TocActivityResult
import io.stillpage.app.ui.widget.dialog.PhotoDialog
import io.stillpage.app.ui.book.changecover.ChangeCoverDialog
import io.stillpage.app.utils.*
import io.stillpage.app.utils.viewbindingdelegate.viewBinding

import io.stillpage.app.help.glide.ImageLoader
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音乐详情页面
 * 复用BookInfoViewModel的逻辑，使用音乐专用布局和播放跳转
 */
class MusicInfoActivity :
    VMBaseActivity<ActivityMusicInfoBinding, BookInfoViewModel>(toolBarTheme = Theme.Auto),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    SongAdapter.CallBack {

    override val binding by viewBinding(ActivityMusicInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()

    override val oldBook: Book?
        get() = viewModel.bookData.value

    private var book: Book? = null
    private var isIntroExpanded = false
    private val songAdapter by lazy { SongAdapter(this, this) }
    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let { (chapterIndex, chapterPos) ->
            viewModel.getBook()?.let { book ->
                startMusicPlay(book, chapterIndex)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.setBackgroundResource(R.color.transparent)

        initViews()
        initObservers()
        initEvents()

        // 初始化数据
        viewModel.initData(intent)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit -> {
                // 编辑音乐信息
                return true
            }
            R.id.menu_refresh -> {
                // 刷新音乐信息
                viewModel.getBook()?.let { book ->
                    viewModel.refreshBook(book)
                }
                return true
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initViews() {
        // 设置歌曲列表
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MusicInfoActivity)
            adapter = songAdapter
        }
    }

    private fun initObservers() {
        viewModel.bookData.observe(this) { book ->
            this.book = book
            showBook(book)
        }

        viewModel.chapterListData.observe(this) { chapters ->
            updateSongs(chapters)
        }
    }

    private fun initEvents() = binding.run {
        // 封面点击事件 - 支持更换封面
        ivCover.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeCoverDialog(book.name, book.author))
            }
        }

        // 播放全部按钮
        btnPlayAll.setOnClickListener {
            viewModel.getBook()?.let { book ->
                startMusicPlay(book, 0) // 从第一首开始播放
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

        // 艺术家点击搜索
        tvArtist.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                val intent = Intent(this@MusicInfoActivity, SearchActivity::class.java).apply {
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
    }

    private fun showBook(book: Book) = binding.run {
        AppLog.put("MusicInfoActivity显示音乐信息: ${book.name}")

        showCover(book)
        tvName.text = book.name
        
        // 艺术家信息（使用作者字段）
        tvArtist.text = book.getRealAuthor()
        
        // 来源信息
        tvOrigin.text = book.originName
        
        // 发行时间
        tvReleaseTime.text = formatTime(book.latestChapterTime)
        
        // 类型信息（使用分类字段）
        tvGenre.text = book.kind ?: "未知类型"
        
        // 简介
        tvIntro.text = book.getDisplayIntro()

        // 生成随机统计数据
        generateRandomStats(book)

        updateCollectButton()
    }

    private fun showCover(book: Book) {
        book.getDisplayCover().let { coverUrl ->
            ImageLoader.load(this, coverUrl)
                .placeholder(R.drawable.image_cover_default)
                .error(R.drawable.image_cover_default)
                .into(binding.ivCover)
        }
    }

    private fun generateRandomStats(book: Book) = binding.run {
        // 基于书名生成伪随机数据
        val hash = book.name.hashCode()
        val playCount = (kotlin.math.abs(hash) % 100000 + 1000).toString()
        val collectCount = (kotlin.math.abs(hash) % 10000 + 100).toString()
        val shareCount = (kotlin.math.abs(hash) % 1000 + 50).toString()
        
        tvPlayCount.text = formatCount(playCount.toInt())
        tvCollectCount.text = formatCount(collectCount.toInt())
        tvShareCount.text = formatCount(shareCount.toInt())
    }

    private fun formatCount(count: Int): String {
        return when {
            count >= 10000 -> "${count / 1000}.${(count % 1000) / 100}万"
            count >= 1000 -> "${count / 1000}.${(count % 1000) / 100}k"
            else -> count.toString()
        }
    }

    private fun updateCollectButton() = binding.run {
        if (viewModel.inBookshelf) {
            btnCollect.text = "已收藏"
            btnCollect.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite, 0, 0, 0)
        } else {
            btnCollect.text = "收藏"
            btnCollect.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_favorite_border, 0, 0, 0)
        }
    }

    private fun updateSongs(chapters: List<BookChapter>) {
        songAdapter.setItems(chapters)
    }

    private fun toggleIntroExpansion() = binding.run {
        isIntroExpanded = !isIntroExpanded
        if (isIntroExpanded) {
            tvIntro.maxLines = Int.MAX_VALUE
            tvExpand.text = "收起"
        } else {
            tvIntro.maxLines = 3
            tvExpand.text = "展开"
        }
    }

    private fun startMusicPlay(book: Book, songIndex: Int) {
        AppLog.put("MusicInfoActivity启动音乐播放: ${book.name}, 歌曲: $songIndex")
        if (!viewModel.inBookshelf) {
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    launchMusicPlay(book, songIndex)
                }
            }
        } else {
            launchMusicPlay(book, songIndex)
        }
    }

    private fun launchMusicPlay(book: Book, songIndex: Int) {
        // 若判断为 MV，回归旧逻辑：统一走内置 WebView 展示
        if (io.stillpage.app.help.PlaybackTypeDetector.isMusicVideo(book)) {
            val candidateUrl = book.tocUrl.ifEmpty { book.bookUrl }
            val lower = candidateUrl.lowercase()
            val exclude = listOf(
                "javascript:", "mailto:", "tel:", "sms:",
                ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico",
                ".html", ".htm", ".php", ".asp", ".jsp"
            )
            val videoHints = listOf(".mp4", ".m3u8", ".webm", ".ts", ".mpd", "/dash/")

            // 统一按原策略走内置浏览器
            val intent = Intent(this, io.stillpage.app.ui.browser.WebViewActivity::class.java).apply {
                putExtra("title", book.name)
                putExtra("url", candidateUrl)
                putExtra("sourceName", book.originName)
                putExtra("sourceOrigin", book.origin)
                putExtra("sourceType", io.stillpage.app.constant.SourceType.book)
            }
            startActivity(intent)
            return
        }
        val intent = Intent(this, MusicPlayActivity::class.java).apply {
            putExtra("bookUrl", book.bookUrl)
            putExtra("songIndex", songIndex)
            putExtra("inBookshelf", viewModel.inBookshelf)
            putExtra("autoPlay", true)
        }
        startActivity(intent)
    }

    private fun deleteBook() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.sure_del)
            .setMessage("确定要删除这个音乐专辑吗？")
            .setPositiveButton("确定") { _, _ ->
                viewModel.delBook {
                    finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun formatTime(timestamp: Long): String {
        return if (timestamp > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        } else {
            "未知"
        }
    }

    // 实现GroupSelectDialog.CallBack接口
    override fun upGroup(requestCode: Int, groupId: Long) {
        viewModel.getBook()?.let {
            it.group = groupId
            viewModel.saveBook(it)
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

    // 实现SongAdapter.CallBack接口
    override fun onSongClick(song: BookChapter, position: Int) {
        viewModel.getBook()?.let { book ->
            startMusicPlay(book, position)
        }
    }

    override fun onSongMoreClick(song: BookChapter, position: Int) {
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

package io.stillpage.app.ui.book.audio.info

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.Theme
import io.stillpage.app.constant.EventBus
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.databinding.ActivityAudioBookInfoBinding
import io.stillpage.app.help.book.*
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.config.ReadBookConfig
import io.stillpage.app.utils.setLightStatusBar
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.model.BookCover
import io.stillpage.app.model.AudioPlay
import io.stillpage.app.constant.Status
import io.stillpage.app.ui.book.audio.AudioPlayActivity
import io.stillpage.app.ui.book.changesource.ChangeBookSourceDialog
import io.stillpage.app.ui.book.group.GroupSelectDialog
import io.stillpage.app.ui.book.info.BookInfoViewModel
import io.stillpage.app.ui.book.info.edit.BookInfoEditActivity
import io.stillpage.app.ui.book.search.SearchActivity
import io.stillpage.app.ui.book.source.edit.BookSourceEditActivity
import io.stillpage.app.ui.book.toc.TocActivityResult
import io.stillpage.app.ui.widget.dialog.PhotoDialog
import io.stillpage.app.ui.widget.dialog.VariableDialog
import io.stillpage.app.ui.login.SourceLoginActivity
import io.stillpage.app.utils.StartActivityContract
import io.stillpage.app.utils.gone
import io.stillpage.app.utils.*
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音频书籍详情页面
 * 完全复用BookInfoActivity的逻辑，只是使用音频专用布局和播放跳转
 */
class AudioBookInfoActivity :
    VMBaseActivity<ActivityAudioBookInfoBinding, BookInfoViewModel>(toolBarTheme = Theme.Dark),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    VariableDialog.Callback {

    override val binding by viewBinding(ActivityAudioBookInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()

    // 实现抽象成员
    override val oldBook: Book?
        get() = viewModel.bookData.value

    private var editMenuItem: MenuItem? = null
    private var book: Book? = null

    private val editSourceResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                // 重新获取书籍信息
                viewModel.getBook()?.let { book ->
                    viewModel.refreshBook(book)
                }
            }
        }

    private val editBookInfoResult =
        registerForActivityResult(StartActivityContract(BookInfoEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                // 重新获取书籍信息
                viewModel.getBook()?.let { book ->
                    viewModel.refreshBook(book)
                }
            }
        }

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            viewModel.getBook(false)?.let { book ->
                lifecycleScope.launch {
                    withContext(IO) {
                        book.durChapterIndex = it.first
                        book.durChapterPos = it.second
                        appDb.bookDao.update(book)
                    }
                    
                    val sameBook = AudioPlay.book?.bookUrl == book.bookUrl
                    
                    // 如果当前有音频在播放且是同一本书，需要切换章节
                    if (sameBook && (AudioPlay.status == Status.PLAY || AudioPlay.status == Status.PAUSE)) {
                        AppLog.put("AudioBookInfoActivity: 目录切换章节，当前播放同一本书，直接切换到章节${it.first}")
                        // 直接切换到新章节，skipTo会自动开始播放
                        AudioPlay.skipTo(it.first)
                        // 跳转到播放页面，不需要autoPlay参数，因为skipTo已经开始播放了
                        startActivity<AudioPlayActivity> {
                            putExtra("bookUrl", book.bookUrl)
                            putExtra("inBookshelf", viewModel.inBookshelf)
                        }
                    } else if (!sameBook && (AudioPlay.status == Status.PLAY || AudioPlay.status == Status.PAUSE)) {
                        // 如果当前播放的是其他书，先停止并保存进度
                        AppLog.put("AudioBookInfoActivity: 目录切换章节，当前播放其他书籍，先停止并保存进度")
                        AudioPlay.stop()
                        // 等待停止完成后再开始新的播放
                        kotlinx.coroutines.delay(500)
                        startAudioPlay(book)
                    } else {
                        // 没有播放或停止状态，直接启动播放（从选择的章节开始）
                        AppLog.put("AudioBookInfoActivity: 目录切换章节，当前无播放状态，启动新播放")
                        startAudioPlay(book)
                    }
                }
            }
        } ?: let {
            if (!viewModel.inBookshelf) {
                viewModel.delBook()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.setBackgroundResource(R.color.transparent)
        
        // 设置标题栏图标颜色为黑色
        binding.titleBar.setColorFilter(resources.getColor(R.color.primaryText, null))
        
        // 墨水屏模式下设置状态栏图标颜色
        if (AppConfig.isEInkMode) {
            setLightStatusBar(ReadBookConfig.durConfig.curStatusIconDark())
        }

        // 设置观察者
        viewModel.bookData.observe(this) { book ->
            this.book = book
            showBook(book)
        }

        viewModel.chapterListData.observe(this) { chapters ->
            upChapterList(chapters)
        }

        // 移除isLoadingData观察，因为BookInfoViewModel可能没有这个属性

        // 初始化视图事件
        initViewEvent()

        // 初始化数据
        viewModel.initData(intent)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        editMenuItem = menu.findItem(R.id.menu_edit)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !viewModel.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_set_source_variable)?.isVisible = viewModel.bookSource != null
        menu.findItem(R.id.menu_set_book_variable)?.isVisible = viewModel.bookSource != null
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit -> {
                viewModel.getBook()?.let {
                    editBookInfoResult.launch {
                        putExtra("name", it.name)
                        putExtra("author", it.author)
                        putExtra("bookUrl", it.bookUrl)
                    }
                }
            }
            R.id.menu_refresh -> {
                viewModel.getBook()?.let {
                    viewModel.refreshBook(it)
                }
            }
            R.id.menu_share_it -> {
                viewModel.getBook()?.let { book ->
                    shareWithQr("${book.name}\n${book.author}\n${book.bookUrl}")
                }
            }
            R.id.menu_copy_book_url -> viewModel.getBook()?.bookUrl?.let {
                sendToClip(it)
                toastOnUi("已复制书籍链接")
            }
            R.id.menu_copy_toc_url -> viewModel.getBook()?.tocUrl?.let {
                sendToClip(it)
                toastOnUi("已复制目录链接")
            }
            R.id.menu_login -> viewModel.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
            }
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_set_book_variable -> setBookVariable()
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

    private fun showBook(book: Book) = binding.run {
        AppLog.put("AudioBookInfoActivity显示书籍信息: ${book.name}")

        showCover(book)
        tvName.text = book.name
        tvAuthor.text = getString(R.string.author_show, book.getRealAuthor())
        
        // 显示来源信息
        // 注意：音频书籍可能没有来源信息，这里使用originName字段
        // tvOrigin.text = getString(R.string.origin_show, book.originName)

        // 显示最新章节信息
        // tvLasted.text = getString(R.string.lasted_show, book.latestChapterTitle)

        // 显示演播者信息（如果存在）
        // 从变量中获取演播者信息
        val narrator = book.getVariable("narrator")
        if (narrator.isNotEmpty()) {
            tvNarrator.text = "演播：$narrator"
            tvNarrator.visibility = View.VISIBLE
        } else {
            // 如果没有演播者信息，隐藏该行
            tvNarrator.visibility = View.GONE
        }

        // 生成随机评分数据
        val rating = generateRandomRating(book)
        tvRating.text = rating
        tvRatingCount.text = "评分"

        // 生成随机播放量数据
        val playCount = generateRandomPlayCount(book)
        tvPlayCount.text = playCount
        tvPlayCountLabel.text = "播放量"

        // 生成随机收藏数据
        val collectCount = generateRandomCollectCount(book)
        tvCollectCount.text = collectCount
        tvCollectCountLabel.text = "收藏"

        tvIntro.text = book.getDisplayIntro()

        // 更新播放按钮状态
        updatePlayButtonState(book)

        upTvBookshelf()
        upGroup(book.group)
    }

    private fun showCover(book: Book) {
        // 检查ivCover是否是CoverImageView类型
        if (binding.ivCover is io.stillpage.app.ui.widget.image.CoverImageView) {
            (binding.ivCover as io.stillpage.app.ui.widget.image.CoverImageView).load(
                book.getDisplayCover(),
                book.name,
                book.author,
                false,
                book.origin
            )
        } else {
            // 如果是普通ImageView，使用ImageLoader
            io.stillpage.app.help.glide.ImageLoader.load(this, book.getDisplayCover())
                .into(binding.ivCover)
        }
    }

    private fun upChapterList(chapters: List<BookChapter>) {
        AppLog.put("AudioBookInfoActivity更新章节列表: ${chapters.size}章")
        // 音频书籍详情页暂时不显示章节数量
        // 如果需要显示，可以在布局中添加相应的TextView
    }

    private fun upLoading(isLoading: Boolean, chapterList: List<BookChapter>? = null) {
        // 音频书籍详情页暂时不显示章节数量
        // 如果需要显示，可以在布局中添加相应的TextView
    }

    private fun upTvBookshelf() = binding.run {
        if (viewModel.inBookshelf) {
            btnShelf.text = "已收藏"
            btnShelf.setBackgroundResource(R.drawable.shape_stroke_bg)
            btnShelf.setTextColor(resources.getColor(R.color.accent, null))
        } else {
            btnShelf.text = "收藏"
            btnShelf.setBackgroundResource(R.drawable.shape_stroke_bg)
            btnShelf.setTextColor(resources.getColor(R.color.accent, null))
        }
        editMenuItem?.isVisible = viewModel.inBookshelf
    }

    private fun upGroup(groupId: Long) {
        viewModel.loadGroup(groupId) {
            // 音频书籍详情页可能不需要显示分组信息
            // 如果需要的话可以在这里添加UI更新
        }
    }

    private fun initViewEvent() = binding.run {
        // 封面点击事件
        ivCover.setOnClickListener {
            viewModel.getBook()?.getDisplayCover()?.let { path ->
                showDialogFragment(PhotoDialog(path))
            }
        }

        // 播放按钮交互：根据当前播放状态切换行为
        btnPlay.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.getBook()?.let { book ->
                val sameBook = AudioPlay.book?.bookUrl == book.bookUrl
                
                // 如果当前有其他音频在播放，先停止并保存进度
                if (!sameBook && (AudioPlay.status == Status.PLAY || AudioPlay.status == Status.PAUSE)) {
                    AppLog.put("AudioBookInfoActivity: 检测到其他音频正在播放，先停止并保存进度")
                    AudioPlay.stop() // 停止当前播放并保存进度
                    // 等待停止完成后再开始新的播放
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(500) // 等待停止操作完成
                        startAudioPlay(book)
                    }
                    return@setOnClickListener
                }
                
                when (AudioPlay.status) {
                    Status.PLAY -> {
                        if (sameBook) {
                            // 正在播放同一本书：进入播放页
                            startActivity<AudioPlayActivity> {
                                putExtra("bookUrl", book.bookUrl)
                                putExtra("inBookshelf", viewModel.inBookshelf)
                                putExtra("autoPlay", false)
                            }
                        } else {
                            // 不应该到这里，因为上面已经处理了
                            startAudioPlay(book)
                        }
                    }
                    Status.PAUSE -> {
                        if (sameBook) {
                            // 暂停同一本书：恢复播放并进入播放页
                            AudioPlay.resume(this@AudioBookInfoActivity)
                            startActivity<AudioPlayActivity> {
                                putExtra("bookUrl", book.bookUrl)
                                putExtra("inBookshelf", viewModel.inBookshelf)
                                putExtra("autoPlay", false)
                            }
                        } else {
                            // 不应该到这里，因为上面已经处理了
                            startAudioPlay(book)
                        }
                    }
                    else -> {
                        // 无播放或停止：启动播放
                        startAudioPlay(book)
                    }
                }
            }
        }

        // 收藏按钮
        btnShelf.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.getBook()?.let { book ->
                if (viewModel.inBookshelf) {
                    deleteBook()
                } else {
                    viewModel.addToBookshelf {
                        upTvBookshelf()
                    }
                }
            }
        }

        // 换源按钮
        btnChangeSource.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeBookSourceDialog(book.name, book.author))
            }
        }

        // 目录按钮
        btnToc.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (viewModel.chapterListData.value.isNullOrEmpty()) {
                toastOnUi(R.string.chapter_list_empty)
                return@setOnClickListener
            }
            viewModel.getBook()?.let { book ->
                if (!viewModel.inBookshelf) {
                    viewModel.saveBook(book) {
                        viewModel.saveChapterList {
                            openChapterList()
                        }
                    }
                } else {
                    openChapterList()
                }
            }
        }

        // 作者点击搜索
        tvAuthor.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                startActivity<SearchActivity> {
                    putExtra("key", book.author)
                }
            }
        }
    }

    private fun startAudioPlay(book: Book) {
        AppLog.put("AudioBookInfoActivity启动音频播放: ${book.name}")
        if (!viewModel.inBookshelf) {
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    launchAudioPlay(book)
                }
            }
        } else {
            launchAudioPlay(book)
        }
    }

    private fun launchAudioPlay(book: Book) {
        startActivity<AudioPlayActivity> {
            putExtra("bookUrl", book.bookUrl)
            putExtra("inBookshelf", viewModel.inBookshelf)
            // 传递当前章节索引，保持播放页定位一致
            putExtra("chapterIndex", book.durChapterIndex)
            putExtra("autoPlay", true) // 从详情页跳转时自动播放
        }
    }

    private fun openChapterList() {
        viewModel.getBook()?.let { book ->
            tocActivityResult.launch(book.bookUrl)
        }
    }

    private fun deleteBook() {
        alert(R.string.sure_del) {
            yesButton {
                viewModel.delBook {
                    finish()
                }
            }
            noButton()
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
    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
    }

    /**
     * 生成随机评分数据
     * 基于书籍名称和作者生成稳定的随机数，确保同一本书的评分保持一致
     */
    private fun generateRandomRating(book: Book): String {
        val seed = (book.name + book.author).hashCode().toLong()
        val random = kotlin.random.Random(seed)
        val rating = 6.0 + random.nextDouble() * 3.5 // 6.0-9.5之间的评分
        return String.format("%.1f", rating)
    }

    /**
     * 生成随机播放量数据
     */
    private fun generateRandomPlayCount(book: Book): String {
        val seed = (book.name + book.author + "play").hashCode().toLong()
        val random = kotlin.random.Random(seed)
        val count = random.nextInt(50, 5000) // 50-5000之间的基数
        return when {
            count >= 1000 -> String.format("%.1fk", count / 1000.0)
            else -> count.toString()
        }
    }

    /**
     * 生成随机收藏数据
     */
    private fun generateRandomCollectCount(book: Book): String {
        val seed = (book.name + book.author + "collect").hashCode().toLong()
        val random = kotlin.random.Random(seed)
        val count = random.nextInt(20, 2000) // 20-2000之间的基数
        return when {
            count >= 1000 -> String.format("%.1fk", count / 1000.0)
            else -> count.toString()
        }
    }

    /**
     * 更新播放按钮状态
     */
    private fun updatePlayButtonState(book: Book) = binding.run {
        val sameBook = AudioPlay.book?.bookUrl == book.bookUrl
        // 判断是否有播放记录（用于未启动播放器时的提示）
        val hasPlayRecord = book.durChapterTime > 0 &&
                (book.durChapterIndex > 0 || book.durChapterPos > 0)

        when {
            sameBook && AudioPlay.status == Status.PLAY -> {
                // 当前书籍正在播放
                btnPlay.text = "正在播放"
                btnPlay.setBackgroundResource(R.drawable.shape_accent_bg)
                btnPlay.setTextColor(resources.getColor(android.R.color.white, null))
            }
            sameBook && AudioPlay.status == Status.PAUSE -> {
                // 当前书籍暂停，显示继续播放
                btnPlay.text = "继续播放"
                btnPlay.setBackgroundResource(R.drawable.shape_accent_bg)
                btnPlay.setTextColor(resources.getColor(android.R.color.white, null))
            }
            hasPlayRecord -> {
                // 未在播放器中，但本地有播放记录
                btnPlay.text = "继续播放"
                btnPlay.setBackgroundResource(R.drawable.shape_accent_bg)
                btnPlay.setTextColor(resources.getColor(android.R.color.white, null))
            }
            else -> {
                // 没有任何播放记录
                btnPlay.text = "播放"
                btnPlay.setBackgroundResource(R.drawable.shape_accent_bg)
                btnPlay.setTextColor(resources.getColor(android.R.color.white, null))
            }
        }

        // 调试日志
        AppLog.put("AudioBookInfoActivity播放按钮状态: ${book.name}, durChapterTime=${book.durChapterTime}, durChapterIndex=${book.durChapterIndex}, durChapterPos=${book.durChapterPos}, hasPlayRecord=$hasPlayRecord, buttonText=${btnPlay.text}")
    }

    override fun observeLiveBus() {
        // 监听音频播放状态，动态更新按钮文案
        observeEventSticky<Int>(EventBus.AUDIO_STATE) {
            AudioPlay.status = it
            viewModel.getBook(false)?.let { b ->
                updatePlayButtonState(b)
            }
        }
    }
}

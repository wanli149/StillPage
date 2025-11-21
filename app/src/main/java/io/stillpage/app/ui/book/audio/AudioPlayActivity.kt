package io.stillpage.app.ui.book.audio

import android.annotation.SuppressLint
import android.icu.text.SimpleDateFormat
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.BookType
import io.stillpage.app.constant.EventBus
import io.stillpage.app.constant.Status
import io.stillpage.app.constant.Theme
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.databinding.ActivityAudioPlayBinding
import io.stillpage.app.help.book.isAudio
import io.stillpage.app.help.book.removeType
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.config.ReadBookConfig
import io.stillpage.app.utils.setLightStatusBar
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.model.AudioPlay
import io.stillpage.app.model.BookCover
import io.stillpage.app.service.AudioPlayService
import io.stillpage.app.constant.AppLog
import io.stillpage.app.ui.about.AppLogDialog
import io.stillpage.app.ui.book.changesource.ChangeBookSourceDialog
import io.stillpage.app.ui.book.source.edit.BookSourceEditActivity
import io.stillpage.app.ui.book.toc.TocActivityResult
import io.stillpage.app.ui.login.SourceLoginActivity
import io.stillpage.app.ui.widget.seekbar.SeekBarChangeListener
import io.stillpage.app.utils.StartActivityContract
import io.stillpage.app.utils.applyNavigationBarPadding
import io.stillpage.app.utils.dpToPx
import io.stillpage.app.utils.invisible
import io.stillpage.app.utils.observeEvent
import io.stillpage.app.utils.observeEventSticky
import io.stillpage.app.utils.sendToClip
import io.stillpage.app.utils.showDialogFragment
import io.stillpage.app.utils.startActivity
import io.stillpage.app.utils.startActivityForBook
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import io.stillpage.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onLongClick
import java.util.Locale

/**
 * 音频播放
 */
@SuppressLint("ObsoleteSdkInt")
class AudioPlayActivity :
    VMBaseActivity<ActivityAudioPlayBinding, AudioPlayViewModel>(toolBarTheme = Theme.Dark),
    ChangeBookSourceDialog.CallBack,
    AudioPlay.CallBack {

    override val binding by viewBinding(ActivityAudioPlayBinding::inflate)
    override val viewModel by viewModels<AudioPlayViewModel>()
    private val timerSliderPopup by lazy { TimerSliderPopup(this) }
    private var adjustProgress = false
    private var playMode = AudioPlay.PlayMode.LIST_END_STOP
    private var currentSpeed = AppConfig.audioPlaySpeed

    private val progressTimeFormat by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SimpleDateFormat("mm:ss", Locale.getDefault())
        } else {
            java.text.SimpleDateFormat("mm:ss", Locale.getDefault())
        }
    }
    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            if (it.first != AudioPlay.book?.durChapterIndex
                || it.second == 0
            ) {
                AudioPlay.skipTo(it.first)
            }
        }
    }
    private val sourceEditResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upSource()
            }
        }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // 边到边布局与状态栏透明（替换已弃用的 systemUiVisibility）
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
        
        // 墨水屏模式下设置状态栏图标颜色
        if (AppConfig.isEInkMode) {
            setLightStatusBar(ReadBookConfig.durConfig.curStatusIconDark())
        }

        AudioPlay.register(this)
        viewModel.titleData.observe(this) {
            binding.tvBookName.text = it
        }
        viewModel.coverData.observe(this) {
            upCover(it)
        }
        // 先初始化数据，再设置观察者
        viewModel.initData(intent)
        initView()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.audio_play, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !AudioPlay.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_wake_lock)?.isChecked = AppConfig.audioPlayUseWakeLock
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source -> AudioPlay.book?.let {
                showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
            }

            R.id.menu_login -> AudioPlay.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
            }

            R.id.menu_wake_lock -> AppConfig.audioPlayUseWakeLock = !AppConfig.audioPlayUseWakeLock
            R.id.menu_copy_audio_url -> sendToClip(AudioPlayService.url)
            R.id.menu_edit_source -> AudioPlay.bookSource?.let {
                sourceEditResult.launch {
                    putExtra("sourceUrl", it.bookSourceUrl)
                }
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    // 使用 OnBackPressedDispatcher 替代已弃用的 onBackPressed
    private fun registerOnBackPressedDispatcher() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initView() {
        // 注册系统返回键回调
        registerOnBackPressedDispatcher()
        // 顶部导航栏
        binding.btnBack.setOnClickListener {
            finish()
        }
        binding.btnShare.setOnClickListener {
            val book = AudioPlay.book
            val shareUrl = AudioPlay.durPlayUrl.ifBlank { book?.bookUrl.orEmpty() }
            val text = if (book != null && shareUrl.isNotBlank()) {
                "${book.name}\n$shareUrl"
            } else {
                book?.name ?: getString(R.string.app_name)
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        }
        binding.btnMore.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showMoreMenu(v)
        }

        // 功能按钮
        binding.btnTimer.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            animateButtonClick(v) {
                TimerWheelPickerDialog.show(this)
            }
        }
        // 首帧同步持久化的播放速度到 UI
        runCatching {
            currentSpeed = AppConfig.audioPlaySpeed
            binding.tvSpeed.text = String.format(Locale.ROOT, "%.1fX", currentSpeed)
            binding.tvSpeed.visible()
        }
        
        // 播放速度控制按钮
        findViewById<View>(R.id.btn_speed_down).setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            animateButtonClick(v) {
                // 减速：每次减少0.25x，最小0.5x
                val newSpeed = kotlin.math.max(0.5f, currentSpeed - 0.25f)
                val adjust = newSpeed - currentSpeed
                if (adjust != 0f) {
                    AudioPlay.adjustSpeed(adjust)
                }
            }
        }
        
        findViewById<View>(R.id.btn_speed_up).setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            animateButtonClick(v) {
                // 加速：每次增加0.25x，最大3.0x
                val newSpeed = kotlin.math.min(3.0f, currentSpeed + 0.25f)
                val adjust = newSpeed - currentSpeed
                if (adjust != 0f) {
                    AudioPlay.adjustSpeed(adjust)
                }
            }
        }
        binding.btnMode.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            animateButtonClick(v) {
                AudioPlay.changePlayMode()
            }
        }
        binding.btnChapterList.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            animateButtonClick(v) {
                AudioPlay.book?.let {
                    AudioTocDialog.show(this, object : AudioTocDialog.Callback {
                        override fun onChapterSelected(chapterIndex: Int, chapterPos: Int) {
                            AudioPlay.skipTo(chapterIndex)
                        }
                    })
                }
            }
        }

        // 播放模式变化监听
        observeEventSticky<AudioPlay.PlayMode>(EventBus.PLAY_MODE_CHANGED) {
            playMode = it
            updatePlayModeIcon()
        }

        // 播放控制按钮
        binding.fabPlayStop.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            playButton()
        }
        binding.fabPlayStop.setOnLongClickListener {
            AudioPlay.stop()
            true
        }
        binding.ivSkipNext.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            AudioPlay.next()
        }
        binding.ivSkipPrevious.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            AudioPlay.prev()
        }
        binding.ivFastForward.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            animateButtonClick(v) {
                // 快进15秒
                AudioPlay.adjustProgress(AudioPlay.durChapterPos + 15000)
            }
        }
        binding.ivFastRewind.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            animateButtonClick(v) {
                // 快退15秒
                AudioPlay.adjustProgress(kotlin.math.max(0, AudioPlay.durChapterPos - 15000))
            }
        }

        // 进度条监听
        binding.playerProgress.setOnSeekBarChangeListener(object : SeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvDurTime.text = progressTimeFormat.format(progress.toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                adjustProgress = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                adjustProgress = false
                AudioPlay.adjustProgress(seekBar.progress)
            }
        })

        // 兼容旧版本Android
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            binding.ivFastRewind.invisible()
            binding.ivFastForward.invisible()
        }
    }

    private fun updatePlayModeIcon() {
        // 添加播放模式切换动画
        binding.btnMode.animate()
            .rotationY(90f)
            .setDuration(150)
            .withEndAction {
                binding.btnMode.setImageResource(playMode.iconRes)
                binding.btnMode.rotationY = -90f
                binding.btnMode.animate()
                    .rotationY(0f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun upCover(path: String?) {
        // 显示加载状态
        showCoverLoading(true)
        
        if (path.isNullOrBlank()) {
            io.stillpage.app.help.glide.ImageLoader.load(this, BookCover.defaultDrawable)
                .into(binding.ivCover)
            showCoverLoading(false)
            // 为默认封面也添加淡入动画，保持一致性
            binding.ivCover.alpha = 0f
            binding.ivCover.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
            return
        }
        
        BookCover.load(
            context = this, 
            path = path, 
            sourceOrigin = AudioPlay.bookSource?.bookSourceUrl,
            onLoadFinish = {
                showCoverLoading(false)
                // 添加封面加载完成的淡入动画
                binding.ivCover.alpha = 0f
                binding.ivCover.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
        ).into(binding.ivCover)
    }
    
    /**
     * 显示/隐藏封面加载状态
     */
    private fun showCoverLoading(show: Boolean) {
        if (show) {
            binding.progressCoverLoading.visibility = View.VISIBLE
            binding.progressCoverLoading.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        } else {
            binding.progressCoverLoading.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    binding.progressCoverLoading.visibility = View.GONE
                }
                .start()
        }
    }
    
    /**
     * 通用按钮点击动画
     */
    private fun animateButtonClick(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                action.invoke()
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }
    
    /**
     * 显示更多菜单 - 复用现有的选项菜单
     */
    private fun showMoreMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.audio_play, popup.menu)
        
        // 动态设置菜单项的可见性和状态，复用现有逻辑
        val menu = popup.menu
        menu.findItem(R.id.menu_login)?.isVisible = !AudioPlay.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_wake_lock)?.isChecked = AppConfig.audioPlayUseWakeLock
        
        // 设置菜单项点击监听器，复用现有的处理逻辑
        popup.setOnMenuItemClickListener { item ->
            onCompatOptionsItemSelected(item)
        }
        
        // 显示菜单
        popup.show()
    }

    private fun playButton() {
        when (AudioPlay.status) {
            Status.PLAY -> AudioPlay.pause(this)
            Status.PAUSE -> AudioPlay.resume(this)
            else -> AudioPlay.loadOrUpPlayUrl()
        }
    }

    private fun updatePlayState(status: Int) {
        val targetIcon = when (status) {
            Status.PLAY -> R.drawable.ic_pause
            Status.PAUSE -> R.drawable.ic_play_24dp
            else -> R.drawable.ic_play_24dp
        }
        
        // 添加平滑的状态切换动画
        animatePlayButtonState(targetIcon, status != Status.STOP)
    }
    
    /**
     * 播放按钮状态切换动画
     */
    private fun animatePlayButtonState(targetIcon: Int, hideLoading: Boolean = true) {
        // 缩放动画 + 图标切换
        binding.fabPlayStop.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(120)
            .withEndAction {
                binding.fabPlayStop.setImageResource(targetIcon)
                binding.fabPlayStop.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
            .start()
            
        // 隐藏加载指示器
        if (hideLoading) {
            binding.progressLoading.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    binding.progressLoading.visibility = View.GONE
                    binding.progressLoading.alpha = 1f
                }
                .start()
        }
    }

    override val oldBook: Book?
        get() = AudioPlay.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (book.isAudio) {
            viewModel.changeTo(source, book, toc)
        } else {
            AudioPlay.stop()
            lifecycleScope.launch {
                withContext(IO) {
                    AudioPlay.book?.migrateTo(book, toc)
                    book.removeType(BookType.updateError)
                    AudioPlay.book?.delete()
                    appDb.bookDao.insert(book)
                }
                startActivityForBook(book)
                finish()
            }
        }
    }

    override fun finish() {
        val book = AudioPlay.book ?: return super.finish()

        if (AudioPlay.inBookshelf) {
            return super.finish()
        }

        if (!AppConfig.showAddToShelfAlert) {
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    AudioPlay.book?.removeType(BookType.notShelf)
                    AudioPlay.book?.save()
                    AudioPlay.inBookshelf = true
                    setResult(RESULT_OK)
                }
                noButton { viewModel.removeFromBookshelf { super.finish() } }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保在Activity销毁时保存进度
        AppLog.put("AudioPlayActivity: Activity销毁，保存进度")
        AudioPlay.saveRead()
        
        // 清理所有正在进行的动画，避免内存泄漏
        binding.fabPlayStop.clearAnimation()
        binding.btnMode.clearAnimation()
        binding.ivCover.clearAnimation()
        binding.progressLoading.clearAnimation()
        binding.progressCoverLoading.clearAnimation()
        
        if (AudioPlay.status != Status.PLAY) {
            AudioPlay.stop()
        }
        AudioPlay.unregister(this)
    }

    @SuppressLint("SetTextI18n")
    override fun observeLiveBus() {
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                playButton()
            }
        }
        observeEventSticky<Int>(EventBus.AUDIO_STATE) {
            AudioPlay.status = it
            updatePlayState(it)
        }
        observeEventSticky<String>(EventBus.AUDIO_SUB_TITLE) {
            binding.tvSubTitle.text = it
            binding.ivSkipPrevious.isEnabled = AudioPlay.durChapterIndex > 0
            binding.ivSkipNext.isEnabled =
                AudioPlay.durChapterIndex < AudioPlay.simulatedChapterSize - 1
        }
        observeEventSticky<Int>(EventBus.AUDIO_SIZE) {
            binding.playerProgress.max = it
            binding.tvAllTime.text = progressTimeFormat.format(it.toLong())
        }
        observeEventSticky<Int>(EventBus.AUDIO_PROGRESS) {
            if (!adjustProgress) binding.playerProgress.progress = it
            binding.tvDurTime.text = progressTimeFormat.format(it.toLong())
        }
        observeEventSticky<Int>(EventBus.AUDIO_BUFFER_PROGRESS) {
            binding.playerProgress.secondaryProgress = it

        }
        observeEventSticky<Float>(EventBus.AUDIO_SPEED) {
            currentSpeed = it
            binding.tvSpeed.text = String.format(Locale.ROOT, "%.1fX", it)
            binding.tvSpeed.visible()
        }
        observeEventSticky<Int>(EventBus.AUDIO_DS) {
            binding.tvTimer.text = "${it}m"
            binding.tvTimer.visible(it > 0)
        }
    }

    override fun upLoading(loading: Boolean) {
        runOnUiThread {
            if (loading) {
                binding.progressLoading.visibility = View.VISIBLE
                binding.progressLoading.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            } else {
                binding.progressLoading.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        binding.progressLoading.visibility = View.GONE
                        binding.progressLoading.alpha = 1f
                    }
                    .start()
            }
        }
    }

}

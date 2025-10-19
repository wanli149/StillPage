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
        binding.btnMore.setOnClickListener {
            // TODO: 显示更多菜单
        }

        // 功能按钮
        binding.btnTimer.setOnClickListener {
            timerSliderPopup.showAsDropDown(it, 0, (-100).dpToPx(), Gravity.TOP)
        }
        // 首帧同步持久化的播放速度到 UI
        runCatching {
            currentSpeed = AppConfig.audioPlaySpeed
            binding.tvSpeed.text = String.format(Locale.ROOT, "%.1fX", currentSpeed)
            binding.tvSpeed.visible()
        }
        binding.btnSpeed.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            // 循环预设速度：0.75x、1.0x、1.25x、1.5x、2.0x
            val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            // 找到当前速度的索引（取最接近值）
            val idx = speeds.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.051f }
            val next = speeds[(if (idx >= 0) idx + 1 else 1) % speeds.size]
            val adjust = next - currentSpeed
            AudioPlay.adjustSpeed(adjust)
        }
        binding.btnMode.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            AudioPlay.changePlayMode()
        }
        binding.btnChapterList.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            AudioPlay.book?.let {
                AudioTocDialog.show(this, object : AudioTocDialog.Callback {
                    override fun onChapterSelected(chapterIndex: Int, chapterPos: Int) {
                        AudioPlay.skipTo(chapterIndex)
                    }
                })
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
            AudioPlay.adjustSpeed(0.1f)
        }
        binding.ivFastRewind.setOnClickListener { v ->
            if (AppConfig.enableHaptics) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            AudioPlay.adjustSpeed(-0.1f)
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
        binding.btnMode.setImageResource(playMode.iconRes)
    }

    private fun upCover(path: String?) {
        if (path.isNullOrBlank()) {
            io.stillpage.app.help.glide.ImageLoader.load(this, BookCover.defaultDrawable)
                .into(binding.ivCover)
            return
        }
        BookCover.load(this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl)
            .placeholder(R.drawable.image_cover_default)
            .error(R.drawable.image_cover_default)
            .into(binding.ivCover)
    }

    private fun playButton() {
        when (AudioPlay.status) {
            Status.PLAY -> AudioPlay.pause(this)
            Status.PAUSE -> AudioPlay.resume(this)
            else -> AudioPlay.loadOrUpPlayUrl()
        }
    }

    private fun updatePlayState(status: Int) {
        when (status) {
            Status.PLAY -> {
                // 正在播放，显示暂停图标
                binding.fabPlayStop.setImageResource(R.drawable.ic_pause)
                binding.progressLoading.visibility = View.GONE
            }
            Status.PAUSE -> {
                // 暂停状态，显示播放图标
                binding.fabPlayStop.setImageResource(R.drawable.ic_play_24dp)
                binding.progressLoading.visibility = View.GONE
            }
            else -> {
                // 停止或其他状态，显示播放图标
                binding.fabPlayStop.setImageResource(R.drawable.ic_play_24dp)
                binding.progressLoading.visibility = View.GONE
            }
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
            binding.progressLoading.visible(loading)
        }
    }

}

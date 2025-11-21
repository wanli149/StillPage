@file:Suppress("DEPRECATION")

package io.stillpage.app.ui.main

import android.os.Bundle
import android.text.format.DateUtils
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.stillpage.app.BuildConfig
import io.stillpage.app.R
import io.stillpage.app.constant.AppLog
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.AppConst.appInfo
import io.stillpage.app.constant.EventBus
import io.stillpage.app.constant.PreferKey
import io.stillpage.app.databinding.ActivityMainBinding
import io.stillpage.app.databinding.DialogEditTextBinding
import io.stillpage.app.help.AppWebDav
import io.stillpage.app.help.book.BookHelp
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.config.LocalConfig
import io.stillpage.app.help.coroutine.Coroutine
import io.stillpage.app.help.storage.Backup
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.lib.theme.elevation
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.service.BaseReadAloudService
import io.stillpage.app.ui.about.CrashLogsDialog
import io.stillpage.app.ui.main.bookshelf.BaseBookshelfFragment
import io.stillpage.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.stillpage.app.ui.main.bookshelf.style2.BookshelfFragment2
import io.stillpage.app.ui.main.explore.ExploreFragment
import io.stillpage.app.ui.main.explore.ExploreNewFragment

import io.stillpage.app.ui.main.my.MyFragment
import io.stillpage.app.ui.main.rss.RssFragment
import io.stillpage.app.ui.widget.dialog.TextDialog
import io.stillpage.app.utils.hideSoftInput
import io.stillpage.app.utils.isCreated
import io.stillpage.app.utils.navigationBarHeight
import io.stillpage.app.utils.observeEvent
import io.stillpage.app.utils.setEdgeEffectColor
import io.stillpage.app.utils.setOnApplyWindowInsetsListenerCompat
import io.stillpage.app.utils.shouldHideSoftInput
import io.stillpage.app.utils.showDialogFragment
import io.stillpage.app.utils.toastOnUi
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 主界面
 */
class MainActivity : VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    private val idBookshelf = 0
    private val idBookshelf1 = 11
    private val idBookshelf2 = 12
    private val idExplore = 1
    private val idRss = 2
    private val idMy = 3
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 4
    private val realPositions = arrayOf(idBookshelf, idExplore, idRss, idMy)
    private val adapter by lazy {
        TabFragmentPageAdapter(supportFragmentManager)
    }
    private val onUpBooksBadgeView by lazy {
        binding.bottomNavigationView.addBadgeView(0)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        upBottomMenu()
        initView()
        upHomePage()
        onBackPressedDispatcher.addCallback(this) {
            if (pagePosition != 0) {
                try {
                    binding.viewPagerMain.currentItem = 0
                } catch (e: IllegalArgumentException) {
                    // Fragment可能还没有正确附加到FragmentManager，忽略这个错误
                    // 直接退出应用
                    finish()
                }
                return@addCallback
            }
            (fragmentMap[getFragmentId(0)] as? BookshelfFragment2)?.let {
                if (it.back()) {
                    return@addCallback
                }
            }
            if (System.currentTimeMillis() - exitTime > 2000) {
                toastOnUi(R.string.double_click_exit)
                exitTime = System.currentTimeMillis()
            } else {
                if (BaseReadAloudService.pause) {
                    finish()
                } else {
                    moveTaskToBack(true)
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            currentFocus?.let {
                if (it.shouldHideSoftInput(ev)) {
                    it.post {
                        it.clearFocus()
                        it.hideSoftInput()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            //隐私协议
            if (!privacyPolicy()) return@launch
            //版本更新
            upVersion()
            //设置本地密码
            setLocalPassword()
            notifyAppCrash()
            //备份同步
            backupSync()
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                binding.viewPagerMain.postDelayed(1000) {
                    viewModel.upAllBookToc()
                }
            }
            binding.viewPagerMain.postDelayed(3000) {
                viewModel.postLoad()
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = binding.run {
        when (item.itemId) {
            R.id.menu_bookshelf ->
                viewPagerMain.setCurrentItem(0, false)

            R.id.menu_discovery ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)

            R.id.menu_rss ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)

            R.id.menu_my_config ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
        return false
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_bookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.gotoTop()
                }
            }

            R.id.menu_discovery -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[1] as? ExploreFragment)?.compressExplore()
                }
            }
        }
    }

    private fun initView() = binding.run {
        viewPagerMain.setEdgeEffectColor(primaryColor)
        viewPagerMain.offscreenPageLimit = 3
        viewPagerMain.adapter = adapter
        viewPagerMain.addOnPageChangeListener(PageChangeCallback())
        bottomNavigationView.elevation = elevation
        bottomNavigationView.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNavigationView.setOnNavigationItemReselectedListener(this@MainActivity)
        if (AppConfig.isEInkMode) {
            bottomNavigationView.setBackgroundResource(R.drawable.bg_eink_border_top)
        }
        bottomNavigationView.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val height = windowInsets.navigationBarHeight
            view.bottomPadding = height
            windowInsets.inset(0, 0, 0, height)
        }
    }

    /**
     * 用户隐私与协议
     */
    private suspend fun privacyPolicy(): Boolean = suspendCoroutine { block ->
        if (LocalConfig.privacyPolicyOk) {
            block.resume(true)
            return@suspendCoroutine
        }
        val privacyPolicy = String(assets.open("privacyPolicy.md").readBytes())
        alert(getString(R.string.privacy_policy), privacyPolicy) {
            positiveButton(R.string.agree) {
                LocalConfig.privacyPolicyOk = true
                block.resume(true)
            }
            negativeButton(R.string.refuse) {
                finish()
                block.resume(false)
            }
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCoroutine { block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            block.resume(null)
            return@suspendCoroutine
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = String(assets.open("web/help/md/appHelp.md").readBytes())
            val dialog = TextDialog(getString(R.string.help), help, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else if (!BuildConfig.DEBUG) {
            val log = String(assets.open("updateLog.md").readBytes())
            val dialog = TextDialog(getString(R.string.update_log), log, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else {
            block.resume(null)
        }
    }

    /**
     * 设置本地密码
     */
    private suspend fun setLocalPassword() = suspendCoroutine { block ->
        if (LocalConfig.password != null) {
            block.resume(null)
            return@suspendCoroutine
        }
        alert(R.string.set_local_password, R.string.set_local_password_summary) {
            val editTextBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "password"
            }
            customView {
                editTextBinding.root
            }
            onDismiss {
                block.resume(null)
            }
            okButton {
                LocalConfig.password = editTextBinding.editView.text.toString()
            }
            cancelButton {
                LocalConfig.password = ""
            }
        }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    /**
     * 如果重启太快fragment不会重建,这里更新一下书架的排序
     */
    override fun recreate() {
        (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.run {
            upSort()
        }
        super.recreate()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            onUpBooksBadgeView.setBadgeCount(it)
        }
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                upBottomMenu()
                // 强制刷新ViewPager以应用Fragment变化
                adapter.notifyDataSetChanged()
                if (it) {
                    viewPagerMain.setCurrentItem(bottomMenuCount - 1, false)
                }
            }
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
    }

    private fun upBottomMenu() {
        val showDiscovery = AppConfig.showDiscovery || AppConfig.useNewExplore
        val showRss = AppConfig.showRSS
        binding.bottomNavigationView.menu.let { menu ->
            menu.findItem(R.id.menu_discovery).isVisible = showDiscovery
            menu.findItem(R.id.menu_rss).isVisible = showRss
        }
        var index = 0
        if (showDiscovery) {
            index++
            realPositions[index] = idExplore
        }
        if (showRss) {
            index++
            realPositions[index] = idRss
        }
        index++
        realPositions[index] = idMy
        bottomMenuCount = index + 1
        adapter.notifyDataSetChanged()
    }

    private fun upHomePage() {
        when (AppConfig.defaultHomePage) {
            "bookshelf" -> {}
            "explore" -> if (AppConfig.showDiscovery || AppConfig.useNewExplore) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)
            }

            "rss" -> if (AppConfig.showRSS) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)
            }

            "my" -> binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return if (AppConfig.bookGroupStyle == 1) idBookshelf2 else idBookshelf1
        }
        return id
    }

    private inner class PageChangeCallback : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            pagePosition = position
            binding.bottomNavigationView.menu
                .getItem(realPositions[position]).isChecked = true
        }

    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        private fun getId(position: Int): Int {
            return getFragmentId(position)
        }

        override fun getItemPosition(any: Any): Int {
            val position = (any as MainFragmentInterface).position
                ?: return POSITION_NONE
            val fragmentId = getId(position)
            if ((fragmentId == idBookshelf1 && any is BookshelfFragment1)
                || (fragmentId == idBookshelf2 && any is BookshelfFragment2)
                || (fragmentId == idExplore && isCorrectExploreFragment(any))
                || (fragmentId == idRss && any is RssFragment)
                || (fragmentId == idMy && any is MyFragment)
            ) {
                return POSITION_UNCHANGED
            }
            return POSITION_NONE
        }

        private fun isCorrectExploreFragment(fragment: Any): Boolean {
            val result = when {
                AppConfig.useNewExplore -> {
                    AppLog.put("MainActivity：检查Fragment类型 - 期望ExploreNewFragment，实际：${fragment::class.simpleName}")
                    fragment is ExploreNewFragment
                }
                AppConfig.showDiscovery -> {
                    AppLog.put("MainActivity：检查Fragment类型 - 期望ExploreFragment，实际：${fragment::class.simpleName}")
                    fragment is ExploreFragment
                }
                else -> {
                    AppLog.put("MainActivity：检查Fragment类型 - 默认期望ExploreFragment，实际：${fragment::class.simpleName}")
                    fragment is ExploreFragment // 默认使用传统发现页面
                }
            }
            AppLog.put("MainActivity：Fragment类型检查结果：$result")
            return result
        }

        override fun getItem(position: Int): Fragment {
            return when (getId(position)) {
                idBookshelf1 -> BookshelfFragment1(position)
                idBookshelf2 -> BookshelfFragment2(position)
                idExplore -> {
                    AppLog.put("MainActivity：准备创建发现页面Fragment - useNewExplore=${AppConfig.useNewExplore}, showDiscovery=${AppConfig.showDiscovery}")
                    when {
                        AppConfig.useNewExplore -> {
                            AppLog.put("MainActivity：创建新版发现页面Fragment")
                            ExploreNewFragment(position)
                        }
                        AppConfig.showDiscovery -> {
                            AppLog.put("MainActivity：创建传统发现页面Fragment")
                            ExploreFragment(position)
                        }
                        else -> {
                            // 如果都关闭了，默认显示传统发现页面
                            AppLog.put("MainActivity：发现页面关闭，默认显示传统发现页面")
                            ExploreFragment(position)
                        }
                    }
                }
                idRss -> RssFragment(position)
                else -> MyFragment(position)
            }
        }

        override fun getCount(): Int {
            return bottomMenuCount
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as Fragment
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as Fragment
            }
            fragmentMap[getId(position)] = fragment
            return fragment
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            try {
                super.setPrimaryItem(container, position, `object`)
            } catch (e: IllegalArgumentException) {
                // Fragment可能还没有正确附加到FragmentManager，忽略这个错误
                // 这通常发生在快速切换页面或返回键操作时
            }
        }

    }

}
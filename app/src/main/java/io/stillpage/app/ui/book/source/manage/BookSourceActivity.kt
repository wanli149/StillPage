package io.stillpage.app.ui.book.source.manage

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SubMenu
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.EventBus
import io.stillpage.app.data.AppDatabase
import io.stillpage.app.data.appDb
import io.stillpage.app.data.entities.BookSourcePart
import io.stillpage.app.databinding.ActivityBookSourceBinding
import io.stillpage.app.databinding.DialogEditTextBinding
import io.stillpage.app.help.DirectLinkUpload
import io.stillpage.app.help.config.LocalConfig
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.lib.theme.primaryTextColor
import io.stillpage.app.model.CheckSource
import io.stillpage.app.model.Debug
import io.stillpage.app.ui.association.ImportBookSourceDialog
import io.stillpage.app.ui.book.search.SearchActivity
import io.stillpage.app.ui.book.search.SearchScope
import io.stillpage.app.ui.book.source.debug.BookSourceDebugActivity
import io.stillpage.app.ui.book.source.edit.BookSourceEditActivity
import io.stillpage.app.ui.config.CheckSourceConfig
import io.stillpage.app.ui.file.HandleFileContract
import io.stillpage.app.ui.qrcode.QrCodeResult
import io.stillpage.app.ui.widget.SelectActionBar
import io.stillpage.app.ui.widget.recycler.DragSelectTouchHelper
import io.stillpage.app.ui.widget.recycler.ItemTouchCallback
import io.stillpage.app.ui.widget.recycler.VerticalDivider
import io.stillpage.app.utils.ACache
import io.stillpage.app.utils.NetworkUtils
import io.stillpage.app.utils.applyTint
import io.stillpage.app.utils.cnCompare
import io.stillpage.app.utils.dpToPx
import io.stillpage.app.utils.flowWithLifecycleAndDatabaseChange
import io.stillpage.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.stillpage.app.utils.hideSoftInput
import io.stillpage.app.utils.isAbsUrl
import io.stillpage.app.utils.launch
import io.stillpage.app.utils.observeEvent
import io.stillpage.app.utils.sendToClip
import io.stillpage.app.utils.setEdgeEffectColor
import io.stillpage.app.utils.share
import io.stillpage.app.utils.shouldHideSoftInput
import io.stillpage.app.utils.showDialogFragment
import io.stillpage.app.utils.showHelp
import io.stillpage.app.utils.splitNotBlank
import io.stillpage.app.utils.startActivity
import io.stillpage.app.utils.toastOnUi
import io.stillpage.app.utils.transaction
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 书源管理界面
 */
class BookSourceActivity : VMBaseActivity<ActivityBookSourceBinding, BookSourceViewModel>(),
    PopupMenu.OnMenuItemClickListener,
    BookSourceAdapter.CallBack,
    BookSourceGroupAdapter.CallBack,
    SelectActionBar.CallBack,
    SearchView.OnQueryTextListener {
    override val binding by viewBinding(ActivityBookSourceBinding::inflate)
    override val viewModel by viewModels<BookSourceViewModel>()
    private val importRecordKey = "bookSourceRecordKey"
    private val adapter by lazy { BookSourceAdapter(this, this, binding.recyclerView) }
    private val groupAdapter by lazy { BookSourceGroupAdapter(this, this) }
    private val itemTouchCallback by lazy { ItemTouchCallback(adapter) }
    private var isGroupMode = false
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var sourceFlowJob: Job? = null
    private var checkMessageRefreshJob: Job? = null
    private val groups = linkedSetOf<String>()
    private var groupMenu: SubMenu? = null
    
    // 内容类型分组常量
    companion object {
        private const val GROUP_NOVEL = "小说"
        private const val GROUP_AUDIO = "听书"
        private const val GROUP_MUSIC = "音乐" 
        private const val GROUP_DRAMA = "短剧"
        private const val GROUP_MANGA = "漫画"
        
        private val CONTENT_TYPE_GROUPS = mapOf(
            "TEXT" to GROUP_NOVEL,
            "AUDIO" to GROUP_AUDIO, // 有声书单独分组
            "MUSIC" to GROUP_MUSIC,
            "DRAMA" to GROUP_DRAMA,
            "IMAGE" to GROUP_MANGA,
            "FILE" to GROUP_NOVEL // 文件类型默认归类为小说
        )
    }
    override var sort = BookSourceSort.Default
        private set
    override var sortAscending = true
        private set
    private var snackBar: Snackbar? = null
    private var showDuplicationSource = false
    private val hostMap = hashMapOf<String, String>()
    private val qrResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportBookSourceDialog(it))
    }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportBookSourceDialog(uri.toString()))
        }
    }
    private val exportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    sendToClip(uri.toString())
                }
            }
        }
    }
    private val groupMenuLifecycleOwner = object : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun onMenuOpened() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun onMenuClosed() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        upBookSource()
        initLiveDataGroup()
        initSelectActionBar()
        resumeCheckSource()
        if (!LocalConfig.bookSourcesHelpVersionIsLast) {
            showHelp("SourceMBookHelp")
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

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_source, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        groupMenu = menu.findItem(R.id.menu_group).subMenu
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu!!
        sortSubMenu.findItem(R.id.menu_sort_desc).isChecked = !sortAscending
        sortSubMenu.setGroupCheckable(R.id.menu_group_sort, true, true)
        upGroupMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_book_source -> startActivity<BookSourceEditActivity>()
            R.id.menu_import_qr -> qrResult.launch()
            R.id.menu_group_manage -> showDialogFragment<BookSourceGroupManageDialog>()
            R.id.menu_auto_group -> {
                viewModel.autoGroupByContentType()
                toastOnUi("正在根据内容类型自动分组...")
            }
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_onLine -> showImportDialog()

            R.id.menu_sort_desc -> {
                sortAscending = !sortAscending
                item.isChecked = !sortAscending
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_manual -> {
                item.isChecked = true
                sort = BookSourceSort.Default
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_auto -> {
                item.isChecked = true
                sort = BookSourceSort.Weight
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_name -> {
                item.isChecked = true
                sort = BookSourceSort.Name
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_url -> {
                item.isChecked = true
                sort = BookSourceSort.Url
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_time -> {
                item.isChecked = true
                sort = BookSourceSort.Update
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_respondTime -> {
                item.isChecked = true
                sort = BookSourceSort.Respond
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_enable -> {
                item.isChecked = true
                sort = BookSourceSort.Enable
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_enabled_group -> {
                searchView.setQuery(getString(R.string.enabled), true)
            }

            R.id.menu_disabled_group -> {
                searchView.setQuery(getString(R.string.disabled), true)
            }

            R.id.menu_group_login -> {
                searchView.setQuery(getString(R.string.need_login), true)
            }

            R.id.menu_group_null -> {
                searchView.setQuery(getString(R.string.no_group), true)
            }

            R.id.menu_enabled_explore_group -> {
                searchView.setQuery(getString(R.string.enabled_explore), true)
            }

            R.id.menu_disabled_explore_group -> {
                searchView.setQuery(getString(R.string.disabled_explore), true)
            }

            // 内容类型筛选
            R.id.menu_content_type_text -> {
                searchView.setQuery("contentType:TEXT", true)
            }

            R.id.menu_content_type_audio -> {
                searchView.setQuery("contentType:AUDIO", true)
            }

            R.id.menu_content_type_image -> {
                searchView.setQuery("contentType:IMAGE", true)
            }

            R.id.menu_content_type_music -> {
                searchView.setQuery("contentType:MUSIC", true)
            }

            R.id.menu_content_type_drama -> {
                searchView.setQuery("contentType:DRAMA", true)
            }

            R.id.menu_content_type_file -> {
                searchView.setQuery("contentType:FILE", true)
            }

            R.id.menu_content_type_manual -> {
                searchView.setQuery("contentType:MANUAL", true)
            }

            R.id.menu_content_type_auto -> {
                searchView.setQuery("contentType:AUTO", true)
            }

            R.id.menu_toggle_view_mode -> {
                toggleViewMode()
            }

            R.id.menu_show_same_source -> {
                item.isChecked = !item.isChecked
                showDuplicationSource = item.isChecked
                adapter.showSourceHost = item.isChecked
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_help -> showHelp("SourceMBookHelp")
        }
        if (item.groupId == R.id.source_group) {
            val groupName = item.title.toString()
            // 过滤掉分隔线
            if (!groupName.startsWith("─")) {
                searchView.setQuery("group:$groupName", true)
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.recycledViewPool.setMaxRecycledViews(0, 15)
        // When this page is opened, it is in selection mode
        val dragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        dragSelectTouchHelper.activeSlideSelect()
        // Note: need judge selection first, so add ItemTouchHelper after it.
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.search_book_source)
        searchView.setOnQueryTextListener(this)
    }


    private fun upBookSource(searchKey: String? = null) {
        sourceFlowJob?.cancel()
        sourceFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrEmpty() -> {
                    appDb.bookSourceDao.flowAll()
                }

                searchKey == getString(R.string.enabled) -> {
                    appDb.bookSourceDao.flowEnabled()
                }

                searchKey == getString(R.string.disabled) -> {
                    appDb.bookSourceDao.flowDisabled()
                }

                searchKey == getString(R.string.need_login) -> {
                    appDb.bookSourceDao.flowLogin()
                }

                searchKey == getString(R.string.no_group) -> {
                    appDb.bookSourceDao.flowNoGroup()
                }

                searchKey == getString(R.string.enabled_explore) -> {
                    appDb.bookSourceDao.flowEnabledExplore()
                }

                searchKey == getString(R.string.disabled_explore) -> {
                    appDb.bookSourceDao.flowDisabledExplore()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.bookSourceDao.flowGroupSearch(key)
                }

                searchKey.startsWith("contentType:") -> {
                    val contentType = searchKey.substringAfter("contentType:")
                    appDb.bookSourceDao.flowContentTypeSearch(contentType)
                }

                else -> {
                    appDb.bookSourceDao.flowSearch(searchKey)
                }
            }.map { data ->
                hostMap.clear()
                if (showDuplicationSource) {
                    data.sortedWith(
                        compareBy<BookSourcePart> { getSourceHost(it.bookSourceUrl) == "#" }
                            .thenBy { getSourceHost(it.bookSourceUrl) }
                            .thenByDescending { it.lastUpdateTime })
                } else if (sortAscending) {
                    when (sort) {
                        BookSourceSort.Weight -> data.sortedBy { it.weight }
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o1.bookSourceName.cnCompare(o2.bookSourceName)
                        }

                        BookSourceSort.Url -> data.sortedBy { it.bookSourceUrl }
                        BookSourceSort.Update -> data.sortedByDescending { it.lastUpdateTime }
                        BookSourceSort.Respond -> data.sortedBy { it.respondTime }
                        BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                            var sort = -o1.enabled.compareTo(o2.enabled)
                            if (sort == 0) {
                                sort = o1.bookSourceName.cnCompare(o2.bookSourceName)
                            }
                            sort
                        }

                        else -> data
                    }
                } else {
                    when (sort) {
                        BookSourceSort.Weight -> data.sortedByDescending { it.weight }
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o2.bookSourceName.cnCompare(o1.bookSourceName)
                        }

                        BookSourceSort.Url -> data.sortedByDescending { it.bookSourceUrl }
                        BookSourceSort.Update -> data.sortedBy { it.lastUpdateTime }
                        BookSourceSort.Respond -> data.sortedByDescending { it.respondTime }
                        BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                            var sort = o1.enabled.compareTo(o2.enabled)
                            if (sort == 0) {
                                sort = o1.bookSourceName.cnCompare(o2.bookSourceName)
                            }
                            sort
                        }

                        else -> data.reversed()
                    }
                }
            }.flowWithLifecycleAndDatabaseChange(
                lifecycle,
                table = AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("书源界面更新书源出错", it)
            }.flowOn(IO).conflate().collect { data ->
                // 更新列表适配器
                adapter.setItems(data, adapter.diffItemCallback, !Debug.isChecking)
                
                // 如果当前是分组模式，也更新分组适配器
                if (isGroupMode) {
                    groupAdapter.updateSources(data)
                }
                
                itemTouchCallback.isCanDrag =
                    sort == BookSourceSort.Default && !showDuplicationSource
                delay(500)
            }
        }
    }

    private fun initLiveDataGroup() {
        lifecycleScope.launch {
            appDb.bookSourceDao.flowGroups()
                .flowWithLifecycleAndDatabaseChange(
                    lifecycle,
                    table = AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .flowWithLifecycleAndDatabaseChangeFirst(
                    groupMenuLifecycleOwner.lifecycle,
                    table = AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    
                    // 检查是否需要自动分组
                    checkAndAutoGroup()
                    
                    upGroupMenu()
                    delay(500)
                }
        }
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            adapter.selectAll()
        } else {
            adapter.revertSelection()
        }
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun onClickSelectBarMainAction() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.del(adapter.selection) }
            noButton()
        }
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        if (menu === groupMenu) {
            groupMenuLifecycleOwner.onMenuOpened()
        }
        return super.onMenuOpened(featureId, menu)
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        super.onPanelClosed(featureId, menu)
        if (menu === groupMenu) {
            groupMenuLifecycleOwner.onMenuClosed()
        }
    }

    private fun initSelectActionBar() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.book_source_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(adapter.selection)
            R.id.menu_disable_selection -> viewModel.disableSelection(adapter.selection)
            R.id.menu_enable_explore -> viewModel.enableSelectExplore(adapter.selection)
            R.id.menu_disable_explore -> viewModel.disableSelectExplore(adapter.selection)
            R.id.menu_check_source -> checkSource()
            R.id.menu_top_sel -> viewModel.topSource(*adapter.selection.toTypedArray())
            R.id.menu_bottom_sel -> viewModel.bottomSource(*adapter.selection.toTypedArray())
            R.id.menu_add_group -> selectionAddToGroups()
            R.id.menu_remove_group -> selectionRemoveFromGroups()
            R.id.menu_export_selection -> viewModel.saveToFile(
                adapter,
                searchView.query?.toString(),
                sortAscending,
                sort
            ) { file ->
                exportDir.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        "bookSource.json",
                        file,
                        "application/json"
                    )
                }
            }

            R.id.menu_share_source -> viewModel.saveToFile(
                adapter,
                searchView.query?.toString(),
                sortAscending,
                sort
            ) {
                share(it)
            }

            R.id.menu_check_selected_interval -> adapter.checkSelectedInterval()
            R.id.menu_move_to_novel -> viewModel.moveSourceToGroup(adapter.selection, GROUP_NOVEL)
            R.id.menu_move_to_audio -> viewModel.moveSourceToGroup(adapter.selection, GROUP_AUDIO)
            R.id.menu_move_to_music -> viewModel.moveSourceToGroup(adapter.selection, GROUP_MUSIC)
            R.id.menu_move_to_drama -> viewModel.moveSourceToGroup(adapter.selection, GROUP_DRAMA)
            R.id.menu_move_to_manga -> viewModel.moveSourceToGroup(adapter.selection, GROUP_MANGA)
        }
        return true
    }

    @SuppressLint("InflateParams")
    private fun checkSource() {
        val dialog = alert(titleResource = R.string.search_book_key) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "search word"
                editView.setText(CheckSource.keyword)
            }
            customView { alertBinding.root }
            okButton {
                keepScreenOn(true)
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        CheckSource.keyword = it
                    }
                }
                val selectItems = adapter.selection
                CheckSource.start(this@BookSourceActivity, selectItems)
                val adapterItems = adapter.getItems()
                val firstItem = adapterItems.indexOf(selectItems.firstOrNull())
                val lastItem = adapterItems.indexOf(selectItems.lastOrNull())
                Debug.isChecking = firstItem >= 0 && lastItem >= 0
                startCheckMessageRefreshJob(firstItem, lastItem)
            }
            neutralButton(R.string.check_source_config)
            cancelButton()
        }
        //手动设置监听 避免点击打开校验设置后对话框关闭
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            showDialogFragment<CheckSourceConfig>()
        }
    }

    private fun resumeCheckSource() {
        if (!Debug.isChecking) {
            return
        }
        keepScreenOn(true)
        CheckSource.resume(this)
        startCheckMessageRefreshJob(0, 0)
    }

    @SuppressLint("InflateParams")
    private fun selectionAddToGroups() {
        alert(titleResource = R.string.add_group) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        viewModel.selectionAddToGroups(adapter.selection, it)
                    }
                }
            }
            cancelButton()
        }
    }

    @SuppressLint("InflateParams")
    private fun selectionRemoveFromGroups() {
        alert(titleResource = R.string.remove_group) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        viewModel.selectionRemoveFromGroups(adapter.selection, it)
                    }
                }
            }
            cancelButton()
        }
    }

    private fun upGroupMenu() = groupMenu?.transaction { menu ->
        menu.removeGroup(R.id.source_group)
        
        // 分离内容类型分组和其他分组
        val contentTypeGroups = mutableListOf<String>()
        val otherGroups = mutableListOf<String>()
        
        groups.forEach { group ->
            if (CONTENT_TYPE_GROUPS.values.contains(group)) {
                contentTypeGroups.add(group)
            } else {
                otherGroups.add(group)
            }
        }
        
        // 按固定顺序添加内容类型分组
        listOf(GROUP_NOVEL, GROUP_AUDIO, GROUP_MUSIC, GROUP_DRAMA, GROUP_MANGA).forEach { contentTypeGroup ->
            if (contentTypeGroups.contains(contentTypeGroup)) {
                menu.add(R.id.source_group, Menu.NONE, Menu.NONE, contentTypeGroup)
            }
        }
        
        // 添加分隔线（如果两种分组都存在）
        if (contentTypeGroups.isNotEmpty() && otherGroups.isNotEmpty()) {
            menu.add(R.id.source_group, Menu.NONE, Menu.NONE, "────────")
        }
        
        // 添加其他分组（按字母顺序）
        otherGroups.sorted().forEach {
            menu.add(R.id.source_group, Menu.NONE, Menu.NONE, it)
        }
    }

    @SuppressLint("InflateParams")
    private fun showImportDialog() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(importRecordKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        alert(titleResource = R.string.import_on_line) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
                editView.setFilterValues(cacheUrls)
                editView.delCallBack = {
                    cacheUrls.remove(it)
                    aCache.put(importRecordKey, cacheUrls.joinToString(","))
                }
            }
            customView { alertBinding.root }
            okButton {
                val text = alertBinding.editView.text?.toString()
                text?.let {
                    if (it.isAbsUrl() && !cacheUrls.contains(it)) {
                        cacheUrls.add(0, it)
                        aCache.put(importRecordKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(ImportBookSourceDialog(it))
                }
            }
            cancelButton()
        }
    }

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.CHECK_SOURCE) { msg ->
            snackBar?.setText(msg) ?: let {
                snackBar = Snackbar
                    .make(binding.root, msg, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.cancel) {
                        CheckSource.stop(this)
                        Debug.finishChecking()
                    }.apply { show() }
            }
        }
        observeEvent<Int>(EventBus.CHECK_SOURCE_DONE) {
            keepScreenOn(false)
            snackBar?.dismiss()
            snackBar = null
            adapter.notifyItemRangeChanged(
                0,
                adapter.itemCount,
                bundleOf(Pair("checkSourceMessage", null))
            )
            groups.forEach { group ->
                if (group.contains("失效") && searchView.query.isEmpty()) {
                    searchView.setQuery("失效", true)
                    toastOnUi("发现有失效书源，已为您自动筛选！")
                }
            }
        }
    }

    private fun startCheckMessageRefreshJob(firstItem: Int, lastItem: Int) {
        checkMessageRefreshJob?.cancel()
        checkMessageRefreshJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (lastItem == 0) {
                        adapter.notifyItemRangeChanged(
                            0,
                            adapter.itemCount,
                            bundleOf(Pair("checkSourceMessage", null))
                        )
                    } else {
                        adapter.notifyItemRangeChanged(
                            firstItem,
                            lastItem + 1,
                            bundleOf(Pair("checkSourceMessage", null))
                        )
                    }
                    if (!Debug.isChecking) {
                        checkMessageRefreshJob?.cancel()
                    }
                    delay(300L)
                }
            }
        }
    }

    /**
     * 保持亮屏
     */
    private fun keepScreenOn(on: Boolean) {
        val isScreenOn =
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (on == isScreenOn) return
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun upCountView() {
        binding.selectActionBar
            .upCountView(adapter.selection.size, adapter.itemCount)
    }

    override fun getSourceHost(origin: String): String {
        return hostMap.getOrPut(origin) {
            NetworkUtils.getSubDomainOrNull(origin) ?: "#"
        }
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        newText?.let {
            upBookSource(it)
        }
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun del(bookSource: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + bookSource.bookSourceName)
            noButton()
            yesButton {
                viewModel.del(listOf(bookSource))
            }
        }
    }

    override fun edit(bookSource: BookSourcePart) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", bookSource.bookSourceUrl)
        }
    }

    override fun upOrder(items: List<BookSourcePart>) {
        viewModel.upOrder(items)
    }

    override fun enable(enable: Boolean, bookSource: BookSourcePart) {
        viewModel.enable(enable, listOf(bookSource))
    }

    override fun enableExplore(enable: Boolean, bookSource: BookSourcePart) {
        viewModel.enableExplore(enable, listOf(bookSource))
    }

    override fun toTop(bookSource: BookSourcePart) {
        if (sortAscending) {
            viewModel.topSource(bookSource)
        } else {
            viewModel.bottomSource(bookSource)
        }
    }

    override fun toBottom(bookSource: BookSourcePart) {
        if (sortAscending) {
            viewModel.bottomSource(bookSource)
        } else {
            viewModel.topSource(bookSource)
        }
    }

    override fun searchBook(bookSource: BookSourcePart) {
        startActivity<SearchActivity> {
            putExtra("searchScope", SearchScope(bookSource).toString())
        }
    }

    override fun debug(bookSource: BookSourcePart) {
        startActivity<BookSourceDebugActivity> {
            putExtra("key", bookSource.bookSourceUrl)
        }
    }
    
    override fun moveToGroup(bookSource: BookSourcePart, group: String) {
        viewModel.moveSourceToGroup(listOf(bookSource), group)
    }

    // BookSourceGroupAdapter.CallBack 的额外方法
    override fun getAllSources(): List<BookSourcePart> {
        // 始终返回原始适配器的数据，因为它包含所有书源
        return adapter.getItems()
    }

    /**
     * 切换视图模式（列表/分组）
     */
    private fun toggleViewMode() {
        isGroupMode = !isGroupMode
        
        if (isGroupMode) {
            // 切换到分组模式
            binding.recyclerView.adapter = groupAdapter
            // 获取当前数据并按分组显示
            val currentSources = adapter.getItems()
            groupAdapter.updateSources(currentSources)
            toastOnUi("已切换到分组视图")
        } else {
            // 切换到列表模式
            binding.recyclerView.adapter = adapter
            toastOnUi("已切换到列表视图")
        }
    }



    /**
     * 检查并执行自动分组
     */
    private fun checkAndAutoGroup() {
        lifecycleScope.launch {
            // 检查是否有未分组的书源需要自动分组
            val ungroupedSources = appDb.bookSourceDao.allNoGroup
            if (ungroupedSources.isNotEmpty()) {
                // 只对有明确内容类型特征的书源进行自动分组
                val needAutoGroup = ungroupedSources.any { source ->
                    hasContentTypeKeywords(source.bookSourceName, source.bookSourceUrl)
                }
                if (needAutoGroup) {
                    viewModel.autoGroupByContentType()
                }
            }
        }
    }
    
    /**
     * 检查书源名称或URL是否包含内容类型关键词
     */
    private fun hasContentTypeKeywords(name: String, url: String): Boolean {
        val lowerName = name.lowercase()
        val lowerUrl = url.lowercase()
        
        val keywords = listOf(
            // 音频类
            "有声", "听书", "audio", "podcast", "radio",
            // 漫画类
            "漫画", "连环画", "绘本", "comic", "manga",
            // 音乐类
            "音乐", "music", "song", "album", "mv",
            // 短剧类
            "短剧", "drama", "video", "movie", "film", "tv", "vod", "watch", "play", "series", "episode", "season"
        )
        
        return keywords.any { keyword ->
            lowerName.contains(keyword) || lowerUrl.contains(keyword)
        }
    }

    override fun finish() {
        if (searchView.query.isNullOrEmpty()) {
            super.finish()
        } else {
            searchView.setQuery("", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!Debug.isChecking) {
            Debug.debugMessageMap.clear()
        }
    }

}
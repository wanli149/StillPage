package io.stillpage.app.ui.config

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.view.postDelayed
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.jeremyliao.liveeventbus.LiveEventBus
import io.stillpage.app.R
import io.stillpage.app.constant.AppLog
import io.stillpage.app.constant.EventBus
import io.stillpage.app.constant.PreferKey
import io.stillpage.app.databinding.DialogEditTextBinding
import io.stillpage.app.help.AppFreezeMonitor
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.ExploreCacheManager
import io.stillpage.app.ui.main.explore.ExploreNewViewModel
import io.stillpage.app.help.config.LocalConfig
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.lib.prefs.fragment.PreferenceFragment
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.model.CheckSource
import io.stillpage.app.model.ImageProvider
import io.stillpage.app.receiver.SharedReceiverActivity
import io.stillpage.app.service.WebService
import io.stillpage.app.ui.file.HandleFileContract
import io.stillpage.app.ui.widget.number.NumberPickerDialog
import io.stillpage.app.utils.LogUtils
import io.stillpage.app.utils.getPrefBoolean
import io.stillpage.app.utils.postEvent
import io.stillpage.app.utils.putPrefBoolean
import io.stillpage.app.utils.putPrefString
import io.stillpage.app.utils.removePref
import io.stillpage.app.utils.restart
import io.stillpage.app.utils.setEdgeEffectColor
import io.stillpage.app.utils.showDialogFragment
import splitties.init.appCtx

/**
 * 其它设置
 */
class OtherConfigFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val viewModel by activityViewModels<ConfigViewModel>()
    private val packageManager = appCtx.packageManager
    private val componentName = ComponentName(
        appCtx,
        SharedReceiverActivity::class.java.name
    )
    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        putPrefBoolean(PreferKey.processText, isProcessTextEnabled())
        addPreferencesFromResource(R.xml.pref_config_other)
        upPreferenceSummary(PreferKey.userAgent, AppConfig.userAgent)
        upPreferenceSummary(PreferKey.preDownloadNum, AppConfig.preDownloadNum.toString())
        upPreferenceSummary(PreferKey.threadCount, AppConfig.threadCount.toString())
        upPreferenceSummary(PreferKey.webPort, AppConfig.webPort.toString())
        // 新增：进度保存节流窗口配置项的摘要
        upPreferenceSummary(PreferKey.saveThrottleMs, AppConfig.saveThrottleMs.toString())
        AppConfig.defaultBookTreeUri?.let {
            upPreferenceSummary(PreferKey.defaultBookTreeUri, it)
        }
        upPreferenceSummary(PreferKey.checkSource, CheckSource.summary)
        upPreferenceSummary(PreferKey.bitmapCacheSize, AppConfig.bitmapCacheSize.toString())
        upPreferenceSummary(PreferKey.imageRetainNum, AppConfig.imageRetainNum.toString())
        upPreferenceSummary(PreferKey.sourceEditMaxLine, AppConfig.sourceEditMaxLine.toString())
        // 新增：发现页TTL配置摘要
        upPreferenceSummary(PreferKey.exploreTtlAllMin, AppConfig.exploreTtlAllMin.toString())
        upPreferenceSummary(PreferKey.exploreTtlDramaMin, AppConfig.exploreTtlDramaMin.toString())
        upPreferenceSummary(PreferKey.exploreTtlMusicMin, AppConfig.exploreTtlMusicMin.toString())
        upPreferenceSummary(PreferKey.exploreTtlAudioMin, AppConfig.exploreTtlAudioMin.toString())
        upPreferenceSummary(PreferKey.exploreTtlImageMin, AppConfig.exploreTtlImageMin.toString())
        upPreferenceSummary(PreferKey.exploreTtlTextMin, AppConfig.exploreTtlTextMin.toString())
        upPreferenceSummary(PreferKey.exploreTtlFileMin, AppConfig.exploreTtlFileMin.toString())
        upPreferenceSummary(PreferKey.exploreSourceTtl, AppConfig.exploreSourceTtl ?: "未配置")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.other_setting)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        listView.setEdgeEffectColor(primaryColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            PreferKey.userAgent -> showUserAgentDialog()
            PreferKey.defaultBookTreeUri -> localBookTreeSelect.launch {
                title = getString(R.string.select_book_folder)
                mode = HandleFileContract.DIR_SYS
            }

            PreferKey.preDownloadNum -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.pre_download))
                .setMaxValue(9999)
                .setMinValue(0)
                .setValue(AppConfig.preDownloadNum)
                .show {
                    AppConfig.preDownloadNum = it
                }

            PreferKey.threadCount -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.threads_num_title))
                .setMaxValue(999)
                .setMinValue(1)
                .setValue(AppConfig.threadCount)
                .show {
                    AppConfig.threadCount = it
                }

            PreferKey.webPort -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.web_port_title))
                .setMaxValue(60000)
                .setMinValue(1024)
                .setValue(AppConfig.webPort)
                .show {
                    AppConfig.webPort = it
                }

            PreferKey.cleanCache -> clearCache()
            PreferKey.uploadRule -> showDialogFragment<DirectLinkUploadConfig>()
            PreferKey.checkSource -> showDialogFragment<CheckSourceConfig>()
            PreferKey.bitmapCacheSize -> {
                NumberPickerDialog(requireContext())
                    .setTitle(getString(R.string.bitmap_cache_size))
                    .setMaxValue(2047)
                    .setMinValue(1)
                    .setValue(AppConfig.bitmapCacheSize)
                    .show {
                        AppConfig.bitmapCacheSize = it
                        ImageProvider.bitmapLruCache.resize(ImageProvider.cacheSize)
                    }
            }
            PreferKey.imageRetainNum -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.image_retain_number))
                .setMaxValue(999)
                .setMinValue(0)
                .setValue(AppConfig.imageRetainNum)
                .show {
                    AppConfig.imageRetainNum = it
                }

            PreferKey.sourceEditMaxLine -> {
                NumberPickerDialog(requireContext())
                    .setTitle(getString(R.string.source_edit_text_max_line))
                    .setMaxValue(Int.MAX_VALUE)
                    .setMinValue(10)
                    .setValue(AppConfig.sourceEditMaxLine)
                    .show {
                        AppConfig.sourceEditMaxLine = it
                    }
            }

            PreferKey.saveThrottleMs -> {
                NumberPickerDialog(requireContext())
                    .setTitle(getString(R.string.audio_save_throttle_ms))
                    .setMaxValue(60000)
                    .setMinValue(200)
                    .setValue(AppConfig.saveThrottleMs)
                    .show {
                        AppConfig.saveThrottleMs = it
                    }
            }

            PreferKey.clearWebViewData -> clearWebViewData()
            "localPassword" -> alertLocalPassword()
            PreferKey.shrinkDatabase -> shrinkDatabase()
            // 发现页TTL配置入口
            PreferKey.exploreTtlAllMin -> NumberPickerDialog(requireContext())
                .setTitle("汇总视图TTL（分钟）")
                .setMaxValue(1440)
                .setMinValue(5)
                .setValue(AppConfig.exploreTtlAllMin)
                .show { AppConfig.exploreTtlAllMin = it }

            PreferKey.exploreTtlDramaMin -> NumberPickerDialog(requireContext())
                .setTitle("短剧TTL（分钟）")
                .setMaxValue(1440)
                .setMinValue(5)
                .setValue(AppConfig.exploreTtlDramaMin)
                .show { AppConfig.exploreTtlDramaMin = it }

            PreferKey.exploreTtlMusicMin -> NumberPickerDialog(requireContext())
                .setTitle("音乐TTL（分钟）")
                .setMaxValue(1440)
                .setMinValue(10)
                .setValue(AppConfig.exploreTtlMusicMin)
                .show { AppConfig.exploreTtlMusicMin = it }

            PreferKey.exploreTtlAudioMin -> NumberPickerDialog(requireContext())
                .setTitle("听书TTL（分钟）")
                .setMaxValue(1440)
                .setMinValue(10)
                .setValue(AppConfig.exploreTtlAudioMin)
                .show { AppConfig.exploreTtlAudioMin = it }

            PreferKey.exploreTtlImageMin -> NumberPickerDialog(requireContext())
                .setTitle("漫画/图片TTL（分钟）")
                .setMaxValue(1440)
                .setMinValue(10)
                .setValue(AppConfig.exploreTtlImageMin)
                .show { AppConfig.exploreTtlImageMin = it }

            PreferKey.exploreTtlTextMin -> NumberPickerDialog(requireContext())
                .setTitle("文本TTL（分钟）")
                .setMaxValue(1440)
                .setMinValue(10)
                .setValue(AppConfig.exploreTtlTextMin)
                .show { AppConfig.exploreTtlTextMin = it }

            PreferKey.exploreTtlFileMin -> NumberPickerDialog(requireContext())
                .setTitle("文件TTL（分钟）")
                .setMaxValue(1440)
                .setMinValue(10)
                .setValue(AppConfig.exploreTtlFileMin)
                .show { AppConfig.exploreTtlFileMin = it }

            PreferKey.exploreSourceTtl -> showExploreSourceTtlDialog()
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.preDownloadNum -> {
                upPreferenceSummary(key, AppConfig.preDownloadNum.toString())
            }

            PreferKey.threadCount -> {
                upPreferenceSummary(key, AppConfig.threadCount.toString())
                postEvent(PreferKey.threadCount, "")
            }

            PreferKey.webPort -> {
                upPreferenceSummary(key, AppConfig.webPort.toString())
                if (WebService.isRun) {
                    WebService.stop(requireContext())
                    WebService.start(requireContext())
                }
            }

            PreferKey.saveThrottleMs -> {
                upPreferenceSummary(key, AppConfig.saveThrottleMs.toString())
            }

            PreferKey.defaultBookTreeUri -> {
                upPreferenceSummary(key, AppConfig.defaultBookTreeUri)
            }

            PreferKey.recordLog -> {
                AppConfig.recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)
                LogUtils.upLevel()
                LogUtils.logDeviceInfo()
                LiveEventBus.config().enableLogger(AppConfig.recordLog)
                AppFreezeMonitor.init(appCtx)
            }

            PreferKey.processText -> sharedPreferences?.let {
                setProcessTextEnable(it.getBoolean(key, true))
            }

            PreferKey.showDiscovery -> {
                handleDiscoveryToggle(key, sharedPreferences)
            }
            PreferKey.showRss -> postEvent(EventBus.NOTIFY_MAIN, true)
            PreferKey.useNewExplore -> {
                handleNewExploreToggle(key, sharedPreferences)
            }
            PreferKey.language -> listView.postDelayed(1000) {
                appCtx.restart()
            }

            PreferKey.userAgent -> listView.post {
                upPreferenceSummary(PreferKey.userAgent, AppConfig.userAgent)
            }

            PreferKey.checkSource -> listView.post {
                upPreferenceSummary(PreferKey.checkSource, CheckSource.summary)
            }

            PreferKey.bitmapCacheSize -> {
                upPreferenceSummary(key, AppConfig.bitmapCacheSize.toString())
            }

            PreferKey.imageRetainNum -> {
                upPreferenceSummary(key, AppConfig.imageRetainNum.toString())
            }

            PreferKey.sourceEditMaxLine -> {
                upPreferenceSummary(key, AppConfig.sourceEditMaxLine.toString())
            }

            // 成人内容开关变更：通知发现页刷新
            PreferKey.enableAdultContent -> {
                postEvent(PreferKey.enableAdultContent, true)
            }
            // 发现页TTL配置变化：更新摘要并实时应用到缓存
            PreferKey.exploreTtlAllMin -> {
                upPreferenceSummary(key, AppConfig.exploreTtlAllMin.toString())
                ExploreCacheManager.setTypeTtl(ExploreNewViewModel.ContentType.ALL, AppConfig.exploreTtlAllMin * 60_000L)
            }
            PreferKey.exploreTtlDramaMin -> {
                upPreferenceSummary(key, AppConfig.exploreTtlDramaMin.toString())
                ExploreCacheManager.setTypeTtl(ExploreNewViewModel.ContentType.DRAMA, AppConfig.exploreTtlDramaMin * 60_000L)
            }
            PreferKey.exploreTtlMusicMin -> {
                upPreferenceSummary(key, AppConfig.exploreTtlMusicMin.toString())
                ExploreCacheManager.setTypeTtl(ExploreNewViewModel.ContentType.MUSIC, AppConfig.exploreTtlMusicMin * 60_000L)
            }
            PreferKey.exploreTtlAudioMin -> {
                upPreferenceSummary(key, AppConfig.exploreTtlAudioMin.toString())
                ExploreCacheManager.setTypeTtl(ExploreNewViewModel.ContentType.AUDIO, AppConfig.exploreTtlAudioMin * 60_000L)
            }
            PreferKey.exploreTtlImageMin -> {
                upPreferenceSummary(key, AppConfig.exploreTtlImageMin.toString())
                ExploreCacheManager.setTypeTtl(ExploreNewViewModel.ContentType.IMAGE, AppConfig.exploreTtlImageMin * 60_000L)
            }
            PreferKey.exploreTtlTextMin -> {
                upPreferenceSummary(key, AppConfig.exploreTtlTextMin.toString())
                ExploreCacheManager.setTypeTtl(ExploreNewViewModel.ContentType.TEXT, AppConfig.exploreTtlTextMin * 60_000L)
            }
            PreferKey.exploreTtlFileMin -> {
                upPreferenceSummary(key, AppConfig.exploreTtlFileMin.toString())
                ExploreCacheManager.setTypeTtl(ExploreNewViewModel.ContentType.FILE, AppConfig.exploreTtlFileMin * 60_000L)
            }
            PreferKey.exploreSourceTtl -> {
                val conf = AppConfig.exploreSourceTtl ?: ""
                upPreferenceSummary(key, if (conf.isEmpty()) "未配置" else conf)
                parseSourceTtlMap(conf).forEach { (url, minutes) ->
                    ExploreCacheManager.setSourceTtl(url, minutes * 60_000L)
                }
            }
        }
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.preDownloadNum -> preference.summary =
                getString(R.string.pre_download_s, value)

            PreferKey.threadCount -> preference.summary = getString(R.string.threads_num, value)
            PreferKey.webPort -> preference.summary = getString(R.string.web_port_summary, value)
            PreferKey.bitmapCacheSize -> preference.summary =
                getString(R.string.bitmap_cache_size_summary, value)
            PreferKey.imageRetainNum -> preference.summary =
                getString(R.string.image_retain_number_summary, value)

            PreferKey.sourceEditMaxLine -> preference.summary =
                getString(R.string.source_edit_max_line_summary, value)

            PreferKey.saveThrottleMs -> preference.summary =
                getString(R.string.audio_save_throttle_ms_summary, value)

            PreferKey.enableHaptics -> preference.summary =
                getString(R.string.enable_haptics_summary)

            else -> if (preference is ListPreference) {
                val index = preference.findIndexOfValue(value)
                // Set the summary to reflect the new value.
                preference.summary = if (index >= 0) preference.entries[index] else null
            } else {
                preference.summary = value
            }
        }
    }

    private fun showExploreSourceTtlDialog() {
        val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = "url=分钟，多项用逗号或换行分隔"
            editView.setText(AppConfig.exploreSourceTtl ?: "")
        }
        alert("书源TTL映射") {
            customView { alertBinding.root }
            okButton {
                val text = alertBinding.editView.text?.toString()?.trim()
                AppConfig.exploreSourceTtl = text
            }
            cancelButton()
        }
    }

    private fun parseSourceTtlMap(text: String): Map<String, Int> {
        if (text.isBlank()) return emptyMap()
        val map = mutableMapOf<String, Int>()
        text.split('\n', ',').forEach { raw ->
            val kv = raw.trim()
            if (kv.isEmpty()) return@forEach
            val idx = kv.indexOf('=')
            if (idx in 1 until kv.length) {
                val url = kv.substring(0, idx).trim()
                val minStr = kv.substring(idx + 1).trim()
                minStr.toIntOrNull()?.let { map[url] = it }
            }
        }
        return map
    }

    @SuppressLint("InflateParams")
    private fun showUserAgentDialog() {
        alert(getString(R.string.user_agent)) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = getString(R.string.user_agent)
                editView.setText(AppConfig.userAgent)
            }
            customView { alertBinding.root }
            okButton {
                val userAgent = alertBinding.editView.text?.toString()
                if (userAgent.isNullOrBlank()) {
                    removePref(PreferKey.userAgent)
                } else {
                    putPrefString(PreferKey.userAgent, userAgent)
                }
            }
            cancelButton()
        }
    }

    private fun clearCache() {
        requireContext().alert(
            titleResource = R.string.clear_cache,
            messageResource = R.string.sure_del
        ) {
            okButton {
                viewModel.clearCache()
            }
            noButton()
        }
    }

    private fun shrinkDatabase() {
        alert(R.string.sure, R.string.shrink_database) {
            okButton {
                viewModel.shrinkDatabase()
            }
            noButton()
        }
    }

    private fun clearWebViewData() {
        alert(R.string.clear_webview_data, R.string.sure_del) {
            okButton {
                viewModel.clearWebViewData()
            }
            noButton()
        }
    }

    private fun isProcessTextEnabled(): Boolean {
        return packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun setProcessTextEnable(enable: Boolean) {
        if (enable) {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
        } else {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun alertLocalPassword() {
        context?.alert(R.string.set_local_password, R.string.set_local_password_summary) {
            val editTextBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "password"
            }
            customView {
                editTextBinding.root
            }
            okButton {
                LocalConfig.password = editTextBinding.editView.text.toString()
            }
            cancelButton()
        }
    }

    private var isHandlingToggle = false

    private fun handleDiscoveryToggle(key: String, sharedPreferences: SharedPreferences?) {
        if (isHandlingToggle) return

        val isShowDiscovery = sharedPreferences?.getBoolean(PreferKey.showDiscovery, true) ?: true
        AppLog.put("设置页面：传统发现页面开关改变为：$isShowDiscovery")

        isHandlingToggle = true
        try {
            if (isShowDiscovery) {
                // 开启传统发现页面时，关闭新发现页面
                val useNewExplorePreference = findPreference<io.stillpage.app.lib.prefs.SwitchPreference>(PreferKey.useNewExplore)
                if (useNewExplorePreference?.isChecked == true) {
                    useNewExplorePreference.isChecked = false
                    AppLog.put("设置页面：开启传统发现页面，自动关闭新发现页面")
                }
            } else {
                // 关闭传统发现页面时，如果新发现页面也关闭了，则开启新发现页面
                val useNewExplorePreference = findPreference<io.stillpage.app.lib.prefs.SwitchPreference>(PreferKey.useNewExplore)
                if (useNewExplorePreference?.isChecked == false) {
                    useNewExplorePreference.isChecked = true
                    AppLog.put("设置页面：传统发现页面关闭，自动开启新发现页面")
                }
            }
        } finally {
            isHandlingToggle = false
        }

        // 更新底部菜单，不重启APP
        postEvent(EventBus.NOTIFY_MAIN, true)
    }

    private fun handleNewExploreToggle(key: String, sharedPreferences: SharedPreferences?) {
        if (isHandlingToggle) return

        val useNewExplore = sharedPreferences?.getBoolean(PreferKey.useNewExplore, false) ?: false
        AppLog.put("设置页面：新发现页面开关改变为：$useNewExplore")

        isHandlingToggle = true
        try {
            if (useNewExplore) {
                // 开启新发现页面时，关闭传统发现页面
                val showDiscoveryPreference = findPreference<io.stillpage.app.lib.prefs.SwitchPreference>(PreferKey.showDiscovery)
                if (showDiscoveryPreference?.isChecked == true) {
                    showDiscoveryPreference.isChecked = false
                    AppLog.put("设置页面：开启新发现页面，自动关闭传统发现页面")
                }
            } else {
                // 关闭新发现页面时，如果传统发现页面也关闭了，则开启传统发现页面
                val showDiscoveryPreference = findPreference<io.stillpage.app.lib.prefs.SwitchPreference>(PreferKey.showDiscovery)
                if (showDiscoveryPreference?.isChecked == false) {
                    showDiscoveryPreference.isChecked = true
                    AppLog.put("设置页面：新发现页面关闭，自动开启传统发现页面")
                }
            }
        } finally {
            isHandlingToggle = false
        }

        // 更新底部菜单，不重启APP
        postEvent(EventBus.NOTIFY_MAIN, true)
    }

}
package io.stillpage.app.ui.association

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import io.stillpage.app.R
import io.stillpage.app.base.BaseDialogFragment
import io.stillpage.app.constant.AppLog
import io.stillpage.app.databinding.DialogOpenUrlConfirmBinding
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.utils.applyTint
import io.stillpage.app.utils.setLayout
import io.stillpage.app.utils.toastOnUi
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx

class OpenUrlConfirmDialog() : BaseDialogFragment(R.layout.dialog_open_url_confirm),
    Toolbar.OnMenuItemClickListener {

    constructor(
        uri: String,
        mimeType: String?,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int
    ) : this() {
        arguments = Bundle().apply {
            putString("uri", uri)
            putString("mimeType", mimeType)
            putString("sourceOrigin", sourceOrigin)
            putString("sourceName", sourceName)
            putInt("sourceType", sourceType)
        }
    }

    val binding by viewBinding(DialogOpenUrlConfirmBinding::bind)
    val viewModel by viewModels<OpenUrlConfirmViewModel>()

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initMenu()
        val arguments = arguments ?: return
        viewModel.initData(arguments)
        if (viewModel.uri.isBlank()) {
            dismiss()
            return
        }
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.subtitle = viewModel.sourceName
        initView()
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.open_url_confirm)
        binding.toolBar.menu.applyTint(requireContext())
    }

    private fun initView() {
        binding.message.text = "${viewModel.sourceName} 正在请求跳转链接/应用，是否跳转？"
        binding.btnNegative.setOnClickListener { dismiss() }
        binding.btnPositive.setOnClickListener {
            openUrl()
            dismiss()
        }
    }

    private fun openUrl() {
        try {
            val uri = viewModel.uri.toUri()
            val mimeType = viewModel.mimeType
            val urlStr = uri.toString()

            // 回归旧逻辑：不再强制走独立播放器，交由系统/目标应用处理
            // 创建目标 Intent 并设置类型
            val targetIntent = Intent(Intent.ACTION_VIEW).apply {
                // 同时设置 Data 和 Type
                if (!mimeType.isNullOrBlank()) {
                    setDataAndType(uri, mimeType)
                } else {
                    data = uri
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 验证是否有应用可以处理
            if (targetIntent.resolveActivity(appCtx.packageManager) != null) {
                startActivity(targetIntent)
            } else {
                toastOnUi(R.string.can_not_open)
            }
        } catch (e: Exception) {
            AppLog.put("打开链接失败", e, true)
        }
    }

    /**
     * 判断是否为可直接交给播放器处理的视频直链
     */
    private fun isDirectVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") ||
            lower.startsWith("tel:") || lower.startsWith("sms:")) return false
        val excludeExt = listOf(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico",
            ".html", ".htm", ".php", ".asp", ".jsp"
        )
        if (excludeExt.any { lower.contains(it) }) return false
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

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_disable_source -> {
                viewModel.disableSource {
                    dismiss()
                }
            }

            R.id.menu_delete_source -> {
                alert(R.string.draw) {
                    setMessage(getString(R.string.sure_del) + "\n" + viewModel.sourceName)
                    noButton()
                    yesButton {
                        viewModel.deleteSource {
                            dismiss()
                        }
                    }
                }
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.finish()
    }

}

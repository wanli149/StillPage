package io.stillpage.app.ui.association

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.stillpage.app.R
import io.stillpage.app.base.BaseDialogFragment
import io.stillpage.app.databinding.DialogVerificationCodeViewBinding
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.glide.ImageLoader
import io.stillpage.app.help.glide.OkHttpModelLoader
import io.stillpage.app.help.source.SourceVerificationHelp
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.model.ImageProvider
import io.stillpage.app.ui.widget.dialog.PhotoDialog
import io.stillpage.app.utils.applyTint
import io.stillpage.app.utils.setLayout
import io.stillpage.app.utils.showDialogFragment
import io.stillpage.app.utils.viewbindingdelegate.viewBinding

/**
 * 图片验证码对话框
 * 结果保存在内存中
 * val key = "${sourceOrigin ?: ""}_verificationResult"
 * CacheManager.get(key)
 */
class VerificationCodeDialog() : BaseDialogFragment(R.layout.dialog_verification_code_view),
    Toolbar.OnMenuItemClickListener {

    constructor(
        imageUrl: String,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int
    ) : this() {
        arguments = Bundle().apply {
            putString("imageUrl", imageUrl)
            putString("sourceOrigin", sourceOrigin)
            putString("sourceName", sourceName)
            putInt("sourceType", sourceType)
        }
    }

    val binding by viewBinding(DialogVerificationCodeViewBinding::bind)
    val viewModel by viewModels<VerificationCodeViewModel>()

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private var sourceOrigin: String? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?): Unit = binding.run {
        initMenu()
        val arguments = arguments ?: return@run
        viewModel.initData(arguments)
        toolBar.setBackgroundColor(primaryColor)
        toolBar.subtitle = arguments.getString("sourceName")
        sourceOrigin = arguments.getString("sourceOrigin")
        val imageUrl = arguments.getString("imageUrl") ?: return@run
        loadImage(imageUrl, sourceOrigin)
        verificationCodeImageView.setOnClickListener {
            showDialogFragment(PhotoDialog(imageUrl, sourceOrigin))
        }

        // 尝试自动识别验证码
        tryAutoRecognition(imageUrl)
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.verification_code)
        binding.toolBar.menu.applyTint(requireContext())
    }

    @SuppressLint("CheckResult")
    private fun loadImage(url: String, sourceUrl: String?) {
        ImageProvider.remove(url)
        ImageLoader.loadBitmap(requireContext(), url).apply {
            sourceUrl?.let {
                apply(RequestOptions().set(OkHttpModelLoader.sourceOriginOption, it))
            }
        }.error(R.drawable.image_loading_error)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap?>,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: Bitmap,
                    model: Any,
                    target: Target<Bitmap?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    val bitmap = resource.copy(resource.config!!, true)
                    ImageProvider.put(url, bitmap) // 传给 PhotoDialog
                    return false
                }
            })
            .into(binding.verificationCodeImageView)
    }

    @SuppressLint("InflateParams")
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_ok -> {
                val verificationCode = binding.verificationCode.text.toString()
                SourceVerificationHelp.setResult(sourceOrigin!!, verificationCode)
                dismiss()
            }

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

    private fun tryAutoRecognition(imageUrl: String) {
        if (!AppConfig.enableAutoVerificationCode) {
            return
        }

        // 显示自动识别提示
        binding.verificationCode.hint = getString(R.string.auto_verification_processing)

        viewModel.tryAutoRecognition(imageUrl, sourceOrigin) { result ->
            if (result != null) {
                // 自动识别成功
                binding.verificationCode.setText(result)
                binding.verificationCode.hint = getString(R.string.auto_verification_success, result)

                // 可以选择自动提交或让用户确认
                if (AppConfig.autoSubmitVerificationCode) {
                    // 自动提交
                    SourceVerificationHelp.setResult(sourceOrigin!!, result)
                    dismiss()
                }
            } else {
                // 自动识别失败，恢复正常提示
                binding.verificationCode.hint = getString(R.string.input_verification_code)
            }
        }
    }

    override fun onDestroy() {
        SourceVerificationHelp.checkResult(sourceOrigin!!)
        super.onDestroy()
        activity?.finish()
    }

}

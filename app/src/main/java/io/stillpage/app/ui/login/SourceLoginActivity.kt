package io.stillpage.app.ui.login

import android.os.Bundle
import androidx.activity.viewModels
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.data.entities.BaseSource
import io.stillpage.app.databinding.ActivitySourceLoginBinding
import io.stillpage.app.utils.showDialogFragment
import io.stillpage.app.utils.viewbindingdelegate.viewBinding


class SourceLoginActivity : VMBaseActivity<ActivitySourceLoginBinding, SourceLoginViewModel>() {

    override val binding by viewBinding(ActivitySourceLoginBinding::inflate)
    override val viewModel by viewModels<SourceLoginViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.initData(intent, success = { source ->
            initView(source)
        }, error = {
            finish()
        })
    }

    private fun initView(source: BaseSource) {
        if (source.loginUi.isNullOrEmpty()) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fl_fragment, WebViewLoginFragment(), "webViewLogin")
                .commit()
        } else {
            showDialogFragment<SourceLoginDialog>()
        }
    }

}
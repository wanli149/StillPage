package io.stillpage.app.ui.widget.dialog

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.stillpage.app.R
import io.stillpage.app.base.BaseDialogFragment
import io.stillpage.app.databinding.DialogCodeViewBinding
import io.stillpage.app.help.IntentData
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.ui.widget.code.addJsPattern
import io.stillpage.app.ui.widget.code.addJsonPattern
import io.stillpage.app.ui.widget.code.addStillPagePattern
import io.stillpage.app.utils.applyTint
import io.stillpage.app.utils.disableEdit
import io.stillpage.app.utils.setLayout
import io.stillpage.app.utils.viewbindingdelegate.viewBinding

class CodeDialog() : BaseDialogFragment(R.layout.dialog_code_view) {

    constructor(code: String, disableEdit: Boolean = true, requestId: String? = null) : this() {
        arguments = Bundle().apply {
            putBoolean("disableEdit", disableEdit)
            putString("code", IntentData.put(code))
            putString("requestId", requestId)
        }
    }

    val binding by viewBinding(DialogCodeViewBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        if (arguments?.getBoolean("disableEdit") == true) {
            binding.toolBar.title = "code view"
            binding.codeView.disableEdit()
        } else {
            initMenu()
        }
        binding.codeView.addStillPagePattern()
        binding.codeView.addJsonPattern()
        binding.codeView.addJsPattern()
        arguments?.getString("code")?.let {
            binding.codeView.text = IntentData.get(it)
        }
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.code_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_save -> {
                    binding.codeView.text?.toString()?.let { code ->
                        val requestId = arguments?.getString("requestId")
                        (parentFragment as? Callback)?.onCodeSave(code, requestId)
                            ?: (activity as? Callback)?.onCodeSave(code, requestId)
                    }
                    dismiss()
                }
            }
            return@setOnMenuItemClickListener true
        }
    }


    interface Callback {

        fun onCodeSave(code: String, requestId: String?)

    }

}
package io.stillpage.app.ui.book.read

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.stillpage.app.R
import io.stillpage.app.base.BaseDialogFragment
import io.stillpage.app.base.adapter.ItemViewHolder
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.data.entities.ReplaceRule
import io.stillpage.app.databinding.DialogRecyclerViewBinding
import io.stillpage.app.databinding.Item1lineTextBinding
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.lib.theme.primaryColor
import io.stillpage.app.model.ReadBook
import io.stillpage.app.ui.replace.edit.ReplaceEditActivity
import io.stillpage.app.utils.setLayout
import io.stillpage.app.utils.viewbindingdelegate.viewBinding

/**
 * 起效的替换规则
 */
class EffectiveReplacesDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel by activityViewModels<ReadBookViewModel>()
    private val adapter by lazy { ReplaceAdapter(requireContext()) }
    private val chineseConvert by lazy { ReplaceRule(0, "繁简转换") }

    private var isEdit = false

    private val editActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == AppCompatActivity.RESULT_OK) {
                isEdit = true
            }
        }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            toolBar.setBackgroundColor(primaryColor)
            toolBar.setTitle(R.string.effective_replaces)
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter
        }
        val effectiveReplaceRules = ReadBook.curTextChapter?.effectiveReplaceRules ?: emptyList()
        if (AppConfig.chineseConverterType > 0) {
            adapter.setItems(effectiveReplaceRules + chineseConvert)
        } else {
            adapter.setItems(effectiveReplaceRules)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (isEdit) {
            viewModel.replaceRuleChanged()
        }
    }
    
    private fun showChineseConvertAlert() {
        alert(titleResource = R.string.chinese_converter) {
            items(resources.getStringArray(R.array.chinese_mode).toList()) { _, i ->
                if (AppConfig.chineseConverterType != i) {
                    AppConfig.chineseConverterType = i
                    isEdit = true
                }
            }
        }
    }

    private inner class ReplaceAdapter(context: Context) :
        RecyclerAdapter<ReplaceRule, Item1lineTextBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): Item1lineTextBinding {
            return Item1lineTextBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: Item1lineTextBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let { item ->
                    if (item == chineseConvert) {
                        showChineseConvertAlert()
                        return@let
                    }
                    editActivity.launch(ReplaceEditActivity.startIntent(requireContext(), item.id))
                }
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: Item1lineTextBinding,
            item: ReplaceRule,
            payloads: MutableList<Any>
        ) {
            binding.textView.text = item.name
        }

    }

}
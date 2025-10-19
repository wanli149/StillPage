package io.stillpage.app.ui.book.audio

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.databinding.DialogAudioTocBinding
import io.stillpage.app.model.AudioPlay
import io.stillpage.app.ui.book.toc.ChapterListAdapter
import io.stillpage.app.help.book.isLocal
import io.stillpage.app.lib.theme.ThemeStore
import io.stillpage.app.lib.theme.bottomBackground
import io.stillpage.app.utils.applyTint
import io.stillpage.app.utils.viewbindingdelegate.viewBinding

class AudioTocDialog : BottomSheetDialogFragment(), ChapterListAdapter.Callback {

    private val binding by viewBinding(DialogAudioTocBinding::bind)
    private val viewModel by viewModels<AudioTocViewModel>()
    private lateinit var adapter: ChapterListAdapter
    private var callback: Callback? = null

    interface Callback {
        fun onChapterSelected(chapterIndex: Int, chapterPos: Int)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(io.stillpage.app.R.layout.dialog_audio_toc, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        initData()
        initClickEvents()
    }

    private fun initRecyclerView() {
        adapter = ChapterListAdapter(requireContext(), this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.ivChapterTop.applyTint(ThemeStore.accentColor(requireContext()))
        binding.ivChapterBottom.applyTint(ThemeStore.accentColor(requireContext()))
        binding.ivChapterTop.setOnClickListener {
            binding.recyclerView.scrollToPosition(0)
        }
        binding.ivChapterBottom.setOnClickListener {
            if (adapter.itemCount > 0) {
                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private fun initData() {
        AudioPlay.book?.let { book ->
            viewModel.loadChapterList(book) { chapters ->
                adapter.setItems(chapters)
                // 滚动到当前章节
                if (AudioPlay.durChapterIndex >= 0 && AudioPlay.durChapterIndex < chapters.size) {
                    binding.recyclerView.scrollToPosition(AudioPlay.durChapterIndex)
                }
            }
        }
    }

    // 实现ChapterListAdapter.Callback接口
    override val scope: kotlinx.coroutines.CoroutineScope
        get() = lifecycleScope

    override val book: io.stillpage.app.data.entities.Book?
        get() = AudioPlay.book

    override val isLocalBook: Boolean
        get() = AudioPlay.book?.isLocal ?: false

    override fun openChapter(bookChapter: BookChapter) {
        callback?.onChapterSelected(bookChapter.index, 0)
        dismiss()
    }

    override fun durChapterIndex(): Int {
        return AudioPlay.durChapterIndex
    }

    override fun onListChanged() {
        // 列表变化处理
    }

    private fun initClickEvents() {
        binding.ivClose.setOnClickListener {
            dismiss()
        }
    }

    fun setCallback(callback: Callback): AudioTocDialog {
        this.callback = callback
        return this
    }

    companion object {
        fun show(context: Context, callback: Callback) {
            if (context is androidx.fragment.app.FragmentActivity) {
                val dialog = AudioTocDialog().setCallback(callback)
                dialog.show(context.supportFragmentManager, "AudioTocDialog")
            }
        }
    }
}

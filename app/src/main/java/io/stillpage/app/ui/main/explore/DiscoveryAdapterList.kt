package io.stillpage.app.ui.main.explore

import android.content.Context
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import io.stillpage.app.R
import io.stillpage.app.base.adapter.ItemViewHolder
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.databinding.ItemDiscoveryListBinding
import io.stillpage.app.help.ContentTypeDetector
import io.stillpage.app.utils.gone

class DiscoveryAdapterList(context: Context, private val callback: CallBack) :
    RecyclerAdapter<ExploreNewViewModel.DiscoveryItem, ItemDiscoveryListBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemDiscoveryListBinding {
        return ItemDiscoveryListBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemDiscoveryListBinding,
        item: ExploreNewViewModel.DiscoveryItem,
        payloads: MutableList<Any>
    ) {
        val book = item.book
        binding.apply {
            // 封面加载 - 使用CoverImageView
            ivCover.load(book.coverUrl, book.name, book.author)

            // 书名
            tvTitle.text = book.name

            // 作者
            tvAuthor.text = book.author

            // 简介
            tvIntro.text = book.intro?.takeIf { it.isNotBlank() } ?: "暂无简介"

            // 最新章节
            tvLatestChapter.text = book.latestChapterTitle?.takeIf { it.isNotBlank() } ?: "未知章节"

            // 内容类型标签（直接使用已计算的类型）
            setupContentTypeLabel(item.contentType, binding)

            // 书架状态
            bvInBookshelf.isVisible = callback.isInBookShelf(book.name, book.author)

            // 加载状态 - 已移除非居中的转动圆圈指示器
            // rlLoading.gone()
        }
    }

    private fun setupContentTypeLabel(contentType: ExploreNewViewModel.ContentType, binding: ItemDiscoveryListBinding) {
        binding.tvContentType.apply {
            text = contentType.displayName

            val backgroundRes = when (contentType) {
                ExploreNewViewModel.ContentType.TEXT -> R.color.content_type_text
                ExploreNewViewModel.ContentType.AUDIO -> R.color.content_type_audio
                ExploreNewViewModel.ContentType.IMAGE -> R.color.content_type_image
                ExploreNewViewModel.ContentType.MUSIC -> R.color.content_type_music
                ExploreNewViewModel.ContentType.DRAMA -> R.color.content_type_drama
                ExploreNewViewModel.ContentType.FILE -> R.color.content_type_file
                else -> R.color.content_type_text
            }

            try {
                setBackgroundColor(ContextCompat.getColor(context, backgroundRes))
            } catch (e: Exception) {
                setBackgroundColor(getDefaultColorForType(contentType))
            }
        }
    }

    private fun getDefaultColorForType(contentType: ExploreNewViewModel.ContentType): Int {
        return when (contentType) {
            ExploreNewViewModel.ContentType.TEXT -> ContextCompat.getColor(context, R.color.success)
            ExploreNewViewModel.ContentType.AUDIO -> ContextCompat.getColor(context, R.color.accent)
            ExploreNewViewModel.ContentType.IMAGE -> ContextCompat.getColor(context, R.color.primary)
            ExploreNewViewModel.ContentType.MUSIC -> ContextCompat.getColor(context, R.color.lightBlue_color)
            ExploreNewViewModel.ContentType.DRAMA -> ContextCompat.getColor(context, R.color.content_type_drama)
            ExploreNewViewModel.ContentType.FILE -> ContextCompat.getColor(context, R.color.disabled)
            else -> ContextCompat.getColor(context, R.color.success)
        }
    }

    // 已不再需要在适配器中做类型检测，统一由 ViewModel 提供

    override fun registerListener(holder: ItemViewHolder, binding: ItemDiscoveryListBinding) {
        binding.apply {
            // 点击事件
            root.setOnClickListener {
                getItem(holder.layoutPosition)?.let { item ->
                    callback.showBookInfo(item.book)
                }
            }

            // 长按事件
            root.setOnLongClickListener {
                getItem(holder.layoutPosition)?.let { item ->
                    callback.showBookMenu(item.book)
                }
                true
            }
        }
    }

    interface CallBack {
        fun showBookInfo(book: SearchBook)
        fun showBookMenu(book: SearchBook) {
            // 默认空实现
        }
        fun isInBookShelf(name: String, author: String): Boolean
        fun getBookSource(origin: String): io.stillpage.app.data.entities.BookSource?
    }
}

package io.stillpage.app.ui.book.manga.info

import android.content.Context
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.TranslateAnimation
import io.stillpage.app.base.adapter.ItemViewHolder
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.databinding.ItemMangaChapterBinding
import io.stillpage.app.utils.gone
import io.stillpage.app.utils.visible

class ChapterAdapter(context: Context, private val callBack: CallBack) :
    RecyclerAdapter<BookChapter, ItemMangaChapterBinding>(context) {

    interface CallBack {
        fun onChapterClick(chapter: BookChapter, position: Int)
        fun onChapterMoreClick(chapter: BookChapter, position: Int)
    }

    override fun getViewBinding(parent: ViewGroup): ItemMangaChapterBinding {
        return ItemMangaChapterBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemMangaChapterBinding,
        item: BookChapter,
        payloads: MutableList<Any>
    ) {
        binding.run {
            val index = holder.layoutPosition + 1
            tvChapterIndex.text = String.format("%02d", index)
            tvChapterTitle.text = item.getDisplayTitle()
            
            // 显示更新时间（如果有的话）
            if (item.tag.isNullOrEmpty()) {
                tvChapterTime.gone()
            } else {
                tvChapterTime.visible()
                tvChapterTime.text = item.tag
            }

            // 章节点击事件
            root.setOnClickListener {
                callBack.onChapterClick(item, holder.layoutPosition)
            }

            // 更多操作点击事件
            ivMore.setOnClickListener {
                callBack.onChapterMoreClick(item, holder.layoutPosition)
            }
            
            // 为每个章节项添加进入动画
            val animation = TranslateAnimation(
                Animation.RELATIVE_TO_SELF, -1.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f
            ).apply {
                duration = 300
                startOffset = (index * 50).toLong() // 延迟动画开始时间，实现瀑布流效果
            }
            root.startAnimation(animation)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemMangaChapterBinding) {
        // 在convert方法中已经设置了点击事件
    }
}
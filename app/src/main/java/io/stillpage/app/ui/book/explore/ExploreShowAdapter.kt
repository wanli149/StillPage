package io.stillpage.app.ui.book.explore

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import io.stillpage.app.ui.common.ContentTypeUi
import io.stillpage.app.R
import io.stillpage.app.base.adapter.ItemViewHolder
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.data.entities.Book
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.databinding.ItemSearchBinding
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.ContentTypeDetector
import io.stillpage.app.utils.gone
import io.stillpage.app.utils.visible
import io.stillpage.app.ui.main.explore.ExploreNewViewModel.ContentType


class ExploreShowAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    override fun getViewBinding(parent: ViewGroup): ItemSearchBinding {
        return ItemSearchBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchBinding,
        item: SearchBook,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            bind(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bindChange(binding, item, bundle)
            }
        }

    }

    private fun bind(binding: ItemSearchBinding, item: SearchBook) {
        binding.run {
            tvName.text = item.name
            tvAuthor.text = context.getString(R.string.author_show, item.author)
            ivInBookshelf.isVisible = callBack.isInBookshelf(item.name, item.author)
            if (item.latestChapterTitle.isNullOrEmpty()) {
                tvLasted.gone()
            } else {
                tvLasted.text = context.getString(R.string.lasted_show, item.latestChapterTitle)
                tvLasted.visible()
            }
            tvIntroduce.text = item.trimIntro(context)
            val kinds = item.getKindList()
            if (kinds.isEmpty()) {
                llKind.gone()
            } else {
                llKind.visible()
                llKind.setLabels(kinds)
            }

            // 内容类型微标：先用统一解析，必要时再进行智能识别；无书源回退轻量识别
            tvContentType.gone()
            callBack.getBookSource(item.origin) { source ->
                val ct = kotlin.runCatching {
                    if (source != null) {
                        val resolved = io.stillpage.app.help.ContentTypeResolver.resolveFromSource(source)
                        if (resolved != ContentType.TEXT) resolved
                        else ContentTypeDetector.detectContentType(item, source)
                    } else {
                        detectBasicContentType(item)
                    }
                }.getOrElse { detectBasicContentType(item) }
                tvContentType.post { applyTypeLabel(tvContentType, ct) }
            }

            ivCover.load(
                item.coverUrl,
                item.name,
                item.author,
                AppConfig.loadCoverOnlyWifi,
                item.origin
            )
        }
    }

    /**
     * 旧发现页的轻量内容类型识别：仅基于 SearchBook 文本与URL特征。
     * 与新发现页的智能识别保持一致的标签文案，但不依赖书源对象。
     */
    private fun detectBasicContentType(book: SearchBook): ContentType {
        return io.stillpage.app.help.ContentTypeHeuristics.detectBasicFromBook(book)
    }

    private fun applyTypeLabel(tv: TextView, type: ContentType) {
        tv.text = ContentTypeUi.label(type)
        tv.visible()
        val backgroundRes = ContentTypeUi.backgroundRes(type)
        kotlin.runCatching {
            tv.setBackgroundColor(ContextCompat.getColor(tv.context, backgroundRes))
        }.onFailure {
            // 退化为透明背景以避免崩溃
            tv.setBackgroundColor(0x00000000)
        }
    }

    private fun bindChange(binding: ItemSearchBinding, item: SearchBook, bundle: Bundle) {
        binding.run {
            bundle.keySet().forEach {
                when (it) {
                    "isInBookshelf" -> ivInBookshelf.isVisible =
                        callBack.isInBookshelf(item.name, item.author)
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.showBookInfo(it.toBook())
            }
        }
    }

    interface CallBack {
        /**
         * 是否已经加入书架
         */
        fun isInBookshelf(name: String, author: String): Boolean

        fun showBookInfo(book: Book)

        /**
         * 异步获取书源对象，避免主线程 IO
         */
        fun getBookSource(origin: String, onResult: (io.stillpage.app.data.entities.BookSource?) -> Unit)
    }
}
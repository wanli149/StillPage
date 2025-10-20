package io.stillpage.app.ui.main.explore

import android.content.Context
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import io.stillpage.app.R
import io.stillpage.app.base.adapter.ItemViewHolder
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.constant.BookSourceType
import io.stillpage.app.data.entities.SearchBook
import io.stillpage.app.databinding.ItemDiscoveryGridBinding
import io.stillpage.app.help.ContentTypeDetector
import io.stillpage.app.utils.gone
import io.stillpage.app.utils.visible

class DiscoveryAdapterGrid(context: Context, private val callback: CallBack) :
    RecyclerAdapter<ExploreNewViewModel.DiscoveryItem, ItemDiscoveryGridBinding>(context) {

    // ViewHolder复用优化：缓存绑定数据
    private val bindingCache = mutableMapOf<Int, Any>()
    
    override fun getViewBinding(parent: ViewGroup): ItemDiscoveryGridBinding {
        return ItemDiscoveryGridBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemDiscoveryGridBinding,
        item: ExploreNewViewModel.DiscoveryItem,
        payloads: MutableList<Any>
    ) {
        val book = item.book
        val position = holder.layoutPosition
        
        // 检查是否可以使用增量更新
        if (payloads.isNotEmpty()) {
            handlePayloadUpdate(binding, item, payloads)
            return
        }
        
        binding.apply {
            // 优化文本设置：避免重复设置相同内容
            if (tvTitle.text != book.name) {
                tvTitle.text = book.name
            }
            
            if (tvAuthor.text != book.author) {
                tvAuthor.text = book.author
            }

            // 内容类型标签（使用缓存避免重复计算）
            setupContentTypeLabel(item.contentType, binding, position)

            // 书架状态（缓存检查结果）
            val inBookshelf = getBookshelfStatus(book.name, book.author, position)
            if (bvInBookshelf.isVisible != inBookshelf) {
                bvInBookshelf.isVisible = inBookshelf
            }

            // 封面加载优化：延迟加载，避免滚动时频繁加载
            loadCoverOptimized(ivCover, book, position)
        }
    }
    
    /**
     * 处理增量更新
     */
    private fun handlePayloadUpdate(
        binding: ItemDiscoveryGridBinding,
        item: ExploreNewViewModel.DiscoveryItem,
        payloads: MutableList<Any>
    ) {
        payloads.forEach { payload ->
            when (payload) {
                "bookshelf_status" -> {
                    binding.bvInBookshelf.isVisible = callback.isInBookShelf(item.book.name, item.book.author)
                }
                "content_type" -> {
                    setupContentTypeLabel(item.contentType, binding, -1)
                }
            }
        }
    }
    
    /**
     * 优化封面加载
     */
    private fun loadCoverOptimized(
        imageView: io.stillpage.app.ui.widget.image.CoverImageView,
        book: io.stillpage.app.data.entities.SearchBook,
        position: Int
    ) {
        // 使用位置缓存，避免重复加载相同封面
        val cacheKey = "${book.bookUrl}_${book.coverUrl}".hashCode()
        val cachedUrl = bindingCache[cacheKey] as? String
        
        if (cachedUrl != book.coverUrl) {
            imageView.load(book.coverUrl, book.name, book.author)
            bindingCache[cacheKey] = book.coverUrl ?: ""
        }
    }
    
    /**
     * 获取书架状态（带缓存）
     */
    private fun getBookshelfStatus(name: String, author: String, position: Int): Boolean {
        val cacheKey = "${name}_${author}".hashCode()
        val cached = bindingCache[cacheKey] as? Boolean
        
        // 每10个位置重新检查一次，避免状态过期
        if (cached == null || position % 10 == 0) {
            val status = callback.isInBookShelf(name, author)
            bindingCache[cacheKey] = status
            return status
        }
        
        return cached
    }

    private fun setupContentTypeLabel(
        contentType: ExploreNewViewModel.ContentType, 
        binding: ItemDiscoveryGridBinding,
        position: Int
    ) {
        binding.tvContentType.apply {
            // 避免重复设置相同内容
            if (text != contentType.displayName) {
                text = contentType.displayName
            }

            // 使用缓存的颜色值
            val colorCacheKey = "color_${contentType.name}".hashCode()
            val cachedColor = bindingCache[colorCacheKey] as? Int
            
            val color = cachedColor ?: run {
                val backgroundRes = when (contentType) {
                    ExploreNewViewModel.ContentType.TEXT -> R.color.content_type_text
                    ExploreNewViewModel.ContentType.AUDIO -> R.color.content_type_audio
                    ExploreNewViewModel.ContentType.IMAGE -> R.color.content_type_image
                    ExploreNewViewModel.ContentType.MUSIC -> R.color.content_type_music
                    ExploreNewViewModel.ContentType.DRAMA -> R.color.content_type_drama
                    ExploreNewViewModel.ContentType.FILE -> R.color.content_type_file
                    else -> R.color.content_type_text
                }

                val resolvedColor = try {
                    ContextCompat.getColor(context, backgroundRes)
                } catch (e: Exception) {
                    getDefaultColorForType(contentType)
                }
                
                bindingCache[colorCacheKey] = resolvedColor
                resolvedColor
            }
            
            setBackgroundColor(color)
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

    override fun registerListener(holder: ItemViewHolder, binding: ItemDiscoveryGridBinding) {
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

package io.stillpage.app.ui.book.manga.info

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.TextView
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import io.stillpage.app.R
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.databinding.ItemMangaChapterBinding
import io.stillpage.app.utils.gone
import io.stillpage.app.utils.visible

/**
 * 支持分页加载和分组展开/收起的章节适配器
 */
class PagedChapterAdapter(
    context: Context,
    private val callBack: ChapterAdapter.CallBack
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val inflater = LayoutInflater.from(context)
    private val items = mutableListOf<Any>() // 存储章节和分组项
    private var recyclerView: RecyclerView? = null
    private var currentPage = 0
    private val pageSize = 50 // 每页显示的章节数
    
    // 视图类型
    companion object {
        private const val TYPE_CHAPTER = 0
        private const val TYPE_GROUP = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is BookChapter -> TYPE_CHAPTER
            is ChapterGroup -> TYPE_GROUP
            else -> TYPE_CHAPTER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_CHAPTER -> {
                val binding = ItemMangaChapterBinding.inflate(inflater, parent, false)
                ChapterViewHolder(binding)
            }
            TYPE_GROUP -> {
                val view = inflater.inflate(R.layout.item_chapter_group, parent, false)
                GroupViewHolder(view)
            }
            else -> {
                val binding = ItemMangaChapterBinding.inflate(inflater, parent, false)
                ChapterViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChapterViewHolder -> {
                val chapter = items[position] as BookChapter
                holder.bind(chapter, position)
            }
            is GroupViewHolder -> {
                val group = items[position] as ChapterGroup
                holder.bind(group, position)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    /**
     * 设置章节数据并进行分组
     */
    fun setChapters(chapters: List<BookChapter>) {
        items.clear()
        
        // 先添加所有章节
        items.addAll(chapters)
        
        // 从后往前查找卷名章节并进行分组
        for (i in chapters.size - 1 downTo 0) {
            val chapter = chapters[i]
            // 如果是卷名，则创建分组
            if (chapter.isVolume) {
                // 查找这个卷名后面连续的非卷名章节作为该卷的内容
                val groupChapters = mutableListOf<BookChapter>()
                for (j in i + 1 until chapters.size) {
                    val nextChapter = chapters[j]
                    if (!nextChapter.isVolume) {
                        groupChapters.add(nextChapter)
                    } else {
                        break
                    }
                }
                
                // 如果该卷有内容章节，则创建分组
                if (groupChapters.isNotEmpty()) {
                    val group = ChapterGroup(chapter.title, groupChapters)
                    // 在卷名位置插入分组对象
                    items[i] = group
                }
            }
        }
        
        notifyDataSetChanged()
    }

    /**
     * 加载更多章节（分页加载）
     */
    fun loadMoreChapters(chapters: List<BookChapter>) {
        val startPosition = items.size
        items.addAll(chapters)
        notifyItemRangeInserted(startPosition, chapters.size)
    }

    /**
     * 切换分组展开/收起状态
     */
    fun toggleGroup(position: Int) {
        if (position < 0 || position >= items.size) return
        
        val item = items[position]
        if (item is ChapterGroup) {
            item.isExpanded = !item.isExpanded
            
            // 旋转指示器图标实现动画效果
            val viewHolder = recyclerView?.findViewHolderForAdapterPosition(position)
            if (viewHolder is GroupViewHolder) {
                viewHolder.indicatorView.animate()
                    .rotation(if (item.isExpanded) 90f else 0f)
                    .setDuration(200)
                    .start()
            }
            
            if (item.isExpanded) {
                // 展开分组，显示章节
                items.addAll(position + 1, item.chapters)
                notifyItemChanged(position)
                notifyItemRangeInserted(position + 1, item.chapters.size)
            } else {
                // 收起分组，隐藏章节
                val chapterCount = item.chapters.size
                repeat(chapterCount) {
                    items.removeAt(position + 1)
                }
                notifyItemChanged(position)
                notifyItemRangeRemoved(position + 1, chapterCount)
            }
        }
    }

    /**
     * 切换所有分组的展开/收起状态
     */
    fun toggleAllGroups(expand: Boolean) {
        var changed = false
        for (i in items.indices) {
            val item = items[i]
            if (item is ChapterGroup) {
                if (item.isExpanded != expand) {
                    item.isExpanded = expand
                    changed = true
                    
                    // 更新指示器动画
                    val viewHolder = recyclerView?.findViewHolderForAdapterPosition(i)
                    if (viewHolder is GroupViewHolder) {
                        viewHolder.indicatorView.animate()
                            .rotation(if (expand) 90f else 0f)
                            .setDuration(200)
                            .start()
                    }
                    
                    // 根据展开/收起状态更新章节显示
                    if (expand) {
                        // 展开分组，显示章节
                        items.addAll(i + 1, item.chapters)
                        notifyItemRangeInserted(i + 1, item.chapters.size)
                    } else {
                        // 收起分组，隐藏章节
                        val chapterCount = item.chapters.size
                        repeat(chapterCount) {
                            if (i + 1 < items.size) {
                                items.removeAt(i + 1)
                            }
                        }
                        notifyItemRangeRemoved(i + 1, chapterCount)
                    }
                }
            }
        }
        
        // 如果有分组状态改变，更新所有分组项的显示
        if (changed) {
            for (i in items.indices) {
                if (items[i] is ChapterGroup) {
                    notifyItemChanged(i)
                }
            }
        }
    }

    /**
     * 章节项的ViewHolder
     */
    inner class ChapterViewHolder(private val binding: ItemMangaChapterBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(chapter: BookChapter, position: Int) {
            binding.run {
                tvChapterIndex.text = String.format("%02d", position + 1)
                tvChapterTitle.text = chapter.getDisplayTitle()
                
                // 显示更新时间（如果有的话）
                if (chapter.tag.isNullOrEmpty()) {
                    tvChapterTime.gone()
                } else {
                    tvChapterTime.visible()
                    tvChapterTime.text = chapter.tag
                }

                // 章节点击事件
                root.setOnClickListener {
                    callBack.onChapterClick(chapter, position)
                }

                // 更多操作点击事件
                ivMore.setOnClickListener {
                    callBack.onChapterMoreClick(chapter, position)
                }
                
                // 为每个章节项添加进入动画
                val animation = TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, -1.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f
                )
                animation.duration = 300
                animation.startOffset = (position * 50).toLong() // 延迟动画开始时间，实现瀑布流效果
                root.startAnimation(animation)
            }
        }
    }

    /**
     * 分组项的ViewHolder
     */
    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.tv_group_title)
        val indicatorView: ImageView = itemView.findViewById(R.id.view_indicator)
        
        fun bind(group: ChapterGroup, position: Int) {
            titleView.text = group.title
            
            // 设置展开/收起指示器
            indicatorView.rotation = if (group.isExpanded) 90f else 0f
            
            // 分组点击事件
            itemView.setOnClickListener {
                toggleGroup(position)
            }
        }
    }
}
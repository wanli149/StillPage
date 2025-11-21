package io.stillpage.app.ui.book.drama.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import io.stillpage.app.lib.theme.ThemeStore
import io.stillpage.app.utils.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.stillpage.app.R
import io.stillpage.app.base.adapter.ItemViewHolder
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.databinding.ItemDramaEpisodeBinding

/**
 * 短剧集数适配器
 */
class DramaEpisodeAdapter(
    context: Context,
    private val callback: CallBack
) : RecyclerAdapter<BookChapter, ItemDramaEpisodeBinding>(context) {
    private var currentEpisode = 0
    private var playedEpisodes = mutableSetOf<Int>()
    private var isGridMode = true

    /**
     * 设置视图模式
     */
    fun setViewMode(isGrid: Boolean) {
        if (isGridMode != isGrid) {
            isGridMode = isGrid
            notifyDataSetChanged()
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemDramaEpisodeBinding {
        return ItemDramaEpisodeBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemDramaEpisodeBinding,
        item: BookChapter,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            val episodeNumber = item.index + 1

            // 显示集数文本
            tvEpisodeNumber.text = if (isGridMode) {
                episodeNumber.toString()
            } else {
                "第${episodeNumber}集"
            }
            tvEpisodeNumber.visibility = android.view.View.VISIBLE

            // 设置集数状态样式
            updateEpisodeStyle(binding, item.index, episodeNumber)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemDramaEpisodeBinding) {
        binding.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                getItem(pos)?.let { chapter ->
                    callback.onEpisodeClick(chapter, chapter.index)
                }
            }
        }
    }

    /**
     * 更新集数样式
     */
    private fun updateEpisodeStyle(binding: ItemDramaEpisodeBinding, index: Int, episodeNumber: Int) {
        binding.apply {
            val strokePx = context.resources.getDimensionPixelSize(R.dimen.drama_episode_stroke_width)
            when {
                // 当前播放的集数 - 使用主题强调色高亮
                index == currentEpisode -> {
                    val accent = ThemeStore.accentColor(context)
                    root.setCardBackgroundColor(accent)
                    root.strokeColor = accent
                    root.strokeWidth = strokePx
                    val textColor = if (ColorUtils.isColorLight(accent)) {
                        ContextCompat.getColor(context, R.color.md_black_1000)
                    } else {
                        ContextCompat.getColor(context, R.color.md_white_1000)
                    }
                    tvEpisodeNumber.setTextColor(textColor)
                }
                // 已播放但非当前 - 半透明/已读样式
                playedEpisodes.contains(index) -> {
                    root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_grey_100))
                    root.strokeColor = ContextCompat.getColor(context, R.color.md_grey_300)
                    root.strokeWidth = strokePx
                    tvEpisodeNumber.setTextColor(ContextCompat.getColor(context, R.color.textColorSecondary))
                }
                // 默认样式
                else -> {
                    root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.md_white_1000))
                    root.strokeColor = ContextCompat.getColor(context, R.color.md_grey_300)
                    root.strokeWidth = strokePx
                    tvEpisodeNumber.setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
                }
            }
        }
    }

    /**
     * 设置当前播放集数
     */
    fun setCurrentEpisode(episode: Int) {
        val oldEpisode = currentEpisode
        currentEpisode = episode

        // 将之前的集数标记为已播放
        if (oldEpisode >= 0) {
            playedEpisodes.add(oldEpisode)
            notifyItemChanged(oldEpisode)
        }

        // 更新当前集数
        notifyItemChanged(episode)
    }

    /**
     * 设置已播放的集数列表
     */
    fun setPlayedEpisodes(episodes: Set<Int>) {
        playedEpisodes.clear()
        playedEpisodes.addAll(episodes)
        notifyDataSetChanged()
    }

    /**
     * 获取网格布局管理器
     */
    fun getGridLayoutManager(): GridLayoutManager {
        return GridLayoutManager(context, 5)
    }

    interface CallBack {
        /**
         * 集数点击事件
         */
        fun onEpisodeClick(chapter: BookChapter, episode: Int)
    }
}

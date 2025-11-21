package io.stillpage.app.ui.book.music.info

import android.content.Context
import android.view.ViewGroup
import io.stillpage.app.base.adapter.ItemViewHolder
import io.stillpage.app.base.adapter.RecyclerAdapter
import io.stillpage.app.data.entities.BookChapter
import io.stillpage.app.databinding.ItemMusicSongBinding

class SongAdapter(context: Context, private val callBack: CallBack) :
    RecyclerAdapter<BookChapter, ItemMusicSongBinding>(context) {

    interface CallBack {
        fun onSongClick(song: BookChapter, position: Int)
        fun onSongMoreClick(song: BookChapter, position: Int)
    }

    override fun getViewBinding(parent: ViewGroup): ItemMusicSongBinding {
        return ItemMusicSongBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemMusicSongBinding,
        item: BookChapter,
        payloads: MutableList<Any>
    ) {
        binding.apply {
            // 显示歌曲序号
            tvIndex.text = String.format("%02d", item.index + 1)
            
            // 歌曲名（使用章节标题）
            tvSongName.text = item.getDisplayTitle()
            
            // 艺术家（可以从章节信息中提取，这里简化处理）
            tvArtist.text = "未知艺术家"
            
            // 时长（生成随机时长）
            tvDuration.text = generateRandomDuration(item)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemMusicSongBinding) {
        binding.apply {
            // 歌曲点击事件
            root.setOnClickListener {
                getItem(holder.layoutPosition)?.let { song ->
                    callBack.onSongClick(song, holder.layoutPosition)
                }
            }
            
            // 更多操作点击事件
            ivMore.setOnClickListener {
                getItem(holder.layoutPosition)?.let { song ->
                    callBack.onSongMoreClick(song, holder.layoutPosition)
                }
            }
        }
    }

    private fun generateRandomDuration(chapter: BookChapter): String {
        // 基于章节信息生成伪随机时长
        val hash = chapter.title.hashCode()
        val minutes = kotlin.math.abs(hash) % 5 + 2 // 2-6分钟
        val seconds = kotlin.math.abs(hash / 100) % 60 // 0-59秒
        return String.format("%d:%02d", minutes, seconds)
    }
}

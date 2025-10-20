package io.stillpage.app.ui.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.stillpage.app.R

/**
 * 骨架屏适配器
 * 用于在加载时显示占位内容
 */
class SkeletonAdapter(
    private val context: Context,
    private val skeletonType: SkeletonView.SkeletonType,
    private val itemCount: Int = 6
) : RecyclerView.Adapter<SkeletonAdapter.SkeletonViewHolder>() {

    private val skeletonViews = mutableListOf<SkeletonView>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonViewHolder {
        val layoutRes = when (skeletonType) {
            SkeletonView.SkeletonType.GRID_ITEM -> R.layout.item_skeleton_grid
            SkeletonView.SkeletonType.LIST_ITEM -> R.layout.item_skeleton_list
            else -> R.layout.item_skeleton_grid
        }
        
        val view = LayoutInflater.from(context).inflate(layoutRes, parent, false)
        return SkeletonViewHolder(view)
    }

    override fun onBindViewHolder(holder: SkeletonViewHolder, position: Int) {
        val skeletonView = holder.itemView.findViewById<SkeletonView>(R.id.skeleton_view)
            ?: holder.itemView as? SkeletonView
            ?: holder.itemView.findViewById<SkeletonView>(android.R.id.content)
        
        skeletonView?.let { skeleton ->
            skeleton.setSkeletonType(skeletonType)
            skeleton.startShimmer()
            skeletonViews.add(skeleton)
        }
    }

    override fun getItemCount(): Int = itemCount

    /**
     * 停止所有动画
     */
    fun stopAllAnimations() {
        skeletonViews.forEach { it.stopShimmer() }
        skeletonViews.clear()
    }

    class SkeletonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
package io.stillpage.app.ui.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.stillpage.app.R
import io.stillpage.app.constant.AppLog

/**
 * 状态布局管理器
 * 统一管理加载、错误、空状态的显示
 */
class StateLayoutManager(
    private val context: Context,
    private val recyclerView: RecyclerView,
    private val emptyMessageView: View
) {
    
    // 状态枚举
    enum class State {
        LOADING,    // 加载中（骨架屏）
        CONTENT,    // 有内容
        EMPTY,      // 空状态
        ERROR       // 错误状态
    }
    
    private var currentState = State.LOADING
    private var skeletonAdapter: SkeletonAdapter? = null
    private var errorStateView: View? = null
    private var emptyStateView: View? = null
    private var isGridMode = true
    
    // 回调接口
    interface StateCallback {
        fun onRetryClicked()
        fun onSettingsClicked() {}
        fun onRefreshClicked() {}
    }
    
    private var callback: StateCallback? = null
    
    /**
     * 设置回调
     */
    fun setCallback(callback: StateCallback) {
        this.callback = callback
    }
    
    /**
     * 设置显示模式
     */
    fun setGridMode(isGrid: Boolean) {
        this.isGridMode = isGrid
    }
    
    /**
     * 显示加载状态（骨架屏）
     */
    fun showLoading() {
        if (currentState == State.LOADING) return
        
        currentState = State.LOADING
        hideAllStates()
        
        val skeletonType = if (isGridMode) {
            SkeletonView.SkeletonType.GRID_ITEM
        } else {
            SkeletonView.SkeletonType.LIST_ITEM
        }
        
        skeletonAdapter = SkeletonAdapter(
            context, 
            skeletonType, 
            if (isGridMode) 9 else 6
        )
        
        recyclerView.adapter = skeletonAdapter
        recyclerView.visibility = View.VISIBLE
        
        AppLog.put("StateLayoutManager: 显示加载状态")
    }
    
    /**
     * 显示内容状态
     */
    fun showContent(adapter: RecyclerView.Adapter<*>) {
        if (currentState == State.CONTENT) return
        
        currentState = State.CONTENT
        hideAllStates()
        
        // 停止骨架屏动画
        skeletonAdapter?.stopAllAnimations()
        skeletonAdapter = null
        
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        
        AppLog.put("StateLayoutManager: 显示内容状态")
    }
    
    /**
     * 显示空状态
     */
    fun showEmpty(message: String = "暂无内容") {
        if (currentState == State.EMPTY) return
        
        currentState = State.EMPTY
        hideAllStates()
        
        if (emptyStateView == null) {
            createEmptyStateView()
        }
        
        emptyStateView?.let { view ->
            val titleView = view.findViewById<android.widget.TextView>(R.id.tv_empty_title)
            val messageView = view.findViewById<android.widget.TextView>(R.id.tv_empty_message)
            
            titleView?.text = "暂无内容"
            messageView?.text = message
            
            view.visibility = View.VISIBLE
        }
        
        recyclerView.visibility = View.GONE
        
        AppLog.put("StateLayoutManager: 显示空状态 - $message")
    }
    
    /**
     * 显示错误状态
     */
    fun showError(title: String = "加载失败", message: String = "网络连接异常，请检查网络设置") {
        if (currentState == State.ERROR) return
        
        currentState = State.ERROR
        hideAllStates()
        
        if (errorStateView == null) {
            createErrorStateView()
        }
        
        errorStateView?.let { view ->
            val titleView = view.findViewById<android.widget.TextView>(R.id.tv_error_title)
            val messageView = view.findViewById<android.widget.TextView>(R.id.tv_error_message)
            
            titleView?.text = title
            messageView?.text = message
            
            view.visibility = View.VISIBLE
        }
        
        recyclerView.visibility = View.GONE
        
        AppLog.put("StateLayoutManager: 显示错误状态 - $title: $message")
    }
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(): State = currentState
    
    /**
     * 隐藏所有状态视图
     */
    private fun hideAllStates() {
        emptyMessageView.visibility = View.GONE
        errorStateView?.visibility = View.GONE
        emptyStateView?.visibility = View.GONE
        
        // 停止骨架屏动画
        skeletonAdapter?.stopAllAnimations()
    }
    
    /**
     * 创建错误状态视图
     */
    private fun createErrorStateView() {
        val parent = recyclerView.parent as? ViewGroup ?: return
        
        errorStateView = LayoutInflater.from(context)
            .inflate(R.layout.layout_error_state, parent, false)
        
        errorStateView?.let { view ->
            // 设置点击事件
            val retryButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_retry)
            val settingsButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_settings)
            
            retryButton?.setOnClickListener {
                callback?.onRetryClicked()
            }
            
            settingsButton?.setOnClickListener {
                callback?.onSettingsClicked()
            }
            
            parent.addView(view)
        }
    }
    
    /**
     * 创建空状态视图
     */
    private fun createEmptyStateView() {
        val parent = recyclerView.parent as? ViewGroup ?: return
        
        emptyStateView = LayoutInflater.from(context)
            .inflate(R.layout.layout_empty_state, parent, false)
        
        emptyStateView?.let { view ->
            // 设置点击事件
            val refreshButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_refresh)
            
            refreshButton?.setOnClickListener {
                callback?.onRefreshClicked()
            }
            
            parent.addView(view)
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        skeletonAdapter?.stopAllAnimations()
        skeletonAdapter = null
        
        val parent = recyclerView.parent as? ViewGroup
        errorStateView?.let { parent?.removeView(it) }
        emptyStateView?.let { parent?.removeView(it) }
        
        errorStateView = null
        emptyStateView = null
        callback = null
    }
}
package io.stillpage.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import io.stillpage.app.R

/**
 * 骨架屏组件
 * 提供优雅的加载占位效果
 */
class SkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 骨架屏类型
    enum class SkeletonType {
        GRID_ITEM,      // 网格项目
        LIST_ITEM,      // 列表项目
        CUSTOM          // 自定义
    }

    private var skeletonType = SkeletonType.GRID_ITEM
    private var isAnimating = false
    
    // 绘制相关
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cornerRadius = 8f
    
    // 动画相关
    private var shimmerAnimator: ValueAnimator? = null
    private var shimmerOffset = 0f
    private val shimmerWidth = 200f
    
    // 颜色
    private val backgroundColor: Int
    private val shimmerColor: Int
    
    init {
        // 获取主题颜色
        backgroundColor = ContextCompat.getColor(context, R.color.skeleton_background)
        shimmerColor = ContextCompat.getColor(context, R.color.shimmer_highlight)
        
        // 解析自定义属性
        context.obtainStyledAttributes(attrs, R.styleable.SkeletonView, defStyleAttr, 0).apply {
            try {
                val typeIndex = getInt(R.styleable.SkeletonView_skeletonType, 0)
                skeletonType = SkeletonType.values()[typeIndex]
            } finally {
                recycle()
            }
        }
        
        setupPaint()
    }
    
    private fun setupPaint() {
        paint.color = backgroundColor
        paint.style = Paint.Style.FILL
        
        shimmerPaint.style = Paint.Style.FILL
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        when (skeletonType) {
            SkeletonType.GRID_ITEM -> drawGridItemSkeleton(canvas)
            SkeletonType.LIST_ITEM -> drawListItemSkeleton(canvas)
            SkeletonType.CUSTOM -> drawCustomSkeleton(canvas)
        }
        
        if (isAnimating) {
            drawShimmerEffect(canvas)
        }
    }
    
    /**
     * 绘制网格项目骨架
     */
    private fun drawGridItemSkeleton(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        
        // 封面区域 (占70%高度)
        val coverHeight = height * 0.7f
        canvas.drawRoundRect(
            0f, 0f, width, coverHeight,
            cornerRadius, cornerRadius, paint
        )
        
        // 标题区域
        val titleTop = coverHeight + 12f
        val titleHeight = 16f
        canvas.drawRoundRect(
            8f, titleTop, width - 8f, titleTop + titleHeight,
            cornerRadius / 2, cornerRadius / 2, paint
        )
        
        // 作者区域
        val authorTop = titleTop + titleHeight + 8f
        val authorWidth = width * 0.6f
        canvas.drawRoundRect(
            8f, authorTop, authorWidth, authorTop + 12f,
            cornerRadius / 2, cornerRadius / 2, paint
        )
    }
    
    /**
     * 绘制列表项目骨架
     */
    private fun drawListItemSkeleton(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        
        // 封面区域 (左侧固定宽度)
        val coverWidth = 80f
        val coverHeight = height - 16f
        canvas.drawRoundRect(
            8f, 8f, coverWidth, coverHeight,
            cornerRadius, cornerRadius, paint
        )
        
        // 内容区域
        val contentLeft = coverWidth + 16f
        val contentWidth = width - contentLeft - 8f
        
        // 标题
        canvas.drawRoundRect(
            contentLeft, 16f, contentLeft + contentWidth * 0.8f, 32f,
            cornerRadius / 2, cornerRadius / 2, paint
        )
        
        // 作者
        canvas.drawRoundRect(
            contentLeft, 40f, contentLeft + contentWidth * 0.5f, 52f,
            cornerRadius / 2, cornerRadius / 2, paint
        )
        
        // 简介（多行）
        val introTop = 60f
        val lineHeight = 14f
        val lineSpacing = 4f
        
        for (i in 0..2) {
            val lineTop = introTop + i * (lineHeight + lineSpacing)
            val lineWidth = when (i) {
                0 -> contentWidth * 0.9f
                1 -> contentWidth * 0.7f
                else -> contentWidth * 0.4f
            }
            canvas.drawRoundRect(
                contentLeft, lineTop, contentLeft + lineWidth, lineTop + lineHeight,
                cornerRadius / 3, cornerRadius / 3, paint
            )
        }
    }
    
    /**
     * 绘制自定义骨架
     */
    private fun drawCustomSkeleton(canvas: Canvas) {
        // 默认绘制简单矩形
        canvas.drawRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            cornerRadius, cornerRadius, paint
        )
    }
    
    /**
     * 绘制闪光效果
     */
    private fun drawShimmerEffect(canvas: Canvas) {
        val gradient = LinearGradient(
            shimmerOffset - shimmerWidth, 0f,
            shimmerOffset, height.toFloat(),
            intArrayOf(
                Color.TRANSPARENT,
                shimmerColor,
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        
        shimmerPaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shimmerPaint)
    }
    
    /**
     * 开始动画
     */
    fun startShimmer() {
        if (isAnimating) return
        
        isAnimating = true
        shimmerAnimator = ValueAnimator.ofFloat(0f, width + shimmerWidth).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener { animator ->
                shimmerOffset = animator.animatedValue as Float
                invalidate()
            }
            
            start()
        }
    }
    
    /**
     * 停止动画
     */
    fun stopShimmer() {
        isAnimating = false
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopShimmer()
    }
    
    /**
     * 设置骨架屏类型
     */
    fun setSkeletonType(type: SkeletonType) {
        if (skeletonType != type) {
            skeletonType = type
            invalidate()
        }
    }
}
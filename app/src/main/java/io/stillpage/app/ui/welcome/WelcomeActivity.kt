package io.stillpage.app.ui.welcome

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.postDelayed
import androidx.viewbinding.ViewBinding
import io.stillpage.app.R
import io.stillpage.app.base.BaseActivity
import io.stillpage.app.constant.PreferKey
import io.stillpage.app.constant.Theme
import io.stillpage.app.databinding.ActivityWelcomeBinding
import io.stillpage.app.databinding.ActivityWelcomeDreamBinding
import io.stillpage.app.help.config.ThemeConfig
import io.stillpage.app.lib.theme.accentColor
import io.stillpage.app.lib.theme.backgroundColor
import io.stillpage.app.ui.book.read.ReadBookActivity
import io.stillpage.app.ui.main.MainActivity
import io.stillpage.app.utils.*
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import io.stillpage.app.lib.dialogs.alert
import java.util.*
import kotlin.random.Random

// 扩展函数：dp转px
val Float.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density + 0.5f).toInt()

open class WelcomeActivity : BaseActivity<ViewBinding>() {

    private var _binding: ViewBinding? = null
    override val binding get() = _binding!!

    private var isDreamVersion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 检测启动来源
        detectLaunchSource()

        // 根据启动来源设置不同的布局
        _binding = if (isDreamVersion) {
            ActivityWelcomeDreamBinding.inflate(layoutInflater)
        } else {
            ActivityWelcomeBinding.inflate(layoutInflater)
        }

        setContentView(binding.root)
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        // 避免从桌面启动程序后，会重新实例化入口类的activity
        if (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0) {
            finish()
        } else {
            // 如果是梦幻版，启动动态效果
            if (isDreamVersion) {
                startDreamEffects()
            }
            // 检查语言设置
            checkLanguageSettings()
        }
    }

    private fun detectLaunchSource() {
        // 检测是否从梦幻版图标启动
        // 通过检查ComponentName来判断
        val componentName = intent.component?.className
        isDreamVersion = componentName?.contains("Launcher1") == true
    }

    override fun setupSystemBar() {
        fullScreen()
        setStatusBarColorAuto(backgroundColor, true, fullScreen)
        upNavigationBarColor()
    }

    override fun upBackgroundImage() {
        if (getPrefBoolean(PreferKey.customWelcome) && !isDreamVersion) {
            kotlin.runCatching {
                when (ThemeConfig.getTheme()) {
                    Theme.Dark -> getPrefString(PreferKey.welcomeImageDark)?.let { path ->
                        val size = windowManager.windowSize
                        BitmapUtils.decodeBitmap(path, size.widthPixels, size.heightPixels).let {
                            (binding as? ActivityWelcomeBinding)?.tvSymbol?.visible(getPrefBoolean(PreferKey.welcomeShowTextDark))
                            window.decorView.background = BitmapDrawable(resources, it)
                            return
                        }
                    }
                    else -> getPrefString(PreferKey.welcomeImage)?.let { path ->
                        val size = windowManager.windowSize
                        BitmapUtils.decodeBitmap(path, size.widthPixels, size.heightPixels).let {
                            (binding as? ActivityWelcomeBinding)?.tvSymbol?.visible(getPrefBoolean(PreferKey.welcomeShowText))
                            window.decorView.background = BitmapDrawable(resources, it)
                            return
                        }
                    }
                }
            }
        }
        super.upBackgroundImage()
    }

    private fun checkLanguageSettings() {
        // 检查是否已经设置过语言
        val currentLanguage = getPrefString(PreferKey.language, "auto")
        if (currentLanguage != "auto") {
            // 已经设置过语言，直接启动主界面
            binding.root.postDelayed(600) { startMainActivity() }
            return
        }

        // 获取系统语言
        val systemLocale = Locale.getDefault()
        val systemLanguage = systemLocale.language
        val systemCountry = systemLocale.country

        when {
            // 检测到英文系统
            systemLanguage == "en" -> {
                showLanguageDialog(
                    "Language Setting",
                    "We detected that your system language is English. Would you like to switch the app language to English?",
                    "English",
                    "en"
                )
            }
            // 检测到繁体中文系统（台湾或香港）
            systemLanguage == "zh" && (systemCountry == "TW" || systemCountry == "HK" || systemCountry == "MO") -> {
                showLanguageDialog(
                    "語言設定",
                    "我們偵測到您的系統語言是繁體中文，是否要將應用程式語言切換為繁體中文？",
                    "繁體中文",
                    "tw"
                )
            }
            else -> {
                // 默认使用简体中文，设置语言偏好并启动主界面
                putPrefString(PreferKey.language, "zh")
                binding.root.postDelayed(600) { startMainActivity() }
            }
        }
    }

    private fun showLanguageDialog(title: String, message: String, languageName: String, languageCode: String) {
        alert(title = title, message = message) {
            positiveButton(android.R.string.ok) {
                // 用户选择切换语言
                putPrefString(PreferKey.language, languageCode)
                // 重启应用以应用新语言
                restart()
            }
            negativeButton(android.R.string.cancel) {
                // 用户选择保持中文
                putPrefString(PreferKey.language, "zh")
                startMainActivity()
            }
        }
    }

    private fun startMainActivity() {
        startActivity<MainActivity>()
        if (getPrefBoolean(PreferKey.defaultToRead)) {
            startActivity<ReadBookActivity>()
        }
        finish()
    }

    private fun startDreamEffects() {
        val dreamBinding = binding as? ActivityWelcomeDreamBinding ?: return

        // 创建动态气泡效果
        createFloatingBubbles(dreamBinding.root)
    }

    private fun createFloatingBubbles(container: ViewGroup) {
        val colors = intArrayOf(
            0x9987CEEB.toInt(), // rgba(135, 206, 235, 0.6)
            0x80FFD700.toInt(), // rgba(255, 215, 0, 0.5)
            0x99FFB6C1.toInt(), // rgba(255, 182, 193, 0.6)
            0x8090EE90.toInt(), // rgba(144, 238, 144, 0.5)
            0x99DDA0DD.toInt(), // rgba(221, 160, 221, 0.6)
            0x80FFA07A.toInt()  // rgba(255, 160, 122, 0.5)
        )

        repeat(15) {
            val bubble = View(this).apply {
                val size = (Random.nextFloat() * 50 + 10).dp
                layoutParams = FrameLayout.LayoutParams(size, size)

                // 设置圆形背景
                background = createCircleDrawable(colors[(0 until colors.size).random()])

                // 设置初始位置
                x = Random.nextFloat() * container.width.toFloat()
                y = Random.nextFloat() * container.height.toFloat()

                alpha = 0f
            }

            container.addView(bubble)

            // 启动动画
            startBubbleAnimation(bubble, container)
        }
    }

    private fun createCircleDrawable(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun startBubbleAnimation(bubble: View, container: ViewGroup) {
        val duration = (Random.nextFloat() * 20000 + 20000).toLong() // 20-40秒
        val delay = (Random.nextFloat() * 20000).toLong() // 0-20秒延迟

        val startX = bubble.x
        val startY = bubble.y

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            startDelay = delay
            repeatCount = ValueAnimator.INFINITE

            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float

                // 计算新位置（模拟浮动轨迹）
                val newX = startX + kotlin.math.sin(progress * 4 * kotlin.math.PI).toFloat() * 50
                val newY = startY - progress * container.height.toFloat() * 1.5f

                bubble.x = newX
                bubble.y = newY

                // 透明度变化
                bubble.alpha = when {
                    progress < 0.1f -> progress * 7f // 淡入
                    progress > 0.9f -> (1f - progress) * 10f // 淡出
                    else -> 0.6f // 中间保持
                }

                // 如果气泡移出屏幕，重置位置
                if (newY < -bubble.height) {
                    bubble.x = Random.nextFloat() * container.width.toFloat()
                    bubble.y = container.height.toFloat() + bubble.height
                }
            }
        }

        animator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

}
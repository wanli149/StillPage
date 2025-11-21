package io.stillpage.app.help

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import io.stillpage.app.R
import io.stillpage.app.ui.welcome.WelcomeActivity
import io.stillpage.app.utils.toastOnUi
import splitties.init.appCtx

/**
 * Created by GKF on 2018/2/27.
 * 更换图标
 */
object LauncherIconHelp {
    private val packageManager: PackageManager = appCtx.packageManager
    private val componentNames = arrayListOf(
        ComponentName(appCtx, "io.stillpage.app.ui.welcome.Launcher1")
    )

    fun changeIcon(icon: String?) {
        if (icon.isNullOrEmpty()) return
        if (Build.VERSION.SDK_INT < 26) {
            appCtx.toastOnUi(R.string.change_icon_error)
            return
        }

        try {
            var hasEnabled = false
            componentNames.forEach {
                if (icon.equals(it.className.substringAfterLast("."), true)) {
                    hasEnabled = true
                    //启用别名
                    packageManager.setComponentEnabledSetting(
                        it,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } else {
                    //禁用别名
                    packageManager.setComponentEnabledSetting(
                        it,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            // 根据是否启用别名来控制主Activity的显示
            if (hasEnabled) {
                // 启用了别名，禁用主Activity的启动器显示
                packageManager.setComponentEnabledSetting(
                    ComponentName(appCtx, WelcomeActivity::class.java.name),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                // 没有启用别名，启用主Activity
                packageManager.setComponentEnabledSetting(
                    ComponentName(appCtx, WelcomeActivity::class.java.name),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            appCtx.toastOnUi("图标切换失败: ${e.message}")
        }
    }

    /**
     * 重置到默认图标（用于修复启动问题）
     */
    fun resetToDefault() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        try {
            // 禁用所有替代图标
            componentNames.forEach {
                packageManager.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            // 启用默认的WelcomeActivity
            packageManager.setComponentEnabledSetting(
                ComponentName(appCtx, WelcomeActivity::class.java.name),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 强制重置所有组件状态（用于修复启动问题）
     */
    fun forceResetComponents() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        try {
            // 强制启用WelcomeActivity
            packageManager.setComponentEnabledSetting(
                ComponentName(appCtx, WelcomeActivity::class.java.name),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // 强制禁用所有别名
            componentNames.forEach {
                packageManager.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            // 重置图标偏好设置
            appCtx.getSharedPreferences("app_preferences", 0)
                .edit()
                .putString("launcherIcon", "ic_launcher")
                .apply()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
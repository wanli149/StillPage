package io.stillpage.app.help.source

import io.stillpage.app.constant.AppLog
import io.stillpage.app.data.entities.BaseSource
import io.stillpage.app.data.entities.BookSource
import io.stillpage.app.exception.NoStackTraceException
import io.stillpage.app.help.CacheManager
import io.stillpage.app.help.IntentData
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.ui.association.VerificationCodeActivity
import io.stillpage.app.ui.browser.WebViewActivity
import io.stillpage.app.utils.isMainThread
import io.stillpage.app.utils.startActivity
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration.Companion.minutes

/**
 * 源验证
 */
object SourceVerificationHelp {

    private val waitTime = 1.minutes.inWholeNanoseconds

    private fun getVerificationResultKey(source: BaseSource) =
        getVerificationResultKey(source.getKey())

    private fun getVerificationResultKey(sourceKey: String) = "${sourceKey}_verificationResult"

    /**
     * 获取书源验证结果
     * 图片验证码 防爬 滑动验证码 点击字符 等等
     */
    @Synchronized
    fun getVerificationResult(
        source: BaseSource?,
        url: String,
        title: String,
        useBrowser: Boolean,
        refetchAfterSuccess: Boolean = true
    ): String {
        source
            ?: throw NoStackTraceException("getVerificationResult parameter source cannot be null")
        require(url.length < 64 * 1024) { "getVerificationResult parameter url too long" }
        check(!isMainThread) { "getVerificationResult must be called on a background thread" }

        clearResult(source.getKey())

        // 如果不使用浏览器且启用了自动验证码识别，先尝试自动识别
        if (!useBrowser && AppConfig.enableAutoVerificationCode) {
            try {
                val autoResult = runBlocking {
                    autoHandleVerificationCode(url, source)
                }
                if (!autoResult.isNullOrBlank()) {
                    AppLog.put("自动验证码识别成功，跳过用户输入: $autoResult")
                    return autoResult
                }
            } catch (e: Exception) {
                AppLog.put("自动验证码识别异常，回退到用户输入", e)
            }
        }

        if (!useBrowser) {
            appCtx.startActivity<VerificationCodeActivity> {
                putExtra("imageUrl", url)
                putExtra("sourceOrigin", source.getKey())
                putExtra("sourceName", source.getTag())
                putExtra("sourceType", source.getSourceType())
                IntentData.put(getVerificationResultKey(source), Thread.currentThread())
            }
        } else {
            startBrowser(source, url, title, true, refetchAfterSuccess)
        }

        var waitUserInput = false
        while (getResult(source.getKey()) == null) {
            if (!waitUserInput) {
                AppLog.putDebug("等待返回验证结果...")
                waitUserInput = true
            }
            LockSupport.parkNanos(this, waitTime)
        }

        return getResult(source.getKey())!!.let {
            it.ifBlank {
                throw NoStackTraceException("验证结果为空")
            }
        }
    }

    /**
     * 启动内置浏览器
     * @param saveResult 保存网页源代码到数据库
     */
    fun startBrowser(
        source: BaseSource?,
        url: String,
        title: String,
        saveResult: Boolean? = false,
        refetchAfterSuccess: Boolean? = true
    ) {
        source ?: throw NoStackTraceException("startBrowser parameter source cannot be null")
        require(url.length < 64 * 1024) { "startBrowser parameter url too long" }
        appCtx.startActivity<WebViewActivity> {
            putExtra("title", title)
            putExtra("url", url)
            putExtra("sourceOrigin", source.getKey())
            putExtra("sourceName", source.getTag())
            putExtra("sourceType", source.getSourceType())
            putExtra("sourceVerificationEnable", saveResult)
            putExtra("refetchAfterSuccess", refetchAfterSuccess)
            IntentData.put(getVerificationResultKey(source), Thread.currentThread())
        }
    }


    fun checkResult(sourceKey: String) {
        getResult(sourceKey) ?: setResult(sourceKey, "")
        val thread = IntentData.get<Thread>(getVerificationResultKey(sourceKey))
        LockSupport.unpark(thread)
    }

    fun setResult(sourceKey: String, result: String?) {
        CacheManager.putMemory(getVerificationResultKey(sourceKey), result ?: "")
    }

    fun getResult(sourceKey: String): String? {
        return CacheManager.get(getVerificationResultKey(sourceKey))
    }

    fun clearResult(sourceKey: String) {
        CacheManager.delete(getVerificationResultKey(sourceKey))
    }

    /**
     * 自动处理验证码
     * @param imageUrl 验证码图片URL
     * @param source 书源信息
     * @return 识别结果，失败返回null
     */
    suspend fun autoHandleVerificationCode(
        imageUrl: String,
        source: BaseSource?
    ): String? {
        if (!AppConfig.enableAutoVerificationCode) {
            return null
        }

        return try {
            AppLog.put("开始自动识别验证码: ${source?.getTag()} - $imageUrl")
            val result = AutoVerificationCodeHelper.recognizeVerificationCode(
                imageUrl,
                (source as? BookSource)?.bookSourceUrl
            )

            if (result != null) {
                AppLog.put("自动验证码识别成功: $result")
                // 缓存识别结果
                setResult(source?.getKey() ?: "", result)
            } else {
                AppLog.put("自动验证码识别失败，需要用户手动输入")
            }

            result
        } catch (e: Exception) {
            AppLog.put("自动验证码处理异常", e)
            null
        }
    }

    /**
     * 获取验证码结果（包含自动识别）
     */
    fun getVerificationResult(source: BaseSource?): String {
        val key = source?.getKey() ?: ""
        return getResult(key) ?: ""
    }
}

package io.stillpage.app.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.size
import io.stillpage.app.R
import io.stillpage.app.base.VMBaseActivity
import io.stillpage.app.constant.AppConst
import io.stillpage.app.databinding.ActivityWebViewBinding
import io.stillpage.app.help.config.AppConfig
import io.stillpage.app.help.http.CookieStore
import io.stillpage.app.help.source.SourceVerificationHelp
import io.stillpage.app.lib.dialogs.SelectItem
import io.stillpage.app.lib.dialogs.alert
import io.stillpage.app.lib.theme.accentColor
import io.stillpage.app.model.Download
import io.stillpage.app.ui.association.OnLineImportActivity
import io.stillpage.app.ui.file.HandleFileContract
import io.stillpage.app.utils.ACache
import io.stillpage.app.utils.gone
import io.stillpage.app.utils.invisible
import io.stillpage.app.utils.keepScreenOn
import io.stillpage.app.utils.longSnackbar
import io.stillpage.app.utils.openUrl
import io.stillpage.app.utils.sendToClip
import io.stillpage.app.utils.setDarkeningAllowed
import io.stillpage.app.utils.startActivity
import io.stillpage.app.utils.toggleSystemBar
import io.stillpage.app.utils.viewbindingdelegate.viewBinding
import io.stillpage.app.utils.visible
import java.net.URLDecoder
import io.stillpage.app.help.http.CookieManager as AppCookieManager

class WebViewActivity : VMBaseActivity<ActivityWebViewBinding, WebViewModel>() {

    override val binding by viewBinding(ActivityWebViewBinding::inflate)
    override val viewModel by viewModels<WebViewModel>()
    private val imagePathKey = "imagePath"
    private var customWebViewCallback: WebChromeClient.CustomViewCallback? = null
    private var webPic: String? = null
    private var isCloudflareChallenge = false
    private var isFullScreen = false
    private var exitOnFirstBack: Boolean = false
    private var lastCapturedVideoUrl: String? = null
    private val saveImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            ACache.get().put(imagePathKey, uri.toString())
            viewModel.saveImage(webPic, uri.toString())
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = intent.getStringExtra("title") ?: getString(R.string.loading)
        binding.titleBar.subtitle = intent.getStringExtra("sourceName")
        exitOnFirstBack = intent.getBooleanExtra("exitOnFirstBack", false)
        viewModel.initData(intent) {
            val url = viewModel.baseUrl
            val headerMap = viewModel.headerMap
            initWebView(url, headerMap)
            val html = viewModel.html
            if (html.isNullOrEmpty()) {
                binding.webView.loadUrl(url, headerMap)
            } else {
                binding.webView.loadDataWithBaseURL(url, html, "text/html", "utf-8", url)
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            if (binding.customWebView.size > 0) {
                customWebViewCallback?.onCustomViewHidden()
                return@addCallback
            }
            if (isFullScreen) {
                toggleFullScreen()
                return@addCallback
            }
            if (!isFullScreen && exitOnFirstBack) {
                finish()
                return@addCallback
            }
            if (binding.webView.canGoBack() && binding.webView.copyBackForwardList().size > 1) {
                binding.webView.goBack()
                return@addCallback
            }
            finish()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.web_view, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (viewModel.sourceOrigin.isNotEmpty()) {
            menu.findItem(R.id.menu_disable_source)?.isVisible = true
            menu.findItem(R.id.menu_delete_source)?.isVisible = true
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_open_in_browser -> openUrl(viewModel.baseUrl)
            R.id.menu_copy_url -> sendToClip(viewModel.baseUrl)
            R.id.menu_ok -> {
                if (viewModel.sourceVerificationEnable) {
                    viewModel.saveVerificationResult(binding.webView) {
                        finish()
                    }
                } else {
                    finish()
                }
            }

            R.id.menu_full_screen -> toggleFullScreen()
            R.id.menu_disable_source -> {
                viewModel.disableSource {
                    finish()
                }
            }

            R.id.menu_delete_source -> {
                alert(R.string.draw) {
                    setMessage(getString(R.string.sure_del) + "\n" + viewModel.sourceName)
                    noButton()
                    yesButton {
                        viewModel.deleteSource {
                            finish()
                        }
                    }
                }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    //实现starBrowser调起页面全屏
    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen

        toggleSystemBar(!isFullScreen)

        if (isFullScreen) {
            supportActionBar?.hide()
        } else {
            supportActionBar?.show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView(url: String, headerMap: HashMap<String, String>) {
        binding.progressBar.fontColor = accentColor
        binding.webView.webChromeClient = CustomWebChromeClient()
        binding.webView.webViewClient = CustomWebViewClient()
        binding.webView.settings.apply {
            setDarkeningAllowed(AppConfig.isNightTheme)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            headerMap[AppConst.UA_NAME]?.let {
                userAgentString = it
            }
        }
        AppCookieManager.applyToWebView(url)
        binding.webView.setOnLongClickListener {
            val hitTestResult = binding.webView.hitTestResult
            if (hitTestResult.type == WebView.HitTestResult.IMAGE_TYPE ||
                hitTestResult.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            ) {
                hitTestResult.extra?.let {
                    saveImage(it)
                    return@setOnLongClickListener true
                }
            }
            return@setOnLongClickListener false
        }
        binding.webView.setDownloadListener { downloadUrl, _, contentDisposition, _, _ ->
            var fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, null)
            fileName = URLDecoder.decode(fileName, "UTF-8")
            binding.llView.longSnackbar(fileName, getString(R.string.action_download)) {
                Download.start(this, downloadUrl, fileName)
            }
        }
    }

    private fun saveImage(webPic: String) {
        this.webPic = webPic
        val path = ACache.get().getAsString(imagePathKey)
        if (path.isNullOrEmpty()) {
            selectSaveFolder()
        } else {
            viewModel.saveImage(webPic, path)
        }
    }

    private fun selectSaveFolder() {
        val default = arrayListOf<SelectItem<Int>>()
        val path = ACache.get().getAsString(imagePathKey)
        if (!path.isNullOrEmpty()) {
            default.add(SelectItem(path, -1))
        }
        saveImage.launch {
            otherActions = default
        }
    }

    override fun finish() {
        SourceVerificationHelp.checkResult(viewModel.sourceOrigin)
        super.finish()
    }

    override fun onDestroy() {
        // Ensure custom full-screen view is closed if still active
        try { customWebViewCallback?.onCustomViewHidden() } catch (_: Throwable) {}
        binding.customWebView.removeAllViews()

        // Strict destruction order to avoid WebView renderer crashes
        try {
            binding.webView.apply {
                // 1) Remove from parent first
                try { (parent as? android.view.ViewGroup)?.removeView(this) } catch (_: Throwable) {}
                // 2) Perform other cleanup
                try { stopLoading() } catch (_: Throwable) {}
                try { settings.javaScriptEnabled = false } catch (_: Throwable) {}
                try { clearHistory() } catch (_: Throwable) {}
                try { removeAllViews() } catch (_: Throwable) {}
                // 3) Finally destroy
                try { destroy() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
            // Ignore cleanup exceptions
        }

        super.onDestroy()
    }

    override fun onPause() {
        try {
            binding.webView.onPause()
            binding.webView.pauseTimers()
        } catch (_: Throwable) {}
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        try {
            binding.webView.onResume()
            binding.webView.resumeTimers()
        } catch (_: Throwable) {}
    }

    inner class CustomWebChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            binding.progressBar.setDurProgress(newProgress)
            binding.progressBar.gone(newProgress == 100)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            binding.llView.invisible()
            binding.customWebView.addView(view)
            customWebViewCallback = callback
            keepScreenOn(true)
            toggleSystemBar(false)
        }

        override fun onHideCustomView() {
            binding.customWebView.removeAllViews()
            binding.llView.visible()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            keepScreenOn(false)
            toggleSystemBar(true)
        }
    }

    inner class CustomWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
            try {
                val reqUrl = request?.url?.toString() ?: return null
                if (isDirectVideoUrl(reqUrl)) {
                    lastCapturedVideoUrl = reqUrl
                }
            } catch (_: Throwable) {
            }
            return null
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            request?.let {
                return shouldOverrideUrlLoading(it.url)
            }
            return true
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "KotlinRedundantDiagnosticSuppress")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            url?.let {
                return shouldOverrideUrlLoading(Uri.parse(it))
            }
            return true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val cookieManager = CookieManager.getInstance()
            url?.let {
                CookieStore.setCookie(it, cookieManager.getCookie(it))
            }
            view?.title?.let { title ->
                if (title != url && title != view.url && title.isNotBlank()) {
                    binding.titleBar.title = title
                } else {
                    binding.titleBar.title = intent.getStringExtra("title")
                }
                view.evaluateJavascript("!!window._cf_chl_opt") {
                    if (it == "true") {
                        isCloudflareChallenge = true
                    } else if (isCloudflareChallenge && viewModel.sourceVerificationEnable) {
                        viewModel.saveVerificationResult(binding.webView) {
                            finish()
                        }
                    }
                }

                // JS 注入抓取视频直链（video/source/a[href*=.mp4|.m3u8|.webm|.ts]）
                val js = """
                    (function(){
                      function pick(arr){ for(var i=0;i<arr.length;i++){ if(arr[i]) return arr[i]; } return ""; }
                      var vids = Array.from(document.querySelectorAll('video')).map(function(v){return pick([v.currentSrc, v.src]);});
                      var sources = Array.from(document.querySelectorAll('source')).map(function(s){return pick([s.src]);});
                      var anchors = Array.from(document.querySelectorAll('a[href]')).map(function(a){return a.href;});
                      var all = vids.concat(sources).concat(anchors);
                      var re = /(https?:\/\/[^\s"'<>]+\.(mp4|m3u8|webm|ts)(\?[^\s"'<>]*)?)/i;
                      for(var i=0;i<all.length;i++){ var u=all[i]; if(re.test(u)) return u; }
                      return "";
                    })();
                """.trimIndent()

                try {
                    view.evaluateJavascript(js) { result ->
                        val urlStr = result?.trim()?.trim('"') ?: ""
                        if (urlStr.isNotBlank() && isDirectVideoUrl(urlStr)) {
                            lastCapturedVideoUrl = urlStr
                            binding.llView.longSnackbar(getString(R.string.copy_play_url), getString(R.string.confirm)) {
                                // 优先交由系统处理该直链，避免误拦截
                                openUrl(Uri.parse(urlStr))
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        private fun shouldOverrideUrlLoading(url: Uri): Boolean {
            when (url.scheme) {
                "http", "https" -> {
                    // 回归旧逻辑：不拦截，交由 WebView 自行处理
                    return false
                }

                // 部分站点使用 intent:// 打开外部 App（含 browser_fallback_url），统一在 WebView 内处理
                "intent" -> {
                    try {
                        val raw = url.toString()
                        // 使用 Android 标准解析，兼容更多站点的 Intent 格式
                        val target = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)

                        // 若目标应用可处理，则交由系统调起
                        if (target.resolveActivity(packageManager) != null) {
                            startActivity(target)
                            return true
                        }

                        // 尝试读取 browser_fallback_url 回退地址并在 WebView 内加载
                        val fallback = target.getStringExtra("browser_fallback_url")
                        if (!fallback.isNullOrBlank() && (fallback.startsWith("http://") || fallback.startsWith("https://"))) {
                            binding.webView.loadUrl(fallback, viewModel.headerMap)
                            return true
                        }
                    } catch (_: Throwable) {
                        // 忽略解析错误，继续默认处理
                    }
                    // 无法解析回退地址则提示由外部应用处理
                    binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                        openUrl(url)
                    }
                    return true
                }

                // HTML5 媒体常见的内部协议，交由 WebView 自行处理
                "blob", "data" -> {
                    return false
                }

                "legado", "yuedu" -> {
                    startActivity<OnLineImportActivity> {
                        data = url
                    }
                    return true
                }

                else -> {
                    // 若已捕获到潜在视频直链，优先提示打开直链
                    val candidate = lastCapturedVideoUrl
                    if (!candidate.isNullOrBlank() && isDirectVideoUrl(candidate)) {
                        binding.llView.longSnackbar(getString(R.string.copy_play_url), getString(R.string.confirm)) {
                            openUrl(Uri.parse(candidate))
                        }
                    } else {
                        binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                            openUrl(url)
                        }
                    }
                    return true
                }
            }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            handler?.proceed()
        }

    }

    /**
     * 判断是否为可直接交给播放器处理的视频直链
     */
    private fun isDirectVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") ||
            lower.startsWith("tel:") || lower.startsWith("sms:")) return false
        val excludeExt = listOf(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico",
            ".html", ".htm", ".php", ".asp", ".jsp"
        )
        if (excludeExt.any { lower.contains(it) }) return false
        // 更宽泛的识别逻辑：覆盖更多直链与提示关键词
        val videoHints = listOf(
            ".mp4", ".m3u8", ".mpd", ".webm", ".ts",
            "/dash/", "application/dash+xml",
            "application/vnd.apple.mpegurl",
            "mime_type=video", "mime_type=video_mp4",
            "video/mp4", "video/mpeg",
            "play", "stream"
        )
        return videoHints.any { lower.contains(it) }
    }

}
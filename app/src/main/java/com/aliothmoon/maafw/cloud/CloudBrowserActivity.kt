package com.aliothmoon.maafw.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.aliothmoon.maafw.BuildConfig
import com.aliothmoon.maafw.constant.DefaultDisplayConfig
import com.aliothmoon.maafw.privileged.PrivilegedServicePort
import org.koin.core.context.GlobalContext
import timber.log.Timber

/**
 * 虚拟屏上的内嵌云终末地浏览器
 *
 * 门户按「开始游戏」这次点击的 pointerType 选云平台：鼠标 → 电脑版，触摸 → 手机版
 * （见 assets/resource_cloud_browser 的说明）。所以这里不改 DOM，输入由特权进程按模式注入
 * 原生鼠标/触摸事件，页面看到的与真机浏览器一致
 *
 * 由特权进程以 shell 身份启动到虚拟屏（[com.aliothmoon.maafw.remote.MaaRunner]）。虚拟屏的 owner
 * 就是那个进程，只有它有权把 Activity 投上去，所以本页面必须导出；外部应用因此拿到的起页面入口，
 * 由 [CloudBrowserLaunchTicket] 的一次性口令挡掉——口令不对就只开默认门户。
 * 登录 Cookie 留在 WebView 的默认数据目录里，跨轮复用
 */
class CloudBrowserActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var displayManager: DisplayManager
    private var boundDisplayId = Display.DEFAULT_DISPLAY
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) {
            if (displayId != boundDisplayId) return
            // 虚拟屏被销毁后系统会把任务搬回主屏；这里主动收掉，避免浏览器突然盖到用户脸上
            Timber.i("CloudBrowserActivity: display %d removed, finishing", displayId)
            finishAndRemoveTaskQuietly()
        }

        override fun onDisplayChanged(displayId: Int) = Unit
    }

    /**
     * 720p 虚拟屏的 dpi 是 240，默认密度下 1280x720 的窗口只有 853x480 个 CSS 像素，
     * 门户会按平板窄屏排版，和电脑浏览器里量出来的 ROI 对不上。把这张 Activity 的密度钉在 1.0，
     * dp 与物理像素一一对应，WebView 的布局视口即 1280x720
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.densityDpi = DisplayMetrics.DENSITY_DEFAULT
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        boundDisplayId = currentDisplayId()

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        configureWebView(webView)
        root.addView(webView)
        setContentView(root)
        webView.requestFocus()

        loadPortal()
        reportPresence()
    }

    /**
     * singleTask 复用已有实例：新一轮换了云平台（UA 分支不同）或地址时必须重载
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadPortal()
    }

    private fun loadPortal() {
        // 页面必须导出（虚拟屏 owner 是特权进程，只有它投得上来），导出的组件收外部参数等于开门：
        // 口令不对就只走默认门户，外部应用递不进 URL，也递不进 UA 分支
        val trusted = CloudBrowserLaunchTicket.matches(intent.getStringExtra(CloudBrowserContract.EXTRA_TOKEN))
        if (!trusted) {
            Timber.w("CloudBrowserActivity: launch without a matching ticket, falling back to the default portal")
        }
        val mode = intent.getStringExtra(CloudBrowserContract.EXTRA_MODE)
            ?.lowercase()
            ?.takeIf { it == CloudBrowserContract.MODE_PC || it == CloudBrowserContract.MODE_MOBILE }
            ?.takeIf { trusted }
            ?: CloudBrowserContract.MODE_PC
        val url = intent.getStringExtra(CloudBrowserContract.EXTRA_URL)
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.takeIf { trusted }
            ?: CloudBrowserContract.DEFAULT_URL
        webView.settings.userAgentString = if (mode == CloudBrowserContract.MODE_PC) PC_UA else MOBILE_UA
        Timber.i("CloudBrowserActivity: mode=%s display=%d url=%s", mode, boundDisplayId, url)
        webView.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        displayManager.registerDisplayListener(displayListener, null)
        webView.onResume()
        reportPresence()
    }

    override fun onPause() {
        displayManager.unregisterDisplayListener(displayListener)
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        displayManager.unregisterDisplayListener(displayListener)
        reportPresence(DefaultDisplayConfig.DISPLAY_NONE)
        runCatching { CookieManager.getInstance().flush() }
        root.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    /** 被系统挪回主屏、或被 moveRootTaskToDisplay 拉回，都会走一次配置变更 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reportPresence()
    }

    /**
     * 把自己现在所在的屏报给特权进程，[displayId] 为空时取当前屏并刷新 [boundDisplayId]
     *
     * 特权侧查任务的唯一手段是 getRunningTasks，部分 ROM 看不见 excludeFromRecents 的任务，
     * 「屏上没任务」分不清是真没起来还是查不到；这一份自报才是页面起来的凭据
     */
    private fun reportPresence(displayId: Int? = null) {
        val target = displayId ?: currentDisplayId().also { boundDisplayId = it }
        runCatching {
            val port: PrivilegedServicePort = GlobalContext.get().get()
            port.serviceOrNull()?.reportEmbeddedBrowserDisplay(target)
        }.onFailure { Timber.w(it, "CloudBrowserActivity: presence report of display %d skipped", target) }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayId(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display.displayId
        else windowManager.defaultDisplay.displayId

    private fun configureWebView(view: WebView) {
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // 布局视口固定为窗口宽度（1280 CSS px），别让 WebView 按 980px 的移动端默认视口排版
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: android.webkit.WebResourceRequest,
            ): Boolean = false
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(
                custom: View,
                callback: WebChromeClient.CustomViewCallback,
            ) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = custom
                customViewCallback = callback
                webView.visibility = View.GONE
                root.addView(
                    custom,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }

            override fun onHideCustomView() {
                val custom = customView ?: return
                root.removeView(custom)
                webView.visibility = View.VISIBLE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
        }
    }

    private fun finishAndRemoveTaskQuietly() {
        runCatching { finishAndRemoveTask() }.onFailure { finish() }
    }

    private companion object {
        /** 门户对 WebView UA 可能另走分支，PC 模式给桌面 Chrome，移动模式给普通手机 Chrome */
        const val PC_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

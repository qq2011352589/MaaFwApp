package com.aliothmoon.maafw.cloud

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aliothmoon.maafw.R
import timber.log.Timber

/**
 * 主屏上的云终末地登录页
 *
 * 虚拟屏的 IME 是隐藏的（[CloudBrowserActivity] 里只能注入、不能打字），
 * 首次登录/短信验证码需要真实软键盘，所以在主屏开一个普通 WebView。
 * WebView 的 Cookie 与 localStorage 是全应用共享并落盘的，
 * 这里登录一次，虚拟屏上的门户直接就是已登录状态
 */
class CloudBrowserLoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val title = TextView(this).apply {
            text = getString(R.string.cloud_login_title)
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
        }
        val close = Button(this).apply {
            text = getString(R.string.cloud_login_close)
            setOnClickListener { finish() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            addView(
                title,
                LinearLayout.LayoutParams(0, (56 * density).toInt(), 1f),
            )
            addView(
                close,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = (12 * density).toInt() },
            )
        }
        root.addView(
            bar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = MOBILE_UA
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest,
                ): Boolean = false
            }
            webChromeClient = WebChromeClient()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        setContentView(root)

        val url = intent.getStringExtra(CloudBrowserContract.EXTRA_URL)
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: CloudBrowserContract.DEFAULT_URL
        Timber.i("CloudBrowserLoginActivity: url=%s", url)
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        runCatching { CookieManager.getInstance().flush() }
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

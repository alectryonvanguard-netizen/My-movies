package com.mymovie.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * In-app web page (like a policy or "watch" link inside other apps): https only, no hand-off to other apps.
 * The site name stays visible in the top bar on purpose so you always know where you are.
 */
class WebActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var host: TextView
    private lateinit var progress: ProgressBar
    private lateinit var back: Button
    private lateinit var root: LinearLayout
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url")?.takeIf { it.startsWith("https://") } ?: return finish()
        val title = intent.getStringExtra("title").orEmpty()
        WindowCompat.setDecorFitsSystemWindows(window, true)

        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xFF101017.toInt())
        }
        fun barButton(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); minWidth = 0; minimumWidth = 0
            setOnClickListener { onClick() }; bar.addView(this)
        }
        barButton("✕") { finish() }
        back = barButton("‹") { if (web.canGoBack()) web.goBack() }
        host = TextView(this).apply {
            setTextColor(0xFFA3A3B5.toInt()); textSize = 13f; maxLines = 1; setPadding(8, 0, 8, 0)
            text = title
        }
        bar.addView(host, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        barButton("↻") { web.reload() }
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6))

        web = WebView(this)
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.scheme == "https") return false
                Toast.makeText(this@WebActivity, "Blocked a link that tried to leave the app", Toast.LENGTH_SHORT).show()
                return true
            }
            override fun onPageFinished(view: WebView, u: String?) {
                host.text = "🔒 " + (android.net.Uri.parse(u ?: url).host ?: title)
                back.alpha = if (view.canGoBack()) 1f else 0.3f
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view; customCallback = callback
                addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                root.visibility = View.GONE
                WindowInsetsControllerCompat(window, view).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            override fun onHideCustomView() { exitFullscreen() }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> exitFullscreen()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })
        web.loadUrl(url)
    }

    private fun exitFullscreen() {
        val v = customView ?: return
        (v.parent as? ViewGroup)?.removeView(v)
        customView = null
        customCallback?.onCustomViewHidden(); customCallback = null
        root.visibility = View.VISIBLE
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { (web.parent as? ViewGroup)?.removeView(web); web.destroy(); super.onDestroy() }

    companion object {
        /** Opens an https page inside the app. */
        fun open(context: Context, url: String, title: String = "") {
            val secure = url.replaceFirst("http://", "https://")
            context.startActivity(Intent(context, WebActivity::class.java).putExtra("url", secure).putExtra("title", title))
        }
    }
}

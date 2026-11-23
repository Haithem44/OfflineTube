package com.haithem44.offlinetube

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton

class YoutubeTab(
    private val activity: MainActivity,
    private val getFolderPath: () -> String,
    private val onReload: () -> Unit,
    private val onPlay: (VideoItem) -> Unit,
    private val onDownload: (String?) -> Unit
) {
    val view: FrameLayout = FrameLayout(activity)
    private var web: WebView? = null
    private var initialized = false
    private var fab: ImageButton? = null

    private fun isWatchUrl(url: String?): Boolean =
        url != null && (url.contains("/watch") || url.contains("youtu.be/") || url.contains("/shorts/"))

    private fun updateFab(url: String?) {
        fab?.visibility = if (isWatchUrl(url)) View.VISIBLE else View.GONE
    }

    fun isInitialized(): Boolean = initialized

    fun reload() { web?.reload() }

    fun onShown() {
        if (!initialized) {
            initialized = true
            setupWebView()
        }
        web?.onResume()
    }

    fun onHidden() {
        web?.onPause()
    }

    /** تحرير الـ WebView لمنع تسرب الذاكرة. */
    fun onDestroy() {
        web?.let {
            try {
                view.removeView(it)
                it.stopLoading()
                it.destroy()
            } catch (_: Exception) {}
        }
        web = null
    }

    fun handleBack(): Boolean {
        val w = web ?: return false
        if (w.canGoBack()) { w.goBack(); return true }
        return false
    }

    private fun setupWebView() {
        val w = WebView(activity)
        web = w
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.settings.loadWithOverviewMode = true
        w.settings.useWideViewPort = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, newProgress: Int) {}
        }
        w.webViewClient = object : WebViewClient() {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun doUpdateVisitedHistory(v: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(v, url, isReload)
                updateFab(url)
            }
            override fun onPageFinished(v: WebView?, url: String?) {
                super.onPageFinished(v, url)
                updateFab(url)
            }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
                if (url != null && (url.contains("youtube.com/watch") || url.contains("youtu.be/"))) {
                    v?.loadUrl(url)
                    return true
                }
                return false
            }
        }
        w.loadUrl("https://m.youtube.com")
        view.addView(w, FrameLayout.LayoutParams(-1, -1))

        val d = activity.resources.displayMetrics.density
        val b = ImageButton(activity)
        b.setImageDrawable(IconDrawable(IconDrawable.DOWNLOAD, Color.WHITE))
        b.background = GradientDrawable().apply { setColor(Color.parseColor("#FF0000")); shape = GradientDrawable.OVAL }
        b.elevation = 8 * d
        val pad = (15 * d).toInt()
        b.setPadding(pad, pad, pad, pad)
        b.visibility = View.GONE
        b.contentDescription = "تحميل الفيديو"
        b.setOnClickListener { onDownload(w.url) }
        val m = (16 * d).toInt()
        view.addView(b, FrameLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt(), Gravity.BOTTOM or Gravity.END).apply { setMargins(m, m, m, m) })
        fab = b
    }
}

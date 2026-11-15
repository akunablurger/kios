package com.tidecoin.kiosk

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    companion object { private const val TAG = "KiosWebView" }

    private lateinit var webView: WebView
    private val TARGET_HOST = "tidecoinweb.vercel.app"
    private val TARGET_URL = "https://tidecoinweb.vercel.app"
    private val REFRESH_INTERVAL_MS = 30 * 60 * 1000L // 30 menit

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            webView.reload()
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive + keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)

        setupWebView()
        webView.loadUrl(TARGET_URL)

        // Auto refresh setiap 30 menit
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)

        // Coba masuk Lock Task Mode (kiosk)
        try {
            startLockTask()
        } catch (e: Exception) {
            // Tidak apa-apa kalau device belum di-whitelist sebagai device owner
        }
    }

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.setSupportZoom(false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return true
                val host = url.host ?: return true

                // Hanya izinkan domain target + subdomain-nya
                return if (host == TARGET_HOST || host.endsWith(".$TARGET_HOST")) {
                    false // biarkan WebView load
                } else {
                    // Blok navigasi ke situs lain → reload ke halaman utama
                    view?.loadUrl(TARGET_URL)
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.i(TAG, "Page loaded: $url")
                hideSystemUI()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "Main-frame load failed: ${request.url}; code=${error?.errorCode}; description=${error?.description}")
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "Main-frame HTTP error: ${request.url}; status=${errorResponse?.statusCode}; reason=${errorResponse?.reasonPhrase}")
                }
            }
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    // Blok tombol Back agar tidak bisa keluar
    override fun onBackPressed() {
        // Jangan panggil super → tidak bisa keluar
        // Kalau mau, bisa reload saja:
        // webView.loadUrl(TARGET_URL)
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable)
        webView.destroy()
        super.onDestroy()
    }
}

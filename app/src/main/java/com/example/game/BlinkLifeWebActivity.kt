package com.example.game

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream

/**
 * BLINK LIFE runs as sandboxed WebAssembly/WebGL inside BLINK.
 * Unlike a downloaded native APK, a static web pack needs no installation prompt.
 */
class BlinkLifeWebActivity : ComponentActivity() {
    private var webView: WebView? = null
    private val host = "appassets.androidplatform.net"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BlinkLifeWebPack.isInstalled(this)) {
            finish()
            return
        }
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val root = BlinkLifeWebPack.gameDirectory(this)
        val loader = WebViewAssetLoader.Builder()
            .setDomain(host)
            .addPathHandler("/blinklife/", WebViewAssetLoader.PathHandler { path ->
                val normalized = path.removePrefix("/")
                if (normalized.isBlank() || normalized.contains('/') || normalized.contains('\\') ||
                    normalized.startsWith('.')) return@PathHandler null
                val file = File(root, normalized)
                if (!file.isFile || file.canonicalFile.parentFile != root.canonicalFile)
                    return@PathHandler null
                val mime = when (file.extension.lowercase()) {
                    "html" -> "text/html"
                    "js" -> "application/javascript"
                    "wasm" -> "application/wasm"
                    "pck" -> "application/octet-stream"
                    "json" -> "application/json"
                    "svg" -> "image/svg+xml"
                    "png" -> "image/png"
                    "ico" -> "image/x-icon"
                    "webp" -> "image/webp"
                    else -> return@PathHandler null
                }
                WebResourceResponse(mime, if (mime.startsWith("text") || mime.contains("javascript") ||
                    mime.contains("json")) "UTF-8" else null, FileInputStream(file))
            })
            .build()

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    return url.scheme != "https" || url.host != host || !url.path.orEmpty().startsWith("/blinklife/")
                }
            }
            loadUrl("https://$host/blinklife/index.html")
        }
        setContentView(webView)
        // Android gesture navigation does not dispatch deprecated Activity.onBackPressed().
        // Keep WebView history navigation and leave the game when there is no prior page.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) webView?.goBack() else finish()
            }
        })
    }


    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        webView?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
        webView = null
        super.onDestroy()
    }
}

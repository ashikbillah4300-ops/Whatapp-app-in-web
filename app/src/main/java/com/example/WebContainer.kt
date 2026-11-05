package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebContainer(
    modifier: Modifier = Modifier,
    isDesktopMode: Boolean,
    zoomPercent: Int = 110,
    fileChooserLauncher: ActivityResultLauncher<Intent>,
    fileHandler: FileHandler,
    onWebViewCreated: (WebView) -> Unit,
    onLoadingChanged: (Boolean, Int) -> Unit,
    onErrorChanged: (Boolean, String?) -> Unit,
    onTitleChanged: (String?) -> Unit,
    onExternalLinkRequested: (String) -> Unit
) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Setup download listener
            setDownloadListener(DownloadHandler(context))

            // Configure security and functionality settings
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true

                // Strict security measures
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                // Media playback for voice notes
                mediaPlaybackRequiresUserGesture = false

                // Viewport & zoom for responsive mobile display
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                textZoom = zoomPercent

                // Cache policy: standard cache to prevent re-downloading static web assets
                cacheMode = WebSettings.LOAD_DEFAULT

                userAgentString = if (isDesktopMode) {
                    AppSettings.DESKTOP_USER_AGENT
                } else {
                    WebSettings.getDefaultUserAgent(context)
                }
            }

            SessionManager.configureCookieManager(this)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString() ?: return false

                    // If URL is within official allowed domains, let WebView load it
                    if (WebSecurityPolicy.isUrlAllowed(targetUrl)) {
                        return false
                    }

                    // Handle application action schemes (e.g., whatsapp://, tel:, mailto:)
                    if (WebSecurityPolicy.isExternalActionScheme(targetUrl)) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                            return true
                        } catch (_: Exception) {
                            return true
                        }
                    }

                    // Outside domains: route to external browser/app fallback
                    onExternalLinkRequested(targetUrl)
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    onErrorChanged(false, null)
                    onLoadingChanged(true, 10)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onLoadingChanged(false, 100)
                    SessionManager.flushCookies()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val description = error?.description?.toString() ?: "Connection failed"
                        val errorDetail = when (error?.errorCode) {
                            ERROR_HOST_LOOKUP -> "Server unreachable. Please check internet connection."
                            ERROR_TIMEOUT -> "Connection timed out connecting to WhatsApp Web."
                            ERROR_CONNECT -> "Failed to establish connection to WhatsApp server."
                            else -> description
                        }
                        onErrorChanged(true, errorDetail)
                        onLoadingChanged(false, 0)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true) {
                        val code = errorResponse?.statusCode ?: 0
                        if (code >= 400) {
                            onErrorChanged(true, "Server responded with HTTP error $code")
                        }
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: android.net.http.SslError?
                ) {
                    // Security requirement: strictly cancel on SSL verification failures
                    WebSecurityPolicy.handleSslError(handler)
                    onErrorChanged(
                        true,
                        "SSL Certificate validation failed. Connection aborted for security."
                    )
                    onLoadingChanged(false, 0)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    val isLoading = newProgress < 100
                    onLoadingChanged(isLoading, newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    onTitleChanged(title)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    return fileHandler.onShowFileChooser(
                        filePathCallback,
                        fileChooserParams,
                        fileChooserLauncher
                    )
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    // Grant requested web audio capture / video capture if Android permissions allow
                    request?.let {
                        val resources = it.resources
                        it.grant(resources)
                    }
                }
            }
        }
    }

    // Update user agent whenever mode switches
    DisposableEffect(isDesktopMode) {
        webView.settings.userAgentString = if (isDesktopMode) {
            AppSettings.DESKTOP_USER_AGENT
        } else {
            WebSettings.getDefaultUserAgent(context)
        }
        onDispose { }
    }

    // Update zoom level dynamically
    DisposableEffect(zoomPercent) {
        webView.settings.textZoom = zoomPercent
        onDispose { }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            onWebViewCreated(webView)
            webView.loadUrl(WebSecurityPolicy.OFFICIAL_WHATSAPP_WEB_URL)
            webView
        }
    )
}

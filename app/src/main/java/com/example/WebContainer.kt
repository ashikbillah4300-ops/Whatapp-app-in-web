package com.example

import android.annotation.SuppressLint
import android.app.AlertDialog
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

            // Setup download listener and JavaScript blob bridge
            val downloadHandler = DownloadHandler(context)
            setDownloadListener(downloadHandler)
            downloadHandler.attachWebView(this)

            // Long-press image saver for direct photo/media saving
            setOnLongClickListener {
                val hitTest = hitTestResult
                val imageUrl = when (hitTest.type) {
                    WebView.HitTestResult.IMAGE_TYPE,
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> hitTest.extra
                    else -> null
                }

                if (!imageUrl.isNullOrBlank()) {
                    AlertDialog.Builder(context)
                        .setTitle("ছবি ডাউনলোড করবেন?")
                        .setMessage("ছবিটি আপনার ফোনের Downloads ফোল্ডারে সেভ হবে।")
                        .setPositiveButton("ডাউনলোড") { _, _ ->
                            downloadHandler.onDownloadStart(
                                imageUrl,
                                settings.userAgentString,
                                null,
                                "image/jpeg",
                                0
                            )
                        }
                        .setNegativeButton("বাতিল", null)
                        .show()
                    true
                } else {
                    false
                }
            }

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

                    // Enable zoom & hook blob media downloads for photos, videos, and documents
                    val clientScript = """
                        (function() {
                            function enableZoom() {
                                var metas = document.getElementsByTagName('meta');
                                var found = false;
                                for (var i = 0; i < metas.length; i++) {
                                    if (metas[i].name === 'viewport') {
                                        metas[i].content = 'width=device-width, initial-scale=1.0, minimum-scale=0.2, maximum-scale=6.0, user-scalable=yes';
                                        found = true;
                                    }
                                }
                                if (!found) {
                                    var meta = document.createElement('meta');
                                    meta.name = 'viewport';
                                    meta.content = 'width=device-width, initial-scale=1.0, minimum-scale=0.2, maximum-scale=6.0, user-scalable=yes';
                                    if (document.head) document.head.appendChild(meta);
                                }
                                if (document.documentElement) document.documentElement.style.touchAction = 'manipulation';
                                if (document.body) document.body.style.touchAction = 'manipulation';
                            }
                            enableZoom();
                            setInterval(enableZoom, 3000);

                            // Prevent WhatsApp from instantly revoking blob URLs before download completes
                            if (!window._revokeHooked && window.URL && window.URL.revokeObjectURL) {
                                window._revokeHooked = true;
                                var origRevoke = window.URL.revokeObjectURL;
                                window.URL.revokeObjectURL = function(u) {
                                    setTimeout(function() {
                                        try { origRevoke.call(window.URL, u); } catch (_) {}
                                    }, 45000);
                                };
                            }

                            // Intercept programmatic click on download links (used by WhatsApp Web for photo/video downloads)
                            if (!window._anchorClickHooked && window.HTMLAnchorElement) {
                                window._anchorClickHooked = true;
                                var origClick = HTMLAnchorElement.prototype.click;
                                HTMLAnchorElement.prototype.click = function() {
                                    var href = this.href;
                                    if (href && (href.startsWith('blob:') || href.startsWith('data:'))) {
                                        var name = this.getAttribute('download') || this.download || ('WhatsApp_Media_' + Date.now());
                                        fetch(href)
                                            .then(function(res) { return res.blob(); })
                                            .then(function(blob) {
                                                var reader = new FileReader();
                                                reader.onloadend = function() {
                                                    if (window.AndroidBlobDownloader && window.AndroidBlobDownloader.saveBase64) {
                                                        window.AndroidBlobDownloader.saveBase64(reader.result, name, blob.type || '');
                                                    }
                                                };
                                                reader.readAsDataURL(blob);
                                            })
                                            .catch(function(err) {
                                                console.error('Blob click download error:', err);
                                            });
                                    }
                                    return origClick.apply(this, arguments);
                                };
                            }

                            // Intercept blob file download links in DOM
                            if (!window._blobClickInterceptor) {
                                window._blobClickInterceptor = true;
                                document.addEventListener('click', function(e) {
                                    var link = e.target.closest('a');
                                    if (link && link.href && link.href.startsWith('blob:')) {
                                        var name = link.getAttribute('download') || ('WhatsApp_' + Date.now());
                                        fetch(link.href)
                                            .then(function(res) { return res.blob(); })
                                            .then(function(blob) {
                                                var reader = new FileReader();
                                                reader.onloadend = function() {
                                                    if (window.AndroidBlobDownloader && window.AndroidBlobDownloader.saveBase64) {
                                                        window.AndroidBlobDownloader.saveBase64(reader.result, name, blob.type || '');
                                                    }
                                                };
                                                reader.readAsDataURL(blob);
                                            })
                                            .catch(function(err) {
                                                console.error('Blob click download error:', err);
                                            });
                                    }
                                }, true);
                            }
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(clientScript, null)
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

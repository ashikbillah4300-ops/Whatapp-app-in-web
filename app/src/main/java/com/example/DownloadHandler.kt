package com.example

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.lang.ref.WeakReference

/**
 * Handles file, document, image, and media downloads from WhatsApp Web.
 * Supports:
 * - blob: URLs (used by WhatsApp Web for decrypted photos, videos, voice notes, PDFs, docs)
 * - data: URLs (base64 inline media)
 * - standard http/https downloads via DownloadManager
 * Directs files into Android's public Downloads directory and triggers system notifications.
 */
class DownloadHandler(
    private val context: Context,
    private val onDownloadStarted: ((String) -> Unit)? = null
) : DownloadListener {

    private var webViewWeakRef: WeakReference<WebView>? = null

    fun attachWebView(webView: WebView) {
        webViewWeakRef = WeakReference(webView)
        webView.addJavascriptInterface(BlobDownloadBridge(), "AndroidBlobDownloader")
    }

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long
    ) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "Invalid download URL", Toast.LENGTH_SHORT).show()
            return
        }

        var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        if (fileName.isNullOrBlank() || fileName == "downloadfile" || fileName.startsWith(".")) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimetype) ?: "bin"
            fileName = "WhatsApp_${System.currentTimeMillis()}.$ext"
        }

        // WhatsApp Web uses blob: URLs for media and file downloads
        if (url.startsWith("blob:")) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Downloading: $fileName", Toast.LENGTH_SHORT).show()
            }

            val sanitizedUrl = url.replace("'", "\\'")
            val sanitizedName = fileName.replace("'", "\\'")
            val safeMime = (mimetype ?: "").replace("'", "\\'")

            val js = """
                (function() {
                    try {
                        var xhr = new XMLHttpRequest();
                        xhr.open('GET', '$sanitizedUrl', true);
                        xhr.responseType = 'blob';
                        xhr.onload = function() {
                            if (this.status === 200) {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    if (window.AndroidBlobDownloader && window.AndroidBlobDownloader.saveBase64) {
                                        window.AndroidBlobDownloader.saveBase64(reader.result, '$sanitizedName', '$safeMime');
                                    }
                                };
                                reader.readAsDataURL(this.response);
                            } else {
                                if (window.AndroidBlobDownloader && window.AndroidBlobDownloader.onDownloadError) {
                                    window.AndroidBlobDownloader.onDownloadError('HTTP ' + this.status);
                                }
                            }
                        };
                        xhr.onerror = function() {
                            if (window.AndroidBlobDownloader && window.AndroidBlobDownloader.onDownloadError) {
                                window.AndroidBlobDownloader.onDownloadError('Network error reading blob');
                            }
                        };
                        xhr.send();
                    } catch (err) {
                        if (window.AndroidBlobDownloader && window.AndroidBlobDownloader.onDownloadError) {
                            window.AndroidBlobDownloader.onDownloadError(err.message || 'Error');
                        }
                    }
                })();
            """.trimIndent()

            webViewWeakRef?.get()?.evaluateJavascript(js, null)
            return
        }

        // Handle inline data URLs
        if (url.startsWith("data:")) {
            saveBase64DataToDownloads(url, fileName, mimetype ?: "application/octet-stream")
            return
        }

        // Validate security: only allow https downloads
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            Toast.makeText(context, "Unrecognized download protocol", Toast.LENGTH_SHORT).show()
            return
        }

        if (uri.scheme?.lowercase() != "https") {
            Toast.makeText(context, "Insecure HTTP downloads are blocked", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val request = DownloadManager.Request(uri).apply {
                setTitle(safeFileName)
                setDescription("Downloading file from WhatsApp Web...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFileName)

                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                if (!mimetype.isNullOrBlank()) {
                    setMimeType(mimetype)
                }
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager != null) {
                downloadManager.enqueue(request)
                Toast.makeText(context, "Download started: $safeFileName", Toast.LENGTH_SHORT).show()
                onDownloadStarted?.invoke(safeFileName)
            } else {
                Toast.makeText(context, "System Download Manager is unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    inner class BlobDownloadBridge {
        @JavascriptInterface
        fun saveBase64(dataUri: String, fileName: String, mimeType: String) {
            saveBase64DataToDownloads(dataUri, fileName, mimeType)
        }

        @JavascriptInterface
        fun onDownloadError(error: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Download error: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBase64DataToDownloads(dataString: String, rawFileName: String, rawMime: String) {
        Thread {
            try {
                var mimeType = rawMime
                var base64Content = dataString

                if (dataString.contains(",")) {
                    val parts = dataString.split(",", limit = 2)
                    val header = parts[0]
                    base64Content = parts[1]
                    if (header.contains(":") && header.contains(";")) {
                        val extractedMime = header.substringAfter(":").substringBefore(";")
                        if (extractedMime.isNotBlank()) {
                            mimeType = extractedMime
                        }
                    }
                }

                if (mimeType.isBlank() || mimeType == "application/octet-stream") {
                    mimeType = "application/octet-stream"
                }

                var fileName = rawFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                if (!fileName.contains(".") || fileName.endsWith(".")) {
                    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
                    fileName = if (fileName.endsWith(".")) "$fileName$ext" else "$fileName.$ext"
                }

                val fileBytes = Base64.decode(base64Content, Base64.DEFAULT)

                val savedUri: Uri?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { os ->
                            os.write(fileBytes)
                        }
                    }
                    savedUri = uri
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val targetFile = File(downloadsDir, fileName)
                    targetFile.outputStream().use { it.write(fileBytes) }
                    MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
                    savedUri = Uri.fromFile(targetFile)
                }

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                    onDownloadStarted?.invoke(fileName)
                }

                savedUri?.let { uri ->
                    showDownloadNotification(fileName, mimeType, uri)
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Download failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showDownloadNotification(fileName: String, mimeType: String, fileUri: Uri) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "whatsapp_web_downloads"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Downloads",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "WhatsApp Web downloads"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle("Download Complete")
                .setContentText(fileName)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: Exception) {
        }
    }
}

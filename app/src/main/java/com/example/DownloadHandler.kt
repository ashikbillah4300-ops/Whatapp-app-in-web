package com.example

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.widget.Toast

/**
 * Handles file and media downloads from WhatsApp Web using Android's system DownloadManager.
 */
class DownloadHandler(
    private val context: Context,
    private val onDownloadStarted: ((String) -> Unit)? = null
) : DownloadListener {

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

        // Handle blob: or data: URIs specially or notify
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            Toast.makeText(
                context,
                "Web media opened. Tap and hold to save media.",
                Toast.LENGTH_SHORT
            ).show()
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
            // Determine filename and sanitize against directory traversal
            var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            fileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            if (fileName.isBlank() || fileName.startsWith(".")) {
                fileName = "whatsapp_download_${System.currentTimeMillis()}"
            }

            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Downloading file from WhatsApp Web...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                // Attach cookies and user agent so authentication is preserved during download
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

            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager != null) {
                downloadManager.enqueue(request)
                val msg = "Download started: $fileName"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onDownloadStarted?.invoke(fileName)
            } else {
                Toast.makeText(context, "System Download Manager is unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Storage permission required to download", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }
}

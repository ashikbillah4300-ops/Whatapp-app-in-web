package com.example

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.ActivityResultLauncher

/**
 * Handles HTML file input (<input type="file">) requests from WhatsApp Web.
 * Safely invokes the Android system document / file picker and ensures the
 * ValueCallback is always resolved even if canceled by the user.
 */
class FileHandler {

    private var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null

    /**
     * Intercepts file chooser request from WebChromeClient.
     */
    fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?,
        launcher: ActivityResultLauncher<Intent>
    ): Boolean {
        // Cancel any previous pending callback to prevent WebView thread lockup
        cancelPendingCallback()

        pendingFilePathCallback = filePathCallback

        return try {
            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }

            // Ensure multiple selection is enabled if requested by the webpage
            if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }

            val chooser = Intent.createChooser(intent, "Select File to Attach")
            launcher.launch(chooser)
            true
        } catch (e: Exception) {
            cancelPendingCallback()
            false
        }
    }

    /**
     * Processes the result returned from the ActivityResultLauncher.
     */
    fun handleResult(resultCode: Int, data: Intent?) {
        val callback = pendingFilePathCallback ?: return

        var results: Array<Uri>? = null

        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            val dataUri = data.data
            val clipData = data.clipData

            results = when {
                clipData != null -> {
                    Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                }
                dataUri != null -> {
                    arrayOf(dataUri)
                }
                else -> {
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                }
            }
        }

        try {
            callback.onReceiveValue(results)
        } catch (_: Exception) {
        } finally {
            pendingFilePathCallback = null
        }
    }

    /**
     * Safely cancels pending callback, sending null to notify WebView that selection was dismissed.
     */
    fun cancelPendingCallback() {
        try {
            pendingFilePathCallback?.onReceiveValue(null)
        } catch (_: Exception) {
        } finally {
            pendingFilePathCallback = null
        }
    }
}

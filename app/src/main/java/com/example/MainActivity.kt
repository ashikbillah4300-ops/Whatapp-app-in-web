package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var appSettings: AppSettings
    private lateinit var networkMonitor: NetworkMonitor
    private val fileHandler = FileHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appSettings = AppSettings(this)
        networkMonitor = NetworkMonitor(this)
        networkMonitor.startMonitoring()

        if (appSettings.isKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContent {
            MyApplicationTheme {
                WhatsAppWebApp(
                    appSettings = appSettings,
                    networkMonitor = networkMonitor,
                    fileHandler = fileHandler,
                    onOpenBrowser = { url -> openInBrowser(this, url) },
                    onOpenWhatsAppApp = { openOfficialWhatsApp(this) },
                    onFinishActivity = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.stopMonitoring()
    }

    companion object {
        fun openInBrowser(context: Context, url: String) {
            try {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(context, Uri.parse(url))
            } catch (_: Exception) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No web browser application found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun openOfficialWhatsApp(context: Context) {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.whatsapp")
                ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
            if (intent != null) {
                context.startActivity(intent)
            } else {
                try {
                    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp"))
                    marketIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(marketIntent)
                } catch (_: Exception) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp"))
                    webIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(webIntent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppWebApp(
    appSettings: AppSettings,
    networkMonitor: NetworkMonitor,
    fileHandler: FileHandler,
    onOpenBrowser: (String) -> Unit,
    onOpenWhatsAppApp: () -> Unit,
    onFinishActivity: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableIntStateOf(0) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isDesktopMode by remember { mutableStateOf(appSettings.isDesktopMode) }
    var currentZoom by remember { mutableIntStateOf(appSettings.zoomPercent) }

    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showZoomDialog by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Camera capture state
    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var showCameraSheet by remember { mutableStateOf(false) }

    // Launcher for file uploads requested by WebView
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        fileHandler.handleResult(result.resultCode, result.data)
    }

    // Camera picture capture launcher
    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedPhoto = bitmap
            showCameraSheet = true
        }
    }

    // Permissions launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Auto-reload when network reconnects if page failed previously
    LaunchedEffect(isOnline) {
        if (isOnline && hasError && appSettings.isAutoReloadOnConnect) {
            hasError = false
            errorMessage = null
            webViewRef?.reload()
        }
    }

    // Back button handling
    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
            webViewRef?.clearMatches()
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onFinishActivity()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (isSearchActive) {
                // In-page search bar
                TopAppBar(
                    modifier = Modifier.testTag("search_top_bar"),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                searchQuery = ""
                                webViewRef?.clearMatches()
                                keyboardController?.hide()
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Search",
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                if (query.isNotBlank()) {
                                    webViewRef?.findAllAsync(query)
                                } else {
                                    webViewRef?.clearMatches()
                                }
                            },
                            placeholder = {
                                Text(
                                    stringResource(R.string.search_hint),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                                webViewRef?.findNext(true)
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                webViewRef?.clearMatches()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = EmeraldPrimary,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            } else {
                // Standard Native WhatsApp Top App Bar
                TopAppBar(
                    modifier = Modifier.testTag("app_top_bar"),
                    title = {
                        Text(
                            text = "Sunflower",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    actions = {
                        // Camera Shortcut Button
                        IconButton(
                            onClick = {
                                takePicturePreviewLauncher.launch(null)
                            },
                            modifier = Modifier.testTag("camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera",
                                tint = Color.White
                            )
                        }

                        // Search Button
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag("search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }

                        // Reload Button
                        IconButton(
                            onClick = {
                                hasError = false
                                errorMessage = null
                                webViewRef?.reload()
                            },
                            modifier = Modifier.testTag("reload_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload WhatsApp Web",
                                tint = Color.White
                            )
                        }

                        // Overflow Menu Button
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (isDesktopMode) "Switch to Mobile View" else "Switch to Desktop View")
                                },
                                onClick = {
                                    showMenu = false
                                    val newMode = !isDesktopMode
                                    isDesktopMode = newMode
                                    appSettings.isDesktopMode = newMode
                                    webViewRef?.reload()
                                    Toast.makeText(
                                        context,
                                        if (newMode) "Desktop View (Recommended for QR Pairing)" else "Mobile View Active",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isDesktopMode) Icons.Default.PhoneAndroid else Icons.Default.Computer,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.testTag("desktop_toggle_menu_item")
                            )

                            DropdownMenuItem(
                                text = { Text("Display & Text Scale ($currentZoom%)") },
                                onClick = {
                                    showMenu = false
                                    showZoomDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FormatSize, contentDescription = null)
                                },
                                modifier = Modifier.testTag("zoom_menu_item")
                            )

                            DropdownMenuItem(
                                text = { Text("Open in External Browser") },
                                onClick = {
                                    showMenu = false
                                    onOpenBrowser(WebSecurityPolicy.OFFICIAL_WHATSAPP_WEB_URL)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                                },
                                modifier = Modifier.testTag("open_browser_menu_item")
                            )

                            DropdownMenuItem(
                                text = { Text("Open Official WhatsApp App") },
                                onClick = {
                                    showMenu = false
                                    onOpenWhatsAppApp()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                                },
                                modifier = Modifier.testTag("open_whatsapp_app_menu_item")
                            )

                            DropdownMenuItem(
                                text = { Text("Reset Web Session (Logout)") },
                                onClick = {
                                    showMenu = false
                                    showResetDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                },
                                modifier = Modifier.testTag("reset_session_menu_item")
                            )

                            DropdownMenuItem(
                                text = { Text("About & Privacy") },
                                onClick = {
                                    showMenu = false
                                    showAboutDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                },
                                modifier = Modifier.testTag("about_privacy_menu_item")
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = EmeraldPrimary,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Web Content full screen
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                WebContainer(
                    isDesktopMode = isDesktopMode,
                    zoomPercent = currentZoom,
                    fileChooserLauncher = fileChooserLauncher,
                    fileHandler = fileHandler,
                    onWebViewCreated = { webViewRef = it },
                    onLoadingChanged = { loading, progress ->
                        isLoading = loading
                        loadingProgress = progress
                    },
                    onErrorChanged = { error, message ->
                        hasError = error
                        errorMessage = message
                    },
                    onTitleChanged = { },
                    onExternalLinkRequested = { url ->
                        onOpenBrowser(url)
                    }
                )

                // Top progress bar during page loading
                AnimatedVisibility(
                    visible = isLoading && loadingProgress in 1..99,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    LinearProgressIndicator(
                        progress = { loadingProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .testTag("loading_progress_bar"),
                        color = MintGreen,
                        trackColor = Color.Transparent
                    )
                }

                // Offline warning strip
                if (!isOnline) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .align(Alignment.TopCenter)
                            .testTag("offline_warning_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Device is offline. Please check your Wi-Fi or mobile data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Connection Error Fallback Screen
                if (hasError) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .testTag("error_fallback_screen"),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .widthIn(max = 500.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Error Icon",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Unable to Load WhatsApp Web",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = errorMessage ?: "Failed to establish a secure connection to the WhatsApp Web service.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        hasError = false
                                        errorMessage = null
                                        webViewRef?.reload()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("retry_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry Connection")
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = { onOpenBrowser(WebSecurityPolicy.OFFICIAL_WHATSAPP_WEB_URL) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("open_browser_button")
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open in External Browser")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TextButton(
                                    onClick = onOpenWhatsAppApp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("open_whatsapp_app_button")
                                ) {
                                    Text("Launch Official WhatsApp App")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Display Scale / Text Zoom Dialog
    if (showZoomDialog) {
        AlertDialog(
            onDismissRequest = { showZoomDialog = false },
            title = {
                Text(
                    text = "Display & Text Scale",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Adjust page scaling for optimal comfort on your phone screen:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val zoomOptions = listOf(
                        100 to "100% (Standard)",
                        110 to "110% (Default)",
                        125 to "125% (Large)",
                        140 to "140% (Extra Large)"
                    )

                    zoomOptions.forEach { (scale, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    currentZoom = scale
                                    appSettings.zoomPercent = scale
                                    showZoomDialog = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentZoom == scale,
                                onClick = {
                                    currentZoom = scale
                                    appSettings.zoomPercent = scale
                                    showZoomDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZoomDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Camera Preview Bottom Sheet
    if (showCameraSheet) {
        CameraPreviewBottomSheet(
            bitmap = capturedPhoto,
            onDismissRequest = { showCameraSheet = false },
            onShare = {
                showCameraSheet = false
                val photo = capturedPhoto
                if (photo != null) {
                    try {
                        val cacheFile = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(cacheFile).use { out ->
                            photo.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        val photoUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            cacheFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, photoUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share photo"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Photo ready for sharing", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Reset Session Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset Web Session?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("This will remove all stored session cookies, cache, and local storage from this app, signing you out of WhatsApp Web. You will need to scan the QR code again to reconnect.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        SessionManager.clearSession(context, webViewRef) {
                            webViewRef?.loadUrl(WebSecurityPolicy.OFFICIAL_WHATSAPP_WEB_URL)
                            Toast.makeText(context, "Session cleared successfully", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text("Reset & Logout")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.testTag("cancel_reset_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // About & Privacy Dialog
    if (showAboutDialog) {
        AboutPrivacyDialog(
            onDismissRequest = { showAboutDialog = false }
        )
    }
}

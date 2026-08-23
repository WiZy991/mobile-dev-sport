package com.fitnessclub.app.ui.screens.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnessclub.app.BuildConfig

/**
 * Сбер ID внутри WebView этого APK.
 * Custom Tabs / системный браузер после OAuth открывают deep link — и при двух flavor
 * на телефоне Android часто отдаёт callback в «Академию борьбы».
 * Здесь перехватываем HTTPS callback и кастомные схемы, не отдавая Intent системе.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SberAuthWebDialog(
    authorizeUrl: String,
    onCallback: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Вход через Сбер ID") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF21A038),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )
            },
        ) { padding ->
            val client = remember(authorizeUrl) {
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val uri = request.url ?: return false
                        if (isSberAuthCallback(uri)) {
                            onCallback(uri)
                            return true
                        }
                        return false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        val uri = Uri.parse(url)
                        if (isSberAuthCallback(uri)) {
                            onCallback(uri)
                            return true
                        }
                        return false
                    }
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = client
                        loadUrl(authorizeUrl)
                    }
                },
                update = { webView ->
                    webView.webViewClient = client
                },
            )
        }
    }
}

internal fun isSberAuthCallback(uri: Uri): Boolean {
    val hasAuthResult = !uri.getQueryParameter("code").isNullOrBlank() ||
        !uri.getQueryParameter("error").isNullOrBlank()
    if (!hasAuthResult) return false

    // HTTPS redirect Сбера на наш API (до 302 на deep link).
    val path = uri.path.orEmpty()
    if ((uri.scheme == "https" || uri.scheme == "http") &&
        path.contains("/auth/sber/callback")
    ) {
        return true
    }

    // Deep link любого flavor / legacy — обрабатываем внутри этого APK, не через систему.
    if (uri.host == "auth" && (uri.path == "/callback" || uri.path.isNullOrEmpty())) {
        return true
    }
    if (uri.toString().startsWith(BuildConfig.APP_AUTH_BRIDGE_URI)) {
        return true
    }
    return false
}

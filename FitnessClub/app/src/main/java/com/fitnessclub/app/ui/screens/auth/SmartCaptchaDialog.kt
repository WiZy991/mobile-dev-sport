package com.fitnessclub.app.ui.screens.auth

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitnessclub.app.BuildConfig

/**
 * Подключение как в ЛК Яндекса → «Подключить» → HTML:
 * captcha.js + div.smart-captcha[data-sitekey].
 *
 * PHP из ЛК — только проверка token на сервере.
 * Origin = worldcashfit.ru (проверка домена в ЛК).
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCaptchaDialog(
    siteKey: String,
    onToken: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember {
        SmartCaptchaJsBridge { token ->
            mainHandler.post { onToken(token) }
        }
    }

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
                    title = { Text("Проверка") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFD35400),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(AndroidColor.WHITE)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            addJavascriptInterface(bridge, "NativeClient")
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        loading = false
                                        loadError = "Не удалось загрузить капчу. Проверьте интернет."
                                    }
                                }
                            }
                            // baseURL = хост из ЛК (worldcashfit.ru), иначе ключ отклонят.
                            val base = BuildConfig.SITE_URL.trimEnd('/') + "/"
                            loadDataWithBaseURL(
                                base,
                                yandexConnectHtml(siteKey),
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                )
                if (loading && loadError == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                loadError?.let { msg ->
                    Text(
                        text = msg,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        color = Color(0xFFB00020),
                    )
                }
            }
        }
    }
}

class SmartCaptchaJsBridge(
    private val onToken: (String) -> Unit,
) {
    @JavascriptInterface
    fun onGetToken(token: String?) {
        val t = token?.trim().orEmpty()
        if (t.isNotEmpty()) onToken(t)
    }

    @JavascriptInterface
    fun onChallengeVisible() = Unit

    @JavascriptInterface
    fun onChallengeHidden() = Unit
}

/**
 * Ровно разметка из ЛК «Подключить» → HTML + webview для приложения.
 * Токен уходит в NativeClient.onGetToken → SMS OTP.
 */
private fun yandexConnectHtml(siteKey: String): String {
    val jsKey = siteKey
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("<", "\\u003c")
        .replace("\n", "")
        .replace("\r", "")
    val attrKey = siteKey
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
    return """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
          <meta charset="UTF-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
          <style>
            html,body{margin:0;padding:16px;background:#fff}
            #captcha-container{min-height:100px}
          </style>
          <script>
            window.onSmartCaptchaSuccess = function(token) {
              if (window.NativeClient && token) {
                window.NativeClient.onGetToken(token);
              }
            };
            function onSmartCaptchaReady() {
              if (!window.smartCaptcha) return;
              var id = window.smartCaptcha.render('captcha-container', {
                sitekey: "$jsKey",
                hl: 'ru',
                webview: true,
                callback: window.onSmartCaptchaSuccess
              });
              window.smartCaptcha.subscribe(id, 'success', window.onSmartCaptchaSuccess);
            }
          </script>
          <script src="https://smartcaptcha.yandexcloud.net/captcha.js?render=onload&onload=onSmartCaptchaReady" defer></script>
        </head>
        <body>
          <div
            style="height:100px"
            id="captcha-container"
            class="smart-captcha"
            data-sitekey="$attrKey"
          ></div>
        </body>
        </html>
    """.trimIndent()
}

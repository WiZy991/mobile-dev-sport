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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
@Composable
fun SmartCaptchaDialog(
    siteKey: String,
    onToken: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val widgetReady = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember {
        SmartCaptchaJsBridge(
            onToken = { token -> mainHandler.post { onToken(token) } },
            onReady = {
                mainHandler.post {
                    widgetReady.set(true)
                    loading = false
                    loadError = null
                }
            },
            onFailed = {
                mainHandler.post {
                    loading = false
                    loadError = VPN_CAPTCHA_ERROR
                }
            },
        )
    }
    DisposableEffect(bridge) {
        val timeout = Runnable {
            if (!widgetReady.get() && loadError == null) {
                loading = false
                loadError = VPN_CAPTCHA_ERROR
            }
        }
        mainHandler.postDelayed(timeout, 12_000)
        onDispose { mainHandler.removeCallbacks(timeout) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // Узкое системное окно обрезало слайдер справа.
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Проверка",
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp),
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(460.dp),
                        factory = { context ->
                            WebView(context).apply {
                                setBackgroundColor(AndroidColor.WHITE)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                // Обзорный масштаб сжимает виджет и обрезает слайдер.
                                settings.loadWithOverviewMode = false
                                settings.useWideViewPort = true
                                settings.setSupportZoom(false)
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
                                        val url = request?.url?.toString().orEmpty()
                                        val captchaScript = url.contains("captcha.js")
                                        if (request?.isForMainFrame == true || captchaScript) {
                                            loading = false
                                            loadError = VPN_CAPTCHA_ERROR
                                        }
                                    }
                                }
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
                                .padding(horizontal = 16.dp),
                            color = Color(0xFFB00020),
                        )
                    }
                }
            }
        }
    }
}

private const val VPN_CAPTCHA_ERROR = "Капча не запустилась. Выключите VPN."

class SmartCaptchaJsBridge(
    private val onToken: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onFailed: () -> Unit,
) {
    @JavascriptInterface
    fun onGetToken(token: String?) {
        val t = token?.trim().orEmpty()
        if (t.isNotEmpty()) onToken(t)
    }

    @JavascriptInterface
    fun onWidgetReady() = onReady()

    @JavascriptInterface
    fun onLoadFailed() = onFailed()

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
            html,body{margin:0;padding:8px 8px 0;background:#fff}
            #captcha-container{width:100%;min-height:100px}
          </style>
          <script>
            window.onSmartCaptchaSuccess = function(token) {
              if (window.NativeClient && token) {
                window.NativeClient.onGetToken(token);
              }
            };
            function onSmartCaptchaReady() {
              if (!window.smartCaptcha) {
                if (window.NativeClient) window.NativeClient.onLoadFailed();
                return;
              }
              var id = window.smartCaptcha.render('captcha-container', {
                sitekey: "$jsKey",
                hl: 'ru',
                webview: true,
                callback: window.onSmartCaptchaSuccess
              });
              if (window.NativeClient) window.NativeClient.onWidgetReady();
            }
            (function loadCaptcha(index) {
              var urls = [
                'https://smartcaptcha.yandexcloud.net/captcha.js?render=onload&onload=onSmartCaptchaReady',
                'https://smartcaptcha.cloud.yandex.ru/captcha.js?render=onload&onload=onSmartCaptchaReady'
              ];
              if (index >= urls.length) {
                if (window.NativeClient) window.NativeClient.onLoadFailed();
                return;
              }
              var s = document.createElement('script');
              s.src = urls[index];
              s.async = true;
              s.onerror = function() { loadCaptcha(index + 1); };
              document.head.appendChild(s);
            })(0);
          </script>
        </head>
        <body>
          <div
            id="captcha-container"
            class="smart-captcha"
            data-sitekey="$attrKey"
          ></div>
        </body>
        </html>
    """.trimIndent()
}

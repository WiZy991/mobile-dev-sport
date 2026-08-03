package com.fitnessclub.app.ui.screens.club

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.fitnessclub.app.ui.theme.Primary
import kotlin.math.abs

/** Приблизительные координаты АТЦ «Новый Де-Фриз», ул. Купера 2. */
private const val DE_FRIES_LAT = 43.313906
private const val DE_FRIES_LON = 131.999418

private const val MOSCOW_DEFAULT_LAT = 55.7558
private const val MOSCOW_DEFAULT_LON = 37.6173

/**
 * Интерактивная Яндекс.Карта (WebView + map-widget) с точкой клуба
 * и кнопками маршрута (приложение или браузер).
 */
@Composable
fun ClubMapPreview(
    latitude: Double,
    longitude: Double,
    address: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val (lat, lon) = remember(latitude, longitude, address) {
        resolveClubCoords(latitude, longitude, address)
    }
    val mapUrl = remember(lat, lon) {
        // Полноэкранный виджет Яндекса — зум/скролл работают, точка на месте
        "https://yandex.ru/map-widget/v1/" +
            "?ll=$lon,$lat" +
            "&z=16" +
            "&pt=$lon,$lat,pm2rdm" +
            "&l=map"
    }
    var mapLoading by remember(mapUrl) { mutableStateOf(true) }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color(0xFFE8EEF4)),
        ) {
            YandexMapWebView(
                url = mapUrl,
                onLoadingChanged = { mapLoading = it },
                modifier = Modifier.fillMaxSize(),
            )
            if (mapLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE8EEF4)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
            Surface(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = address.ifBlank { "Клуб на карте" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F2937),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { openRoute(context, lat, lon, address) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Маршрут",
                    maxLines = 1,
                    softWrap = false,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { openInYandex(context, lat, lon, address) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Яндекс", maxLines = 1, softWrap = false)
                }
                FilledTonalButton(
                    onClick = { openInDgis(context, lat, lon, address) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("2ГИС", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun YandexMapWebView(
    url: String,
    onLoadingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                // Жесты зума/пана карты — не отдаём скроллу LazyColumn
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN,
                        android.view.MotionEvent.ACTION_MOVE,
                        -> v.parent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL,
                        -> v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        onLoadingChanged(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadingChanged(false)
                    }
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                tag = url
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.tag != url) {
                webView.tag = url
                onLoadingChanged(true)
                webView.loadUrl(url)
            }
        },
    )
}

/**
 * CRM часто отдаёт дефолт Москвы (55.75, 37.61), хотя адрес — Владивосток.
 * Игнорируем такой плейсхолдер и ставим точку по адресу.
 */
internal fun resolveClubCoords(
    latitude: Double,
    longitude: Double,
    address: String,
): Pair<Double, Double> {
    val a = address.lowercase()
    val looksVladivostok =
        "владивосток" in a ||
            "де фриз" in a ||
            "де-фриз" in a ||
            "купера" in a ||
            "надеждин" in a

    val isMoscowPlaceholder =
        abs(latitude - MOSCOW_DEFAULT_LAT) < 0.05 &&
            abs(longitude - MOSCOW_DEFAULT_LON) < 0.05

    val hasRealCoords =
        (abs(latitude) > 0.01 || abs(longitude) > 0.01) && !isMoscowPlaceholder

    return when {
        hasRealCoords && !looksVladivostok -> latitude to longitude
        hasRealCoords && looksVladivostok && !isMoscowPlaceholder -> {
            // Координаты из API, но если они явно в Европейской части при владивостокском адресе — правим
            if (longitude < 100.0) DE_FRIES_LAT to DE_FRIES_LON else latitude to longitude
        }
        looksVladivostok -> DE_FRIES_LAT to DE_FRIES_LON
        hasRealCoords -> latitude to longitude
        else -> MOSCOW_DEFAULT_LAT to MOSCOW_DEFAULT_LON
    }
}

private fun openRoute(context: Context, lat: Double, lon: Double, address: String) {
    // Сначала пробуем приложения; если нет — браузер Яндекса (без «No apps…»)
    val candidates = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~$lat,$lon&rtt=auto"))
            .setPackage("ru.yandex.yandexmaps"),
        Intent(Intent.ACTION_VIEW, Uri.parse("dgis://2gis.ru/routeSearch/to/$lon,$lat/go"))
            .setPackage("ru.dublgis.dgismobile"),
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://yandex.ru/maps/?rtext=~$lat,$lon&rtt=auto"),
        ),
    )
    startFirstAvailable(context, candidates)
}

private fun openInYandex(context: Context, lat: Double, lon: Double, address: String) {
    startFirstAvailable(
        context,
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("yandexmaps://maps.yandex.ru/?pt=$lon,$lat&z=16&l=map"))
                .setPackage("ru.yandex.yandexmaps"),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://yandex.ru/maps/?pt=$lon,$lat&z=16&l=map"),
            ),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://yandex.ru/maps/?text=${Uri.encode(address)}"),
            ),
        ),
    )
}

private fun openInDgis(context: Context, lat: Double, lon: Double, address: String) {
    startFirstAvailable(
        context,
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("dgis://2gis.ru/geo/$lon,$lat"))
                .setPackage("ru.dublgis.dgismobile"),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://2gis.ru/geo/$lon,$lat"),
            ),
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://2gis.ru/search/${Uri.encode(address)}"),
            ),
        ),
    )
}

private fun startFirstAvailable(context: Context, intents: List<Intent>) {
    for (intent in intents) {
        try {
            val can = intent.resolveActivity(context.packageManager) != null
            if (!can && intent.`package` != null) continue
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // next
        } catch (_: Exception) {
            // next
        }
    }
}

package com.fitnessclub.app.ui.screens.club

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.fitnessclub.app.ui.theme.Primary
import kotlin.math.abs

/** АТЦ «Новый Де-Фриз», ул. Купера 2. */
private const val DE_FRIES_LAT = 43.313906
private const val DE_FRIES_LON = 131.999418

private const val MOSCOW_DEFAULT_LAT = 55.7558
private const val MOSCOW_DEFAULT_LON = 37.6173

/**
 * Интерактивная карта (Leaflet) с точкой клуба.
 * «Маршрут» строит путь от геолокации пользователя до клуба прямо на карте.
 * Кнопки Яндекс / 2ГИС открывают навигацию во внешнем приложении/браузере.
 *
 * Почему не map-widget Яндекса: в Android WebView он часто отдаёт пустой белый экран
 * (блокировки / cookie / UA). Leaflet+OSM стабильно работает без API-ключа.
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
    val mapHtml = remember(lat, lon, address) {
        buildInteractiveMapHtml(lat, lon, address)
    }

    var mapLoading by remember { mutableStateOf(true) }
    var routing by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }

    fun buildRouteFrom(userLat: Double, userLon: Double) {
        routing = true
        statusText = "Строим маршрут…"
        webViewRef?.evaluateJavascript(
            "window.buildRoute($userLat, $userLon, $lat, $lon);",
            null,
        )
    }

    fun startInAppRoute() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            statusText = "Нужен доступ к геолокации"
            return
        }
        val loc = readBestLocation(context)
        if (loc == null) {
            statusText = "Не удалось определить ваше местоположение. Включите GPS."
            Toast.makeText(context, "Включите геолокацию и попробуйте снова", Toast.LENGTH_SHORT).show()
            return
        }
        buildRouteFrom(loc.latitude, loc.longitude)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            startInAppRoute()
        } else {
            statusText = "Без геолокации маршрут на карте недоступен"
            Toast.makeText(context, "Разрешите геолокацию для маршрута", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp))
                .background(Color(0xFFE8EEF4)),
        ) {
            ClubLeafletWebView(
                html = mapHtml,
                onReady = { webViewRef = it; mapLoading = false },
                onRouteResult = { ok, message ->
                    routing = false
                    statusText = message
                    if (!ok) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
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

        statusText?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                        startInAppRoute()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    }
                },
                enabled = !routing && !mapLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                if (routing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Строим…", maxLines = 1, softWrap = false, fontSize = 16.sp)
                } else {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Маршрут на карте",
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
private fun ClubLeafletWebView(
    html: String,
    onReady: (WebView) -> Unit,
    onRouteResult: (ok: Boolean, message: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val routeCallback = remember { mutableStateOf(onRouteResult) }
    routeCallback.value = onRouteResult

    val bridge = remember {
        object {
            @JavascriptInterface
            fun onRouteOk(distanceKm: String, durationMin: String) {
                mainHandler.post {
                    routeCallback.value(true, "Маршрут: ~$distanceKm км, ~$durationMin мин")
                }
            }

            @JavascriptInterface
            fun onRouteError(message: String) {
                mainHandler.post {
                    routeCallback.value(false, message.ifBlank { "Не удалось построить маршрут" })
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.setSupportZoom(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                addJavascriptInterface(bridge, "AndroidBridge")
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
                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (view != null) {
                            mainHandler.post { onReady(view) }
                        }
                    }
                }
                setBackgroundColor(android.graphics.Color.rgb(232, 238, 244))
                tag = html.hashCode()
                loadDataWithBaseURL(
                    "https://unpkg.com/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            val key = html.hashCode()
            if (webView.tag != key) {
                webView.tag = key
                webView.loadDataWithBaseURL(
                    "https://unpkg.com/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

private fun buildInteractiveMapHtml(lat: Double, lon: Double, address: String): String {
    val safeTitle = address
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .ifBlank { "Клуб" }
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html, body, #map { margin:0; padding:0; width:100%; height:100%; background:#e8eef4; }
  .leaflet-control-attribution { font-size:9px; }
</style>
</head>
<body>
<div id="map"></div>
<script>
  var clubLat = $lat, clubLon = $lon;
  var map = L.map('map', { zoomControl: true, attributionControl: true })
    .setView([clubLat, clubLon], 15);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap'
  }).addTo(map);
  var clubMarker = L.marker([clubLat, clubLon]).addTo(map)
    .bindPopup('$safeTitle');
  var userMarker = null;
  var routeLine = null;

  window.buildRoute = function(fromLat, fromLon, toLat, toLon) {
    try {
      if (routeLine) { map.removeLayer(routeLine); routeLine = null; }
      if (userMarker) { map.removeLayer(userMarker); userMarker = null; }
      userMarker = L.circleMarker([fromLat, fromLon], {
        radius: 8, color: '#2563EB', fillColor: '#3B82F6', fillOpacity: 1, weight: 2
      }).addTo(map).bindPopup('Вы здесь');

      var url = 'https://router.project-osrm.org/route/v1/driving/'
        + fromLon + ',' + fromLat + ';' + toLon + ',' + toLat
        + '?overview=full&geometries=geojson';
      fetch(url).then(function(r) { return r.json(); }).then(function(data) {
        if (!data || data.code !== 'Ok' || !data.routes || !data.routes.length) {
          if (window.AndroidBridge) AndroidBridge.onRouteError('Маршрут не найден');
          return;
        }
        var route = data.routes[0];
        var coords = route.geometry.coordinates.map(function(c) { return [c[1], c[0]]; });
        routeLine = L.polyline(coords, { color: '#F97316', weight: 5, opacity: 0.9 }).addTo(map);
        map.fitBounds(routeLine.getBounds(), { padding: [28, 28] });
        var km = (route.distance / 1000).toFixed(1);
        var min = Math.max(1, Math.round(route.duration / 60));
        if (window.AndroidBridge) AndroidBridge.onRouteOk(String(km), String(min));
      }).catch(function(e) {
        if (window.AndroidBridge) AndroidBridge.onRouteError('Нет сети для построения маршрута');
      });
    } catch (e) {
      if (window.AndroidBridge) AndroidBridge.onRouteError('Ошибка карты');
    }
  };

  setTimeout(function() { map.invalidateSize(true); }, 200);
  setTimeout(function() { map.invalidateSize(true); }, 600);
</script>
</body>
</html>
    """.trimIndent()
}

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
        looksVladivostok && (!hasRealCoords || longitude < 100.0) -> DE_FRIES_LAT to DE_FRIES_LON
        hasRealCoords -> latitude to longitude
        else -> MOSCOW_DEFAULT_LAT to MOSCOW_DEFAULT_LON
    }
}

@SuppressLint("MissingPermission")
private fun readBestLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
    var best: Location? = null
    for (p in providers) {
        try {
            if (!lm.isProviderEnabled(p) && p != LocationManager.PASSIVE_PROVIDER) continue
            val loc = lm.getLastKnownLocation(p) ?: continue
            if (best == null || (loc.accuracy > 0 && loc.accuracy < best.accuracy) ||
                loc.time > best.time
            ) {
                best = loc
            }
        } catch (_: Exception) {
            // ignore provider
        }
    }
    return best
}

private fun openInYandex(context: Context, lat: Double, lon: Double, address: String) {
    startFirstAvailable(
        context,
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~$lat,$lon&rtt=auto"))
                .setPackage("ru.yandex.yandexmaps"),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?rtext=~$lat,$lon&rtt=auto")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/maps/?text=${Uri.encode(address)}")),
        ),
    )
}

private fun openInDgis(context: Context, lat: Double, lon: Double, address: String) {
    startFirstAvailable(
        context,
        listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("dgis://2gis.ru/routeSearch/to/$lon,$lat/go"))
                .setPackage("ru.dublgis.dgismobile"),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://2gis.ru/routeSearch/to/$lon,$lat/go")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://2gis.ru/search/${Uri.encode(address)}")),
        ),
    )
}

private fun startFirstAvailable(context: Context, intents: List<Intent>) {
    for (intent in intents) {
        try {
            if (intent.`package` != null && intent.resolveActivity(context.packageManager) == null) {
                continue
            }
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // next
        } catch (_: Exception) {
            // next
        }
    }
    Toast.makeText(context, "Не удалось открыть карты", Toast.LENGTH_SHORT).show()
}

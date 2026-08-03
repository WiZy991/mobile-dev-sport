package com.fitnessclub.app.ui.screens.club

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fitnessclub.app.ui.theme.Primary
import kotlin.math.abs

/**
 * Превью точки клуба (Yandex Map Widget в WebView) + кнопки маршрута
 * в Яндекс.Карты / 2ГИС / системный geo:.
 */
@Composable
fun ClubMapPreview(
    latitude: Double,
    longitude: Double,
    address: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasCoords = abs(latitude) > 0.01 || abs(longitude) > 0.01
    val mapHtml = remember(latitude, longitude, address, hasCoords) {
        buildClubMapHtml(latitude, longitude, address, hasCoords)
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp))
                .background(Primary.copy(alpha = 0.12f)),
        ) {
            ClubMapWebView(html = mapHtml, modifier = Modifier.fillMaxSize())
            Surface(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.94f),
                shadowElevation = 4.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (address.isNotBlank()) address else "Клуб на карте",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F2937),
                        maxLines = 2,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    openRouteChooser(context, latitude, longitude, address, hasCoords)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Маршрут")
            }
            FilledTonalButton(
                onClick = {
                    openExternalMaps(context, latitude, longitude, address, hasCoords, prefer = MapApp.YANDEX)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Яндекс")
            }
            FilledTonalButton(
                onClick = {
                    openExternalMaps(context, latitude, longitude, address, hasCoords, prefer = MapApp.DGIS)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("2ГИС")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ClubMapWebView(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://yandex.ru",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        },
    )
}

private fun buildClubMapHtml(
    lat: Double,
    lon: Double,
    address: String,
    hasCoords: Boolean,
): String {
    return if (hasCoords) {
        """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
        <style>
          html,body{margin:0;padding:0;height:100%;overflow:hidden;background:#FFE8D6;}
          iframe{border:0;width:100%;height:100%;display:block;}
        </style></head><body>
        <iframe src="https://yandex.ru/map-widget/v1/?ll=$lon,$lat&z=16&pt=$lon,$lat,pm2rdm&l=map"
          allowfullscreen loading="lazy"></iframe>
        </body></html>
        """.trimIndent()
    } else {
        val q = Uri.encode(address.ifBlank { "Владивосток" })
        """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
        <style>
          html,body{margin:0;padding:0;height:100%;overflow:hidden;background:#FFE8D6;}
          iframe{border:0;width:100%;height:100%;display:block;}
        </style></head><body>
        <iframe src="https://yandex.ru/map-widget/v1/?text=$q&z=15&l=map"
          allowfullscreen loading="lazy"></iframe>
        </body></html>
        """.trimIndent()
    }
}

private enum class MapApp { YANDEX, DGIS }

private fun openRouteChooser(
    context: Context,
    lat: Double,
    lon: Double,
    address: String,
    hasCoords: Boolean,
) {
    val intents = buildList {
        addAll(yandexRouteIntents(lat, lon, address, hasCoords))
        addAll(dgisRouteIntents(lat, lon, address, hasCoords))
        add(geoIntent(lat, lon, address, hasCoords))
    }
    val primary = intents.firstOrNull() ?: return
    val chooser = Intent.createChooser(primary, "Проложить маршрут").apply {
        if (intents.size > 1) {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray())
        }
    }
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        openBrowserFallback(context, lat, lon, address, hasCoords)
    }
}

private fun openExternalMaps(
    context: Context,
    lat: Double,
    lon: Double,
    address: String,
    hasCoords: Boolean,
    prefer: MapApp,
) {
    val intents = when (prefer) {
        MapApp.YANDEX -> yandexRouteIntents(lat, lon, address, hasCoords)
        MapApp.DGIS -> dgisRouteIntents(lat, lon, address, hasCoords)
    }
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // try next / web
        }
    }
    when (prefer) {
        MapApp.YANDEX -> openYandexWeb(context, lat, lon, address, hasCoords)
        MapApp.DGIS -> openDgisWeb(context, lat, lon, address, hasCoords)
    }
}

private fun yandexRouteIntents(
    lat: Double,
    lon: Double,
    address: String,
    hasCoords: Boolean,
): List<Intent> {
    val uri = if (hasCoords) {
        Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~$lat,$lon&rtt=auto")
    } else {
        Uri.parse("yandexmaps://maps.yandex.ru/?text=${Uri.encode(address)}")
    }
    return listOf(
        Intent(Intent.ACTION_VIEW, uri).setPackage("ru.yandex.yandexmaps"),
        Intent(Intent.ACTION_VIEW, uri),
    )
}

private fun dgisRouteIntents(
    lat: Double,
    lon: Double,
    address: String,
    hasCoords: Boolean,
): List<Intent> {
    val uri = if (hasCoords) {
        Uri.parse("dgis://2gis.ru/routeSearch/to/$lon,$lat/go")
    } else {
        Uri.parse("dgis://2gis.ru/search/${Uri.encode(address)}")
    }
    return listOf(
        Intent(Intent.ACTION_VIEW, uri).setPackage("ru.dublgis.dgismobile"),
        Intent(Intent.ACTION_VIEW, uri),
    )
}

private fun geoIntent(
    lat: Double,
    lon: Double,
    address: String,
    hasCoords: Boolean,
): Intent {
    val uri = if (hasCoords) {
        Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(address.ifBlank { "Клуб" })})")
    } else {
        Uri.parse("geo:0,0?q=${Uri.encode(address)}")
    }
    return Intent(Intent.ACTION_VIEW, uri)
}

private fun openYandexWeb(
    context: Context,
    lat: Double,
    lon: Double,
    address: String,
    hasCoords: Boolean,
) {
    val url = if (hasCoords) {
        "https://yandex.ru/maps/?rtext=~$lat,$lon&rtt=auto"
    } else {
        "https://yandex.ru/maps/?text=${Uri.encode(address)}"
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun openDgisWeb(
    context: Context,
    lat: Double,
    lon: Double,
    address: String,
    hasCoords: Boolean,
) {
    val url = if (hasCoords) {
        "https://2gis.ru/routeSearch/to/$lon,$lat/go"
    } else {
        "https://2gis.ru/search/${Uri.encode(address)}"
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun openBrowserFallback(
    context: Context,
    lat: Double,
    lon: Double,
    address: String,
    hasCoords: Boolean,
) {
    openYandexWeb(context, lat, lon, address, hasCoords)
}

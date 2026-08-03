package com.fitnessclub.app.ui.screens.club

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.fitnessclub.app.ui.theme.Primary
import kotlin.math.abs

/**
 * Превью точки клуба (статичная карта Яндекс / OSM) + кнопки маршрута.
 * WebView+iframe часто даёт пустой экран — поэтому картинка через Coil.
 */
@Composable
fun ClubMapPreview(
    latitude: Double,
    longitude: Double,
    address: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val (lat, lon, hasCoords) = remember(latitude, longitude, address) {
        resolveClubCoords(latitude, longitude, address)
    }
    val mapWidthPx = with(density) { 720.dp.roundToPx().coerceIn(300, 1280) }
    val mapHeightPx = with(density) { 400.dp.roundToPx().coerceIn(200, 720) }
    val primaryMapUrl = remember(lat, lon, mapWidthPx, mapHeightPx) {
        // Legacy Static API 1.x — без ключа, надёжно для превью
        "https://static-maps.yandex.ru/1.x/" +
            "?lang=ru_RU" +
            "&ll=$lon,$lat" +
            "&size=${mapWidthPx.coerceAtMost(650)},${mapHeightPx.coerceAtMost(450)}" +
            "&z=16" +
            "&l=map" +
            "&pt=$lon,$lat,pm2rdm"
    }
    val fallbackMapUrl = remember(lat, lon, mapWidthPx, mapHeightPx) {
        // OSM static fallback
        "https://staticmap.openstreetmap.de/staticmap.php" +
            "?center=$lat,$lon" +
            "&zoom=16" +
            "&size=${mapWidthPx.coerceAtMost(800)}x${mapHeightPx.coerceAtMost(600)}" +
            "&maptype=mapnik" +
            "&markers=$lat,$lon,red-pushpin"
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0xFFE8EEF4)),
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(primaryMapUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Карта клуба",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(fallbackMapUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Карта клуба",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(42.dp),
                        )
                    }
                },
            )

            // Мягкий градиент снизу под подписью
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
                        ),
                    ),
            )

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
                onClick = {
                    openRouteChooser(context, lat, lon, address, hasCoords = true)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Проложить маршрут",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        openExternalMaps(context, lat, lon, address, hasCoords = true, prefer = MapApp.YANDEX)
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Яндекс", maxLines = 1, softWrap = false)
                }
                FilledTonalButton(
                    onClick = {
                        openExternalMaps(context, lat, lon, address, hasCoords = true, prefer = MapApp.DGIS)
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("2ГИС", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * Если в CRM нет координат — мягкий fallback по городу/адресу,
 * чтобы превью не было пустым.
 */
private fun resolveClubCoords(
    latitude: Double,
    longitude: Double,
    address: String,
): Triple<Double, Double, Boolean> {
    if (abs(latitude) > 0.01 || abs(longitude) > 0.01) {
        return Triple(latitude, longitude, true)
    }
    val a = address.lowercase()
    return when {
        "владивосток" in a || "де фриз" in a || "купера" in a ->
            Triple(43.1286, 131.9238, false) // ТЦ «Новый де Фриз» (приблизительно)
        else -> Triple(55.7558, 37.6173, false)
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
        openYandexWeb(context, lat, lon, address, hasCoords)
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

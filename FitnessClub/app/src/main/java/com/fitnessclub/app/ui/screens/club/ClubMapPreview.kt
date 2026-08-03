package com.fitnessclub.app.ui.screens.club

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fitnessclub.app.R
import com.fitnessclub.app.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

private const val MAP_VIEWPORT_HEIGHT_DP = 300

/** АТЦ «Новый Де-Фриз», ул. Купера 2. */
private const val DE_FRIES_LAT = 43.313906
private const val DE_FRIES_LON = 131.999418

private const val MOSCOW_DEFAULT_LAT = 55.7558
private const val MOSCOW_DEFAULT_LON = 37.6173

/** Маршрут дальше этого — почти наверняка чужой город / дефолт эмулятора. */
private const val MAX_ROUTE_KM = 250.0

/** CartoCDN — стабильнее OSM.org (часто 403 в WebView/эмуляторе). */
private val cartoTiles: OnlineTileSourceBase = XYTileSource(
    "CartoVoyager",
    1,
    19,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
    ),
)

/**
 * Нативная карта (osmdroid) с точкой клуба.
 * «Маршрут на карте» — линия от вашей геолокации до клуба.
 * Яндекс / 2ГИС — внешняя навигация.
 */
@Composable
fun ClubMapPreview(
    latitude: Double,
    longitude: Double,
    address: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (lat, lon) = remember(latitude, longitude, address) {
        resolveClubCoords(latitude, longitude, address)
    }
    val clubPoint = remember(lat, lon) { GeoPoint(lat, lon) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var routing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var routeOverlay by remember { mutableStateOf<Polyline?>(null) }
    var userMarker by remember { mutableStateOf<Marker?>(null) }

    fun clearRoute(map: MapView) {
        routeOverlay?.let { map.overlays.remove(it) }
        userMarker?.let { map.overlays.remove(it) }
        routeOverlay = null
        userMarker = null
        map.invalidate()
    }

    fun drawRoute(map: MapView, from: GeoPoint, points: List<GeoPoint>, km: Double, minutes: Int) {
        clearRoute(map)
        val you = Marker(map).apply {
            position = from
            title = "Вы здесь"
            icon = ContextCompat.getDrawable(map.context, R.drawable.ic_map_pin_user)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        val line = Polyline().apply {
            outlinePaint.color = AndroidColor.parseColor("#F97316")
            outlinePaint.strokeWidth = 12f
            setPoints(points)
        }
        map.overlays.add(line)
        map.overlays.add(you)
        routeOverlay = line
        userMarker = you
        val box = BoundingBox.fromGeoPoints(ArrayList(points + listOf(clubPoint, from)))
        map.zoomToBoundingBox(box, true, 80)
        map.invalidate()
        statusText = "Маршрут: ~${"%.1f".format(km)} км, ~$minutes мин"
    }

    fun fetchAndDraw(from: GeoPoint) {
        val map = mapViewRef ?: return
        val distKm = from.distanceToAsDouble(clubPoint) / 1000.0
        if (distKm > MAX_ROUTE_KM) {
            routing = false
            statusText =
                "Геолокация далеко от клуба (~${distKm.toInt()} км). " +
                    "На эмуляторе задайте точку у Владивостока (Extended controls → Location)."
            Toast.makeText(
                context,
                "GPS не у клуба (сейчас ~${distKm.toInt()} км). Задайте локацию рядом с Де-Фриз.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        routing = true
        statusText = "Строим маршрут…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                fetchOsrmRoute(from.longitude, from.latitude, lon, lat)
            }
            routing = false
            result.fold(
                onSuccess = { data ->
                    drawRoute(map, from, data.points, data.distanceKm, data.durationMin)
                },
                onFailure = { e ->
                    statusText = e.message ?: "Не удалось построить маршрут"
                    Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    fun startInAppRoute() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            statusText = "Нужен доступ к геолокации"
            return
        }
        routing = true
        statusText = "Определяем ваше местоположение…"
        requestFreshLocation(context) { loc ->
            if (loc == null) {
                routing = false
                statusText = "Не удалось определить местоположение. Включите GPS."
                Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                return@requestFreshLocation
            }
            fetchAndDraw(GeoPoint(loc.latitude, loc.longitude))
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) startInAppRoute()
        else {
            statusText = "Без геолокации маршрут недоступен"
            Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
        }
    }

    // Жесты на карте не отдаём LazyColumn (иначе тянется вся страница вместо карты)
    val mapNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = available
        }
    }

    Column(modifier.fillMaxWidth()) {
        // Жёсткий viewport: MapView при зуме/маршруте иначе раздувает item на весь экран
        Box(
            Modifier
                .fillMaxWidth()
                .height(MAP_VIEWPORT_HEIGHT_DP.dp)
                .clip(RectangleShape)
                .nestedScroll(mapNestedScroll)
                .background(Color(0xFFE8EEF4)),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MAP_VIEWPORT_HEIGHT_DP.dp)
                    .clip(RectangleShape),
                factory = { ctx ->
                    val map = object : MapView(ctx) {
                        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                            // osmdroid при зуме запрашивает огромный размер — игнорируем
                            val w = MeasureSpec.getSize(widthMeasureSpec)
                            val h = MeasureSpec.getSize(heightMeasureSpec)
                            val exactW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
                            val exactH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                            super.onMeasure(exactW, exactH)
                            setMeasuredDimension(w, h)
                        }

                        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                            when (ev.actionMasked) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_POINTER_DOWN,
                                MotionEvent.ACTION_MOVE,
                                -> parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL,
                                -> parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            return super.dispatchTouchEvent(ev)
                        }
                    }.apply {
                        setTileSource(cartoTiles)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        isClickable = true
                        isFocusable = true
                        controller.setZoom(15.5)
                        controller.setCenter(clubPoint)
                        overlays.add(
                            Marker(this).apply {
                                position = clubPoint
                                title = address.ifBlank { "Клуб" }
                                icon = ContextCompat.getDrawable(ctx, R.drawable.ic_map_pin_yandex)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            },
                        )
                        onResume()
                    }
                    mapViewRef = map
                    FrameLayout(ctx).apply {
                        clipChildren = true
                        clipToPadding = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        addView(
                            map,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
                },
                update = { frame ->
                    val map = frame.getChildAt(0) as? MapView
                    if (map != null && mapViewRef !== map) mapViewRef = map
                },
            )
            DisposableEffect(Unit) {
                mapViewRef?.onResume()
                onDispose {
                    mapViewRef?.onPause()
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
                enabled = !routing,
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

private data class OsrmRoute(
    val points: List<GeoPoint>,
    val distanceKm: Double,
    val durationMin: Int,
)

private fun fetchOsrmRoute(
    fromLon: Double,
    fromLat: Double,
    toLon: Double,
    toLat: Double,
): Result<OsrmRoute> = runCatching {
    val url =
        "https://router.project-osrm.org/route/v1/driving/" +
            "$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=geojson"
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 12_000
        requestMethod = "GET"
        setRequestProperty("User-Agent", "FitnessClub/1.0")
    }
    try {
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            .bufferedReader().use { it.readText() }
        if (code !in 200..299) error("Сервис маршрутов недоступен ($code)")
        val json = JSONObject(body)
        if (json.optString("code") != "Ok") error("Маршрут не найден")
        val route = json.getJSONArray("routes").getJSONObject(0)
        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = buildList {
            for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                add(GeoPoint(c.getDouble(1), c.getDouble(0)))
            }
        }
        if (points.isEmpty()) error("Пустой маршрут")
        OsrmRoute(
            points = points,
            distanceKm = route.getDouble("distance") / 1000.0,
            durationMin = maxOf(1, (route.getDouble("duration") / 60.0).toInt()),
        )
    } finally {
        conn.disconnect()
    }
}

@SuppressLint("MissingPermission")
private fun requestFreshLocation(context: Context, onResult: (Location?) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (lm == null) {
        onResult(null)
        return
    }
    val last = readBestLocation(context)
    // Если lastKnown уже рядом с клубом — берём сразу
    if (last != null) {
        val club = Location("club").apply {
            latitude = DE_FRIES_LAT
            longitude = DE_FRIES_LON
        }
        if (last.distanceTo(club) / 1000f <= MAX_ROUTE_KM) {
            onResult(last)
            return
        }
    }

    val main = Handler(Looper.getMainLooper())
    var done = false
    fun finish(loc: Location?) {
        if (done) return
        done = true
        main.post { onResult(loc) }
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            try {
                lm.removeUpdates(this)
            } catch (_: Exception) {
            }
            finish(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    try {
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            finish(last) // может быть Москва — дальше отфильтруем по дистанции
            return
        }
        lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        // Таймаут 8 сек
        main.postDelayed({
            try {
                lm.removeUpdates(listener)
            } catch (_: Exception) {
            }
            finish(last)
        }, 8_000)
    } catch (_: Exception) {
        finish(last)
    }
}

@SuppressLint("MissingPermission")
private fun readBestLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    var best: Location? = null
    for (p in listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )) {
        try {
            val loc = lm.getLastKnownLocation(p) ?: continue
            if (best == null || loc.time > best.time) best = loc
        } catch (_: Exception) {
        }
    }
    return best
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
        } catch (_: Exception) {
        }
    }
    Toast.makeText(context, "Не удалось открыть карты", Toast.LENGTH_SHORT).show()
}

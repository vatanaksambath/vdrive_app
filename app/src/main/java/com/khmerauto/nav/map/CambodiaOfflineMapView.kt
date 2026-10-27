package com.khmerauto.nav.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.khmerauto.nav.core.calendar.SolarCalculator
import com.khmerauto.nav.core.database.entity.PlaceCategory
import com.khmerauto.nav.core.database.entity.PlaceEntity
import com.khmerauto.nav.map.routing.ActiveNavigationState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.OnSymbolClickListener
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File

private const val TAG = "CambodiaOfflineMapView"
private const val ROUTE_SOURCE_ID = "kmautonav-route-source"
private const val ROUTE_LAYER_ID = "kmautonav-route-layer"
private const val VEHICLE_PUCK_ICON_ID = "kmautonav-vehicle-puck"
private const val CHARGER_ICON_ID = "kmautonav-charger-icon"
private const val REPAIR_ICON_ID = "kmautonav-repair-icon"
private const val LANDMARK_ICON_ID = "kmautonav-landmark-icon"
private const val SAVED_ICON_ID = "kmautonav-saved-icon"

/**
 * 100% Offline MapLibre Engine Component for Cambodia.
 *
 * Enforces:
 * 1. Offline MBTiles / PMTiles reading with online fallback.
 * 2. Day & Night appearance auto-switching via SolarCalculator.
 * 3. Deep-Sleep Compliance: Stops GL rendering surface immediately on vehicle sleep.
 * 4. Custom layers: Oriented 3D vehicle position puck, EV chargers, and navigation route line.
 */
@Composable
fun CambodiaOfflineMapView(
    vehicleLat: Double,
    vehicleLng: Double,
    vehicleHeading: Float = 0f,
    vehicleSpeedKmh: Float = 0f,
    places: List<PlaceEntity> = emptyList(),
    navigationState: ActiveNavigationState = ActiveNavigationState(),
    onPlaceSelected: ((PlaceEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isDaytime by remember { derivedStateOf { SolarCalculator.isDayAppearance() } }

    // Initialize MapLibre Native singleton
    remember {
        MapLibre.getInstance(context)
    }

    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManagerInstance by remember { mutableStateOf<SymbolManager?>(null) }
    var vehicleSymbolInstance by remember { mutableStateOf<Symbol?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    val placesMap = remember { mutableMapOf<String, PlaceEntity>() }

    // Check if offline MBTiles file exists on head unit storage
    val offlineMbtilesFile = remember { File("/sdcard/kmautonav/maps/cambodia.mbtiles") }
    val styleUri = remember(isDaytime, offlineMbtilesFile.exists()) {
        if (offlineMbtilesFile.exists()) {
            resolveOfflineMbtilesStyle(context, isDaytime, offlineMbtilesFile)
        } else {
            if (isDaytime) "asset://style_day.json" else "asset://style_night.json"
        }
    }

    LaunchedEffect(offlineMbtilesFile.exists()) {
        if (offlineMbtilesFile.exists()) {
            Log.i(TAG, "Discovered offline vector tiles package: ${offlineMbtilesFile.absolutePath} (${offlineMbtilesFile.length() / (1024 * 1024)} MB)")
        } else {
            Log.d(TAG, "Offline MBTiles not found at /sdcard/kmautonav/maps/cambodia.mbtiles. Active with style raster fallback.")
        }
    }

    // Camera follow vehicle position
    LaunchedEffect(vehicleLat, vehicleLng, vehicleHeading) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        val target = LatLng(vehicleLat, vehicleLng)

        // Keep 3D perspective during driving
        val tilt = if (navigationState.isNavigating || vehicleSpeedKmh > 5f) 45.0 else 0.0
        val zoom = if (navigationState.isNavigating) 16.5 else 15.0

        val cameraPosition = CameraPosition.Builder()
            .target(target)
            .zoom(zoom)
            .tilt(tilt)
            .bearing(vehicleHeading.toDouble())
            .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 300)
    }

    // Update Dedicated Oriented Vehicle Puck
    LaunchedEffect(vehicleLat, vehicleLng, vehicleHeading, symbolManagerInstance) {
        val sm = symbolManagerInstance ?: return@LaunchedEffect
        val currentPuck = vehicleSymbolInstance
        if (currentPuck == null) {
            val options = SymbolOptions()
                .withLatLng(LatLng(vehicleLat, vehicleLng))
                .withIconImage(VEHICLE_PUCK_ICON_ID)
                .withIconRotate(vehicleHeading)
                .withIconSize(1.3f)
                .withZIndex(100)
                .withIconIgnorePlacement(true)
                .withIconAllowOverlap(true)
            vehicleSymbolInstance = sm.create(options)
        } else {
            currentPuck.latLng = LatLng(vehicleLat, vehicleLng)
            currentPuck.iconRotate = vehicleHeading
            sm.update(currentPuck)
        }
    }

    // Update Route Line Layer
    LaunchedEffect(navigationState.routePolyline) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        updateRouteOnMap(style, navigationState.routePolyline)
    }

    // Update Places & EV Charger Symbols
    LaunchedEffect(places, symbolManagerInstance) {
        val sm = symbolManagerInstance ?: return@LaunchedEffect
        // Remove existing place symbols (retaining vehicle puck)
        placesMap.keys.forEach { symbolId ->
            sm.annotations.get(symbolId.toLongOrNull() ?: -1L)?.let { sm.delete(it) }
        }
        placesMap.clear()

        places.forEach { place ->
            val iconId = when (place.category) {
                PlaceCategory.EV_CHARGER -> CHARGER_ICON_ID
                PlaceCategory.REPAIR_SHOP -> REPAIR_ICON_ID
                PlaceCategory.SAVED_HOME, PlaceCategory.SAVED_WORK, PlaceCategory.FAVORITE -> SAVED_ICON_ID
                else -> LANDMARK_ICON_ID
            }

            val symbolOptions = SymbolOptions()
                .withLatLng(LatLng(place.latitude, place.longitude))
                .withIconImage(iconId)
                .withIconSize(if (place.isEvCharger) 1.2f else 0.9f)
                .withTextField(place.nameKm)
                .withTextSize(11f)
                .withTextOffset(arrayOf(0f, 1.8f))
                .withTextColor(if (isDaytime) "#0F172A" else "#F0F4F8")
                .withTextHaloColor(if (isDaytime) "#FFFFFF" else "#0B111E")
                .withTextHaloWidth(2f)

            val symbol = sm.create(symbolOptions)
            placesMap[symbol.id.toString()] = place
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    mapViewInstance = this
                    onCreate(null)
                    onStart()
                    onResume()
                    getMapAsync { map ->
                        mapLibreMapInstance = map

                        map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                            // Register custom bitmap icons
                            registerMapIcons(ctx, style)

                            // Initialize Symbol Manager
                            val symbolManager = SymbolManager(this, map, style).apply {
                                iconAllowOverlap = true
                                textAllowOverlap = false
                                addClickListener(OnSymbolClickListener { symbol ->
                                    val place = placesMap[symbol.id.toString()]
                                    if (place != null) {
                                        onPlaceSelected?.invoke(place)
                                        true
                                    } else false
                                })
                            }
                            symbolManagerInstance = symbolManager

                            // Initial camera centered on Phnom Penh
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(vehicleLat, vehicleLng))
                                .zoom(15.0)
                                .tilt(40.0)
                                .build()
                        }
                    }
                }
            },
            update = { mapView ->
                // Reload style if Day/Night mode changed
                mapLibreMapInstance?.let { map ->
                    if (map.style?.uri != styleUri) {
                        map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                            registerMapIcons(context, style)
                            vehicleSymbolInstance = null
                            symbolManagerInstance = SymbolManager(mapView, map, style)
                            updateRouteOnMap(style, navigationState.routePolyline)
                        }
                    }
                }
            }
        )
    }

    // Lifecycle Observer: Ensures zero 12V drain on deep sleep (Guardrail 3)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapViewInstance?.onStart()
                Lifecycle.Event.ON_RESUME -> mapViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "Pausing MapLibre GL surface thread for deep-sleep compliance.")
                    mapViewInstance?.onPause()
                }
                Lifecycle.Event.ON_STOP -> mapViewInstance?.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    mapViewInstance?.onDestroy()
                    mapLibreMapInstance = null
                    symbolManagerInstance = null
                    vehicleSymbolInstance = null
                    mapViewInstance = null
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

private fun updateRouteOnMap(style: Style, routePoints: List<Pair<Double, Double>>) {
    if (!style.isFullyLoaded) return
    try {
        val coordinates = routePoints.map { Point.fromLngLat(it.second, it.first) }
        val feature = if (coordinates.size >= 2) {
            Feature.fromGeometry(LineString.fromLngLats(coordinates))
        } else {
            FeatureCollection.fromFeatures(emptyList())
        }

        var source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
        if (source == null) {
            source = if (feature is Feature) GeoJsonSource(ROUTE_SOURCE_ID, feature)
            else GeoJsonSource(ROUTE_SOURCE_ID)
            style.addSource(source)

            val lineLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).apply {
                setProperties(
                    lineColor("#00E5FF"), // Geely Cyan
                    lineWidth(6.5f),
                    lineCap(LINE_CAP_ROUND),
                    lineJoin(LINE_JOIN_ROUND),
                    lineOpacity(0.92f)
                )
            }
            style.addLayer(lineLayer)
        } else {
            if (feature is Feature) {
                source.setGeoJson(feature)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "updateRouteOnMap note: ${e.message}")
    }
}

private fun registerMapIcons(context: Context, style: Style) {
    // 1. Vehicle Position Puck Icon (Cyan Chevron)
    val puckBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#00E5FF")
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(24f, 4f)
            lineTo(44f, 44f)
            lineTo(24f, 34f)
            lineTo(4f, 44f)
            close()
        }
        canvas.drawPath(path, paint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawPath(path, borderPaint)
    }
    style.addImage(VEHICLE_PUCK_ICON_ID, puckBitmap)

    // 2. EV Charging Station Pin Icon (Emerald Green with Lightning Bolt)
    val chargerBitmap = Bitmap.createBitmap(52, 52, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#00E676") // Emerald Green
            style = Paint.Style.FILL
        }
        canvas.drawCircle(26f, 26f, 22f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(26f, 26f, 22f, borderPaint)

        // Lightning symbol in center
        val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#0B111E")
            style = Paint.Style.FILL
        }
        val boltPath = Path().apply {
            moveTo(28f, 12f)
            lineTo(18f, 28f)
            lineTo(25f, 28f)
            lineTo(22f, 40f)
            lineTo(34f, 24f)
            lineTo(27f, 24f)
            close()
        }
        canvas.drawPath(boltPath, boltPaint)
    }
    style.addImage(CHARGER_ICON_ID, chargerBitmap)

    // 3. EV Repair Workshop Icon (Amber Circle with Wrench)
    val repairBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FFB300") // Amber
            style = Paint.Style.FILL
        }
        canvas.drawCircle(24f, 24f, 20f, bgPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawCircle(24f, 24f, 20f, borderPaint)
        val toolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#0B111E")
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(16f, 32f, 32f, 16f, toolPaint)
    }
    style.addImage(REPAIR_ICON_ID, repairBitmap)

    // 4. Tourism Landmark Pin (Cyan Circle with Center Dot)
    val landmarkBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#00E5FF") // Geely Cyan
            style = Paint.Style.FILL
        }
        canvas.drawCircle(24f, 24f, 18f, bgPaint)
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(24f, 24f, 7f, centerPaint)
    }
    style.addImage(LANDMARK_ICON_ID, landmarkBitmap)

    // 5. Saved Places / Favorite (Gold Star)
    val savedBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FFD700") // Gold
            style = Paint.Style.FILL
        }
        canvas.drawCircle(24f, 24f, 18f, bgPaint)
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#0B111E")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(24f, 24f, 6f, centerPaint)
    }
    style.addImage(SAVED_ICON_ID, savedBitmap)
}

private fun resolveOfflineMbtilesStyle(context: Context, isDaytime: Boolean, mbtilesFile: File): String {
    val styleFile = File(context.cacheDir, "mbtiles_style_${if (isDaytime) "day" else "night"}.json")
    val bgColor = if (isDaytime) "#EFF3F8" else "#0B111E"
    val waterColor = if (isDaytime) "#BFD8FF" else "#102A43"
    val roadColor = if (isDaytime) "#FFFFFF" else "#1E293B"
    val textColor = if (isDaytime) "#0F172A" else "#F0F4F8"
    val textHalo = if (isDaytime) "#FFFFFF" else "#0B111E"

    val styleJson = """
    {
      "version": 8,
      "name": "VDrive Cambodia MBTiles",
      "sources": {
        "openmaptiles": {
          "type": "vector",
          "tiles": ["mbtiles://${mbtilesFile.absolutePath}/{z}/{x}/{y}"]
        }
      },
      "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
      "layers": [
        {
          "id": "background",
          "type": "background",
          "paint": { "background-color": "$bgColor" }
        },
        {
          "id": "water",
          "type": "fill",
          "source": "openmaptiles",
          "source-layer": "water",
          "paint": { "fill-color": "$waterColor" }
        },
        {
          "id": "road",
          "type": "line",
          "source": "openmaptiles",
          "source-layer": "transportation",
          "paint": {
            "line-color": "$roadColor",
            "line-width": 4
          }
        },
        {
          "id": "road_label",
          "type": "symbol",
          "source": "openmaptiles",
          "source-layer": "transportation_name",
          "layout": {
            "symbol-placement": "line",
            "text-field": "{name:km}\n{name:en}",
            "text-size": 12
          },
          "paint": {
            "text-color": "$textColor",
            "text-halo-color": "$textHalo",
            "text-halo-width": 2
          }
        }
      ]
    }
    """.trimIndent()

    try {
        styleFile.writeText(styleJson)
        return "file://${styleFile.absolutePath}"
    } catch (e: Exception) {
        Log.w(TAG, "Failed writing offline MBTiles style JSON: ${e.message}")
        return if (isDaytime) "asset://style_day.json" else "asset://style_night.json"
    }
}


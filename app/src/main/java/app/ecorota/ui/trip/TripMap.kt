package app.ecorota.ui.trip

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ecorota.R
import app.ecorota.core.model.DrivingEvent
import app.ecorota.core.model.RoutePoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs

/**
 * osmdroid map of a recorded route.
 *
 * Tile downloads follow the user's map consent switch: with downloads off the
 * map provider is configured not to open a connection, so only tiles already in
 * the local cache are drawn.
 */
@Composable
fun TripMap(
    route: List<RoutePoint>,
    events: List<DrivingEvent>,
    allowTileDownloads: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapViewHolder() }
    val routePoints = remember(route) {
        route.map { GeoPoint(it.point.latitude, it.point.longitude) }
    }
    val eventPoints = remember(events) {
        events.mapNotNull { it.location }
            .map { GeoPoint(it.latitude, it.longitude) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = holder.mapView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.mapView?.onDetach()
            holder.mapView = null
        }
    }

    AndroidView(
        factory = { viewContext ->
            configureOsmDroid(viewContext)
            MapView(viewContext).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setTilesScaledToDpi(true)
                setUseDataConnection(allowTileDownloads)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
                holder.mapView = this
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    onResume()
                }
            }
        },
        update = { mapView ->
            mapView.setUseDataConnection(allowTileDownloads)
            mapView.overlays.clear()
            if (routePoints.isNotEmpty()) {
                val polyline = Polyline(mapView).apply {
                    setPoints(routePoints)
                    outlinePaint.color = ContextCompat.getColor(context, R.color.brand_green)
                    outlinePaint.strokeWidth = 6f
                }
                mapView.overlays.add(polyline)
            }
            val markerIcon = ContextCompat.getDrawable(context, R.drawable.ic_event_marker)
            eventPoints.forEach { point ->
                val marker = Marker(mapView).apply {
                    position = point
                    icon = markerIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(marker)
            }
            mapView.post { focusRoute(mapView, routePoints + eventPoints) }
        },
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    )
}

private fun configureOsmDroid(context: Context) {
    val configuration = Configuration.getInstance()
    configuration.load(
        context,
        context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE),
    )
    // App-private cache: no storage permission and the consent switch is the
    // only thing that can put tiles there.
    val basePath = java.io.File(context.cacheDir, "osmdroid").apply { mkdirs() }
    configuration.osmdroidBasePath = basePath
    configuration.osmdroidTileCache = java.io.File(basePath, "tiles").apply { mkdirs() }
    configuration.userAgentValue = context.packageName
}

private fun focusRoute(mapView: MapView, points: List<GeoPoint>) {
    if (points.isEmpty()) return
    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    val centered = points.first()
    if (abs(north - south) < 0.0005 && abs(east - west) < 0.0005) {
        mapView.controller.setZoom(16.0)
        mapView.controller.setCenter(GeoPoint(centered.latitude, centered.longitude))
        return
    }
    mapView.zoomToBoundingBox(
        BoundingBox(north, east, south, west),
        false,
        96,
    )
}

private class MapViewHolder {
    var mapView: MapView? = null
}

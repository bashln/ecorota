package app.ecorota.ui.trip

import android.content.Context
import android.text.format.DateFormat
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
import app.ecorota.core.model.GeoPoint as EventGeoPoint
import app.ecorota.core.model.RoutePoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Date
import kotlin.math.abs
import kotlin.math.max

/**
 * Owns the single [MapView] of a trip screen.
 *
 * The detail screen is a lazy list, so an item that scrolls out of the viewport
 * is disposed and composed again when it scrolls back. Building the map in the
 * item meant a new MapView, configuration reload and tile cache reopen on the
 * main thread at every scroll back, which was long enough to freeze the app.
 * The holder keeps the view for the whole screen and releases it only when the
 * screen goes away.
 */
class TripMapHolder {
    internal var mapView: MapView? = null
    internal var appliedRoute: List<RoutePoint>? = null
    internal var appliedEvents: List<DrivingEvent>? = null
    internal var eventMarkers: Map<String, Marker> = emptyMap()
    internal var appliedSelectedEventId: String? = null
    internal var appliedTiles: Boolean? = null
    internal var appliedInitialFocusKey: Pair<List<RoutePoint>, Int>? = null
    internal var hasAppliedInitialFocus = false
    private var resumed = false

    internal fun resume(view: MapView) {
        if (resumed) return
        view.onResume()
        resumed = true
    }

    internal fun pause(view: MapView) {
        if (!resumed) return
        view.onPause()
        resumed = false
    }

    /** Stops the tile provider and releases the map. Call when the screen goes away. */
    fun dispose() {
        val view = mapView ?: return
        pause(view)
        view.onDetach()
        mapView = null
        appliedRoute = null
        appliedEvents = null
        eventMarkers = emptyMap()
        appliedSelectedEventId = null
        appliedTiles = null
        appliedInitialFocusKey = null
        hasAppliedInitialFocus = false
    }
}

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
    selectedEventId: String?,
    resetFocusKey: Int,
    allowTileDownloads: Boolean,
    contentDescription: String,
    holder: TripMapHolder,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val routePoints = remember(route) {
        route.map { GeoPoint(it.point.latitude, it.point.longitude) }
    }
    val selectedPoint = remember(events, selectedEventId) {
        selectedMapLocation(events, selectedEventId)?.let { GeoPoint(it.latitude, it.longitude) }
    }

    DisposableEffect(lifecycleOwner, holder) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = holder.mapView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.resume(mapView)
                Lifecycle.Event.ON_PAUSE -> holder.pause(mapView)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Off screen: pause the tile provider but keep the map for the next
            // scroll back into view.
            holder.mapView?.let { holder.pause(it) }
        }
    }

    AndroidView(
        factory = { viewContext ->
            val mapView = holder.mapView ?: createMapView(viewContext, holder, allowTileDownloads)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                holder.resume(mapView)
            }
            // Back from a scroll: the view was detached from the window, so ask
            // for a new frame instead of trusting the last one.
            mapView.invalidate()
            mapView
        },
        update = { mapView ->
            if (holder.appliedTiles != allowTileDownloads) {
                val enablingTiles = allowTileDownloads && holder.appliedTiles == false
                mapView.setUseDataConnection(allowTileDownloads)
                if (enablingTiles) {
                    // Empty tiles can stay in the in-memory cache after consent
                    // is granted; drop them so the provider asks again.
                    mapView.tileProvider.clearTileCache()
                }
                holder.appliedTiles = allowTileDownloads
                mapView.invalidate()
            }
            // Rebuilding the overlays clears and re-adds every marker, so it is
            // only done when the data changes, not on every recomposition or
            // scroll of the surrounding list.
            if (holder.appliedRoute !== route) {
                holder.appliedRoute = route
                mapView.overlays.clear()
                holder.appliedEvents = null
                holder.eventMarkers = emptyMap()
                if (routePoints.isNotEmpty()) {
                    val polyline = Polyline(mapView).apply {
                        setPoints(routePoints)
                        outlinePaint.color = ContextCompat.getColor(context, R.color.brand_green)
                        outlinePaint.strokeWidth = 6f
                    }
                    mapView.overlays.add(polyline)
                }
            }
            if (holder.appliedEvents !== events) {
                holder.appliedEvents = events
                mapView.overlays.removeAll { it is Marker }
                holder.eventMarkers = events.mapNotNull { event ->
                    event.location?.let { location ->
                        event.id to eventMarker(
                            mapView,
                            context,
                            event,
                            GeoPoint(location.latitude, location.longitude),
                        )
                    }
                }.toMap()
                holder.eventMarkers.values.forEach { marker ->
                    mapView.overlays.add(marker)
                }
            }
            val focusKey = route to resetFocusKey
            if (holder.appliedInitialFocusKey != focusKey) {
                holder.appliedInitialFocusKey = focusKey
                mapView.post {
                    focusRoute(
                        mapView,
                        routePoints + events.mapNotNull { event ->
                            event.location?.let { GeoPoint(it.latitude, it.longitude) }
                        },
                    )
                    holder.hasAppliedInitialFocus = true
                    selectedMapLocation(holder.appliedEvents.orEmpty(), holder.appliedSelectedEventId)
                        ?.let { location ->
                            focusEvent(mapView, GeoPoint(location.latitude, location.longitude))
                            holder.eventMarkers[holder.appliedSelectedEventId]?.showInfoWindow()
                        }
                }
            }
            if (holder.appliedSelectedEventId != selectedEventId) {
                holder.eventMarkers[holder.appliedSelectedEventId]?.closeInfoWindow()
                holder.appliedSelectedEventId = selectedEventId
                if (holder.hasAppliedInitialFocus) {
                    selectedPoint?.let {
                        focusEvent(mapView, it)
                        holder.eventMarkers[selectedEventId]?.showInfoWindow()
                    }
                }
            }
        },
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    )
}

internal fun selectedMapLocation(events: List<DrivingEvent>, eventId: String?): EventGeoPoint? =
    events.firstOrNull { it.id == eventId }?.location

private fun focusEvent(mapView: MapView, point: GeoPoint) {
    mapView.controller.setZoom(max(mapView.zoomLevelDouble, 17.0))
    mapView.controller.animateTo(point)
}

private fun eventMarker(
    mapView: MapView,
    context: Context,
    event: DrivingEvent,
    point: GeoPoint,
) = Marker(mapView).apply {
    position = point
    icon = ContextCompat.getDrawable(context, R.drawable.ic_event_marker)
    title = context.getString(event.type.labelRes())
    snippet = DateFormat.getTimeFormat(context).format(Date(event.startedAtMs))
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
}

private fun createMapView(
    context: Context,
    holder: TripMapHolder,
    allowTileDownloads: Boolean,
): MapView {
    configureOsmDroid(context)
    return MapView(context).apply {
        // The map item of a lazy list leaves the window when it scrolls out.
        // osmdroid destroys its tile provider on window detach by default, and
        // the same view cannot be revived when it scrolls back. Keep the view
        // intact and release it through the holder instead.
        setDestroyMode(false)
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        setTilesScaledToDpi(true)
        setUseDataConnection(allowTileDownloads)
        minZoomLevel = 3.0
        maxZoomLevel = 20.0
        holder.mapView = this
    }
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

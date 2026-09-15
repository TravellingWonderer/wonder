package com.wonder.provider.ui.tripoverview

import android.graphics.DashPathEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.wonder.provider.model.GeoCoordinate
import com.wonder.provider.model.MapLegMarker
import com.wonder.provider.model.MapRouteSegment
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun TripMapView(
    markers: List<MapLegMarker>,
    routeSegments: List<MapRouteSegment>,
    center: GeoCoordinate,
    selectedLegId: String?,
    routeColor: Int,
    routeMutedColor: Int,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.overlays.clear()

            routeSegments.forEach { segment ->
                val highlighted = selectedLegId == null ||
                    segment.fromLegId == selectedLegId ||
                    segment.toLegId == selectedLegId
                val polyline = Polyline(map).apply {
                    setPoints(segment.points.map { GeoPoint(it.latitude, it.longitude) })
                    outlinePaint.color = if (highlighted) routeColor else routeMutedColor
                    outlinePaint.strokeWidth = if (highlighted) 10f else 6f
                    outlinePaint.isAntiAlias = true
                    if (segment.isLongHaul) {
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(24f, 18f), 0f)
                    }
                }
                map.overlays.add(polyline)
            }

            markers.forEach { leg ->
                val marker = Marker(map).apply {
                    position = GeoPoint(leg.coordinate.latitude, leg.coordinate.longitude)
                    title = leg.title
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    alpha = if (selectedLegId == null || selectedLegId == leg.legId) 1f else 0.45f
                    setOnMarkerClickListener { _, _ ->
                        onMarkerClick(leg.legId)
                        true
                    }
                }
                map.overlays.add(marker)
            }

            val allPoints = buildList {
                markers.forEach { add(GeoPoint(it.coordinate.latitude, it.coordinate.longitude)) }
                routeSegments.forEach { segment ->
                    segment.points.forEach { add(GeoPoint(it.latitude, it.longitude)) }
                }
            }
            when {
                allPoints.size >= 2 -> {
                    val box = BoundingBox.fromGeoPoints(allPoints).increaseByScale(1.2f)
                    map.post { map.zoomToBoundingBox(box, true) }
                }
                allPoints.size == 1 -> {
                    map.controller.setZoom(13.0)
                    map.controller.setCenter(allPoints.first())
                }
                else -> {
                    map.controller.setZoom(11.0)
                    map.controller.setCenter(GeoPoint(center.latitude, center.longitude))
                }
            }
            map.invalidate()
        }
    )
}

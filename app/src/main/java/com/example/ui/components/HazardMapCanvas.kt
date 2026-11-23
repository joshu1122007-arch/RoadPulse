package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.IncidentRecord
import com.example.model.IncidentStatus
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.RiskHighRed
import com.example.ui.theme.RiskLowGreen
import com.example.ui.theme.RiskModerateAmber
import com.example.ui.theme.StatusPendingGray
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

private const val TILE_SIZE_DP = 256f

private data class MapCluster(
    val latitude: Double,
    val longitude: Double,
    val incidents: List<IncidentRecord>
)

@Composable
fun HazardMapCanvas(
    centerLat: Double,
    centerLon: Double,
    initialZoom: Float,
    incidents: List<IncidentRecord>,
    selectedIncidentId: String? = null,
    userLocation: Pair<Double, Double>? = null,
    pickLocationMode: Boolean = false,
    pickedLocation: Pair<Double, Double>? = null,
    onCenterChanged: ((Double, Double, Float) -> Unit)? = null,
    onMarkerClick: ((IncidentRecord) -> Unit)? = null,
    onMapPick: ((Double, Double) -> Unit)? = null,
    onRecenterUser: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var mapLat by remember(centerLat) { mutableStateOf(centerLat) }
    var mapLon by remember(centerLon) { mutableStateOf(centerLon) }
    var zoom by remember(initialZoom) { mutableFloatStateOf(initialZoom.coerceIn(5f, 17f)) }

    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFE8EEF2))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val tileSizePx = with(density) { TILE_SIZE_DP.dp.toPx() }
        val intZoom = zoom.roundToInt().coerceIn(5, 17)
        val scaleFactor = 2.0.pow((zoom - intZoom).toDouble())
        val effectiveTilePx = (tileSizePx * scaleFactor).toFloat()

        val centerTileX = lonToTileX(mapLon, intZoom)
        val centerTileY = latToTileY(mapLat, intZoom)

        val numTiles = 1 shl intZoom
        val halfW = widthPx / 2f
        val halfH = heightPx / 2f

        val minTx = floor(centerTileX - halfW / effectiveTilePx).toInt()
        val maxTx = floor(centerTileX + halfW / effectiveTilePx).toInt()
        val minTy = floor(centerTileY - halfH / effectiveTilePx).toInt()
        val maxTy = floor(centerTileY + halfH / effectiveTilePx).toInt()

        // Interactive Pan, Pinch Zoom & Tap layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(intZoom, effectiveTilePx, pickLocationMode) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        val newZoom = (zoom + (ln(zoomChange.toDouble()) / ln(2.0)).toFloat())
                            .coerceIn(5f, 17f)
                        val curScale = 2.0.pow((zoom - intZoom).toDouble())
                        val curTilePx = tileSizePx * curScale
                        val worldTileSpan = (1 shl intZoom).toDouble()
                        val newTileX = (lonToTileX(mapLon, intZoom) - pan.x / curTilePx)
                            .coerceIn(0.0, worldTileSpan - 0.0001)
                        val newTileY = (latToTileY(mapLat, intZoom) - pan.y / curTilePx)
                            .coerceIn(0.0, worldTileSpan - 0.0001)
                        mapLon = tileXToLon(newTileX, intZoom)
                        mapLat = tileYToLat(newTileY, intZoom)
                        zoom = newZoom
                        onCenterChanged?.invoke(mapLat, mapLon, zoom)
                    }
                }
                .pointerInput(pickLocationMode, intZoom, effectiveTilePx, mapLat, mapLon) {
                    detectTapGestures { offset ->
                        if (pickLocationMode && onMapPick != null) {
                            val dxTiles = (offset.x - halfW) / effectiveTilePx
                            val dyTiles = (offset.y - halfH) / effectiveTilePx
                            val tapLat = tileYToLat(latToTileY(mapLat, intZoom) + dyTiles, intZoom)
                                .coerceIn(-85.0, 85.0)
                            val tapLon = tileXToLon(lonToTileX(mapLon, intZoom) + dxTiles, intZoom)
                                .coerceIn(-180.0, 180.0)
                            onMapPick(tapLat, tapLon)
                        }
                    }
                }
        ) {
            // 1. Resilient Cartographic Fallback Grid underneath tiles
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 64f
                var x = (halfW % step)
                while (x < size.width) {
                    drawLine(Color(0xFFD4DEE6), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = (halfH % step)
                while (y < size.height) {
                    drawLine(Color(0xFFD4DEE6), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
                // Subtle radar ring around active center
                drawCircle(
                    color = PrimaryBlue.copy(alpha = 0.14f),
                    radius = size.minDimension * 0.32f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // 2. OpenStreetMap Standard Slippy Tiles
            for (ty in minTy..maxTy) {
                if (ty < 0 || ty >= numTiles) continue
                for (tx in minTx..maxTx) {
                    val wrappedTx = ((tx % numTiles) + numTiles) % numTiles
                    val screenX = halfW + (tx - centerTileX) * effectiveTilePx
                    val screenY = halfH + (ty - centerTileY) * effectiveTilePx
                    val tileUrl = "https://tile.openstreetmap.org/$intZoom/$wrappedTx/$ty.png"

                    val req = remember(tileUrl) {
                        ImageRequest.Builder(context)
                            .data(tileUrl)
                            .setHeader("User-Agent", "RoadPulseAndroid/1.0")
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = req,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                            .size(with(density) { (effectiveTilePx + 1f).toDp() })
                    )
                }
            }

            // 3. User GPS Marker
            if (userLocation != null) {
                val ux = halfW + (lonToTileX(userLocation.second, intZoom) - centerTileX) * effectiveTilePx
                val uy = halfH + (latToTileY(userLocation.first, intZoom) - centerTileY) * effectiveTilePx
                if (ux in -40f..(widthPx + 40f) && uy in -40f..(heightPx + 40f)) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((ux - 14.dp.toPx()).roundToInt(), (uy - 14.dp.toPx()).roundToInt()) }
                            .size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7))
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                }
            }

            // 4. Picked Pin (when in location picker mode)
            if (pickLocationMode && pickedLocation != null) {
                val px = halfW + (lonToTileX(pickedLocation.second, intZoom) - centerTileX) * effectiveTilePx
                val py = halfH + (latToTileY(pickedLocation.first, intZoom) - centerTileY) * effectiveTilePx
                Box(
                    modifier = Modifier
                        .offset { IntOffset((px - 18.dp.toPx()).roundToInt(), (py - 18.dp.toPx()).roundToInt()) }
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DeepNavy)
                        .border(3.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFBBF24))
                    )
                }
            }

            // 5. Clustered or Individual Incident Markers
            val clusters = remember(incidents, zoom) {
                clusterIncidents(incidents, zoom)
            }
            clusters.forEach { cluster ->
                val mx = halfW + (lonToTileX(cluster.longitude, intZoom) - centerTileX) * effectiveTilePx
                val my = halfH + (latToTileY(cluster.latitude, intZoom) - centerTileY) * effectiveTilePx
                if (mx in -60f..(widthPx + 60f) && my in -60f..(heightPx + 60f)) {
                    if (cluster.incidents.size == 1) {
                        val inc = cluster.incidents.first()
                        val isSelected = inc.id == selectedIncidentId
                        val statusRing = when (inc.statusEnum) {
                            IncidentStatus.VERIFIED -> PrimaryBlue
                            IncidentStatus.UNDER_INVESTIGATION -> RiskModerateAmber
                            IncidentStatus.RESOLVED -> RiskLowGreen
                            IncidentStatus.REJECTED -> RiskHighRed
                            IncidentStatus.PENDING_VERIFICATION -> StatusPendingGray
                        }
                        Surface(
                            color = inc.category.color,
                            shape = CircleShape,
                            shadowElevation = if (isSelected) 6.dp else 3.dp,
                            modifier = Modifier
                                .offset {
                                    val half = 18.dp.toPx()
                                    IntOffset((mx - half).roundToInt(), (my - half).roundToInt())
                                }
                                .size(if (isSelected) 42.dp else 36.dp)
                                .border(
                                    width = if (isSelected) 3.dp else 2.dp,
                                    color = if (isSelected) Color.White else statusRing,
                                    shape = CircleShape
                                )
                                .clickable { onMarkerClick?.invoke(inc) }
                                .testTag("map_marker_${inc.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = inc.category.icon,
                                    contentDescription = "${inc.category.label}: ${inc.title}",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        // Cluster Marker
                        Surface(
                            color = DeepNavy,
                            shape = CircleShape,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .offset {
                                    val half = 20.dp.toPx()
                                    IntOffset((mx - half).roundToInt(), (my - half).roundToInt())
                                }
                                .size(40.dp)
                                .border(2.dp, Color.White, CircleShape)
                                .clickable {
                                    // Zoom in on cluster or open first incident
                                    if (zoom < 14.5f) {
                                        mapLat = cluster.latitude
                                        mapLon = cluster.longitude
                                        zoom = (zoom + 1.8f).coerceAtMost(16.5f)
                                    } else {
                                        onMarkerClick?.invoke(cluster.incidents.first())
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${cluster.incidents.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Map Controls Overlay (Zoom +/-, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 3.dp
            ) {
                Column {
                    IconButton(
                        onClick = {
                            zoom = (zoom + 1f).coerceAtMost(17f)
                            onCenterChanged?.invoke(mapLat, mapLon, zoom)
                        },
                        modifier = Modifier.size(44.dp).testTag("map_zoom_in")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                    }
                    IconButton(
                        onClick = {
                            zoom = (zoom - 1f).coerceAtLeast(5f)
                            onCenterChanged?.invoke(mapLat, mapLon, zoom)
                        },
                        modifier = Modifier.size(44.dp).testTag("map_zoom_out")
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                    }
                }
            }
            if (onRecenterUser != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 3.dp
                ) {
                    IconButton(
                        onClick = onRecenterUser,
                        modifier = Modifier.size(44.dp).testTag("map_recenter")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Center on my location",
                            tint = PrimaryBlue
                        )
                    }
                }
            }
        }

        // Coordinates & OpenStreetMap Attribution Badge
        Surface(
            color = DeepNavy.copy(alpha = 0.80f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "© OpenStreetMap • z${String.format("%.1f", zoom)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

private fun clusterIncidents(incidents: List<IncidentRecord>, zoom: Float): List<MapCluster> {
    if (zoom >= 13.5f || incidents.size <= 1) {
        return incidents.map { MapCluster(it.latitude, it.longitude, listOf(it)) }
    }
    val cellDeg = when {
        zoom < 8f -> 0.5
        zoom < 10.5f -> 0.12
        zoom < 12f -> 0.045
        else -> 0.018
    }
    val grouped = incidents.groupBy { inc ->
        val gx = (inc.latitude / cellDeg).roundToInt()
        val gy = (inc.longitude / cellDeg).roundToInt()
        gx to gy
    }
    return grouped.values.map { list ->
        MapCluster(
            latitude = list.map { it.latitude }.average(),
            longitude = list.map { it.longitude }.average(),
            incidents = list
        )
    }
}

private fun lonToTileX(lon: Double, zoom: Int): Double {
    val n = (1 shl zoom).toDouble()
    return (lon + 180.0) / 360.0 * n
}

private fun latToTileY(lat: Double, zoom: Int): Double {
    val clamped = lat.coerceIn(-85.0511, 85.0511)
    val rad = Math.toRadians(clamped)
    val n = (1 shl zoom).toDouble()
    return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n
}

private fun tileXToLon(x: Double, zoom: Int): Double {
    val n = (1 shl zoom).toDouble()
    return x / n * 360.0 - 180.0
}

private fun tileYToLat(y: Double, zoom: Int): Double {
    val n = (1 shl zoom).toDouble()
    val rad = atan(sinh(PI * (1.0 - 2.0 * y / n)))
    return Math.toDegrees(rad)
}

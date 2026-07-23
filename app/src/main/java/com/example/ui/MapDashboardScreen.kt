package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.TransitTeal
import com.example.ui.theme.TransitTealLight
import com.example.viewmodel.BusViewModel
import com.example.viewmodel.ChatMessage
import kotlinx.coroutines.delay
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDashboardScreen(
    viewModel: BusViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // State from ViewModel
    val originQuery by viewModel.originQuery.collectAsState()
    val destQuery by viewModel.destQuery.collectAsState()
    val originStop by viewModel.originStop.collectAsState()
    val destStop by viewModel.destStop.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()
    val isTripActive by viewModel.isTripActive.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val liveLocations by viewModel.liveBusLocations.collectAsState()
    val userCoordinates by viewModel.userCoordinates.collectAsState()
    val walkRouteCoordinates by viewModel.walkRouteCoordinates.collectAsState()

    // OSRM stats
    val walkDistance by viewModel.walkDistanceMeters.collectAsState()
    val walkDuration by viewModel.walkDurationSeconds.collectAsState()

    // Simulated countdown ETA
    var etaSeconds by remember { mutableStateOf(340) }

    // UI Panel triggers
    var showAiAssistant by remember { mutableStateOf(false) }
    var focusedField by remember { mutableStateOf<String?>(null) } // "origin" or "dest"

    // MapView instance reference
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Track real-time map center coordinates for location selection
    var centerPoint by remember { mutableStateOf(GeoPoint(36.6800, 48.5100)) }

    // Nearest station to center point
    val nearestStationToCenter by remember(centerPoint, routes) {
        derivedStateOf {
            viewModel.findNearestStop(centerPoint.latitude, centerPoint.longitude)
        }
    }

    // GPS User Location permission request launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            requestAndMoveToUserLocation(context, viewModel, mapViewRef)
        }
    }

    // Signal map loaded state
    LaunchedEffect(Unit) {
        viewModel.onMapLoaded()
    }

    // Live countdown timer ticking down
    LaunchedEffect(isTripActive) {
        if (isTripActive) {
            etaSeconds = (240..380).random()
            while (etaSeconds > 0) {
                delay(1000)
                etaSeconds--
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = modifier.fillMaxSize()) {

            // 1. NATIVE OSMDROID MAPVIEW
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)

                        // Default Center: Zanjan (36.68, 48.51)
                        controller.setZoom(14.5)
                        controller.setCenter(GeoPoint(36.6800, 48.5100))

                        // Listener to track center point when panning
                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                event?.source?.let { map ->
                                    val c = map.mapCenter
                                    centerPoint = GeoPoint(c.latitude, c.longitude)
                                }
                                return false
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                event?.source?.let { map ->
                                    val c = map.mapCenter
                                    centerPoint = GeoPoint(c.latitude, c.longitude)
                                }
                                return false
                            }
                        })

                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    // Remove all old markers and polylines for clean rendering
                    mapView.overlays.clear()

                    // A. Draw Bus Route Polyline (if selected)
                    selectedRoute?.let { route ->
                        val busPolyline = Polyline(mapView).apply {
                            outlinePaint.color = AndroidColor.parseColor("#1A73E8")
                            outlinePaint.strokeWidth = 14f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND

                            val points = route.coordinates.map { coord ->
                                GeoPoint(coord[1], coord[0])
                            }
                            setPoints(points)
                        }
                        mapView.overlays.add(busPolyline)
                    }

                    // B. Draw Walking Route Polyline (if present)
                    if (walkRouteCoordinates.isNotEmpty()) {
                        val walkPolyline = Polyline(mapView).apply {
                            outlinePaint.color = AndroidColor.parseColor("#10B981")
                            outlinePaint.strokeWidth = 9f
                            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(16f, 16f), 0f)
                            outlinePaint.strokeCap = Paint.Cap.ROUND

                            val points = walkRouteCoordinates.map { coord ->
                                GeoPoint(coord[1], coord[0])
                            }
                            setPoints(points)
                        }
                        mapView.overlays.add(walkPolyline)
                    }

                    // C. Explicit Origin (Boarding) Marker
                    originStop?.let { origin ->
                        val originMarker = Marker(mapView).apply {
                            position = GeoPoint(origin.lat, origin.lng)
                            title = "مبدأ: ${origin.name}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(originMarker)
                    }

                    // D. Explicit Destination (Deboarding) Marker
                    destStop?.let { dest ->
                        val destMarker = Marker(mapView).apply {
                            position = GeoPoint(dest.lat, dest.lng)
                            title = "مقصد: ${dest.name}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(destMarker)
                    }

                    // E. User Location Marker
                    val userMarker = Marker(mapView).apply {
                        position = GeoPoint(userCoordinates.first, userCoordinates.second)
                        title = "موقعیت شما"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    mapView.overlays.add(userMarker)

                    // F. Live Bus Markers (if route active)
                    if (isTripActive) {
                        liveLocations.forEach { bus ->
                            val busMarker = Marker(mapView).apply {
                                position = GeoPoint(bus.lat, bus.lng)
                                title = "اتوبوس 🚌 (خط ${bus.routeId})"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            mapView.overlays.add(busMarker)
                        }
                    }

                    mapView.invalidate()
                }
            )

            // 2. FIXED CENTER PIN MARKER (SNAPP / GOOGLE MAPS STYLE)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = (-22).dp) // Offset pin so tip points exactly at center
                ) {
                    // Floating address tooltip above pin
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = nearestStationToCenter?.name ?: "موقعیت روی نقشه",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Center Pin Icon
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Center Target Location Pin",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(44.dp)
                    )

                    // Pin Tip Drop Shadow
                    Box(
                        modifier = Modifier
                            .size(10.dp, 4.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                }
            }

            // 3. FLOATING TOP SEARCH BAR
            AnimatedVisibility(
                visible = !showAiAssistant,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TextField(
                                value = originQuery,
                                onValueChange = {
                                    viewModel.setOrigin(it)
                                    focusedField = "origin"
                                },
                                placeholder = { Text("مبدأ: جستجو یا انتخاب از روی نقشه", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color(0xFF0F172A),
                                    unfocusedTextColor = Color(0xFF0F172A)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                singleLine = true
                            )
                        }

                        Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), color = Color(0xFFF1F5F9))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TextField(
                                value = destQuery,
                                onValueChange = {
                                    viewModel.setDestination(it)
                                    focusedField = "dest"
                                },
                                placeholder = { Text("مقصد: جستجو یا انتخاب از روی نقشه", fontSize = 12.sp, color = Color(0xFF94A3B8)) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color(0xFF0F172A),
                                    unfocusedTextColor = Color(0xFF0F172A)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                singleLine = true
                            )
                        }

                        // Autocomplete suggestions based on search text
                        val suggestions = remember(originQuery, destQuery, focusedField, routes) {
                            val activeQuery = if (focusedField == "origin") originQuery else destQuery
                            if (activeQuery.isEmpty()) emptyList()
                            else {
                                routes.flatMap { it.stops }
                                    .filter { it.name.contains(activeQuery, ignoreCase = true) }
                                    .distinctBy { it.id }
                            }
                        }

                        if (suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Divider()
                            LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                                items(suggestions) { stop ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (focusedField == "origin") {
                                                    viewModel.setOrigin(stop.name)
                                                    mapViewRef?.controller?.animateTo(GeoPoint(stop.lat, stop.lng))
                                                } else {
                                                    viewModel.setDestination(stop.name)
                                                    mapViewRef?.controller?.animateTo(GeoPoint(stop.lat, stop.lng))
                                                }
                                                focusedField = null
                                            }
                                            .padding(vertical = 10.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = TransitTeal, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(text = stop.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. FLOATING MAP CONTROL BUTTONS (Zoom, My Location, AI Companion, Logout)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Zoom In
                OutlinedIconButton(
                    onClick = { mapViewRef?.controller?.zoomIn() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFF0F172A))
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "افزایش بزرگنمایی", modifier = Modifier.size(20.dp))
                }

                // Zoom Out
                OutlinedIconButton(
                    onClick = { mapViewRef?.controller?.zoomOut() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFF0F172A))
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "کاهش بزرگنمایی", modifier = Modifier.size(20.dp))
                }

                // GPS My Location Button
                OutlinedIconButton(
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            requestAndMoveToUserLocation(context, viewModel, mapViewRef)
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFF1A73E8))
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "موقعیت من", modifier = Modifier.size(20.dp))
                }

                // AI Travel Assistant Toggle
                OutlinedIconButton(
                    onClick = { showAiAssistant = !showAiAssistant },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFF10B981))
                ) {
                    Icon(
                        imageVector = if (showAiAssistant) Icons.Default.Map else Icons.Default.ChatBubble,
                        contentDescription = "دستیار هوشمند",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Logout Button
                OutlinedIconButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "خروج", modifier = Modifier.size(20.dp))
                }
            }

            // 5. BOTTOM PANEL (LOCATION SELECTION & ROUTE SUMMARY)
            AnimatedVisibility(
                visible = !showAiAssistant,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag Indicator
                        Box(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFCBD5E1))
                        )

                        if (!isTripActive) {
                            // --- MODE A: LOCATION SELECTION PANEL (SET ORIGIN / SET DESTINATION) ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = nearestStationToCenter?.name ?: "موقعیت علامت‌گذاری شده",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = String.format("مختصات: %.4f, %.4f", centerPoint.latitude, centerPoint.longitude),
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Button: Set as Origin
                                Button(
                                    onClick = {
                                        viewModel.setOriginFromCoords(centerPoint.latitude, centerPoint.longitude)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.TripOrigin, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("انتخاب به‌عنوان مبدأ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                // Button: Set as Destination
                                Button(
                                    onClick = {
                                        viewModel.setDestinationFromCoords(centerPoint.latitude, centerPoint.longitude)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("انتخاب به‌عنوان مقصد", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // --- MODE B: ACTIVE ROUTE & TRIP SUMMARY PANEL ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedRoute?.name ?: "مسیر اتوبوس مشخص شده",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "خط اتوبوس: " + (selectedRoute?.number ?: "") + " • زنجان",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (etaSeconds > 0) "${(etaSeconds / 60)}" else "۰",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1A73E8)
                                        )
                                        Text(
                                            text = "دقیقه",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1A73E8)
                                        )
                                    }
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFF1F5F9))

                            // OSRM Walk details
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val distText = walkDistance?.let {
                                        if (it >= 1000) "${String.format("%.1f", it / 1000.0)} کیلومتر" else "${it.roundToInt()} متر"
                                    } ?: "۳۵0 متر"
                                    Text(text = "فاصله پیاده‌روی", fontSize = 10.sp, color = Color(0xFF64748B))
                                    Text(text = distText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val durationText = walkDuration?.let {
                                        "${(it / 60).roundToInt()} دقیقه"
                                    } ?: "۴ دقیقه"
                                    Text(text = "زمان پیاده‌روی", fontSize = 10.sp, color = Color(0xFF64748B))
                                    Text(text = durationText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = Color(0xFF1A73E8), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "ایستگاه مبدأ", fontSize = 10.sp, color = Color(0xFF64748B))
                                    Text(text = originStop?.name ?: "سبزه میدان", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.clearTrip() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("پاکسازی مسیر", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 6. GEMINI AI COMPANION CHAT DRAWER
            AnimatedVisibility(
                visible = showAiAssistant,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.85f)
                    .align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.surface)
                    .safeDrawingPadding()
            ) {
                val chatMessages by viewModel.chatMessages.collectAsState()
                val isAiLoading by viewModel.isAiLoading.collectAsState()
                var messageText by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(TransitTeal.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✨")
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "دستیار سفر هوشمند",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TransitTeal
                            )
                        }
                        IconButton(onClick = { showAiAssistant = false }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(chatMessages) { chat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (chat.isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Card(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (chat.isUser) 16.dp else 2.dp,
                                        bottomEnd = if (chat.isUser) 2.dp else 16.dp
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (chat.isUser) Color(0xFFF1F5F9) else Color(0xFFE8F0FE)
                                    ),
                                    modifier = Modifier.widthIn(max = 260.dp),
                                    border = BorderStroke(1.dp, if (chat.isUser) Color(0xFFE2E8F0) else Color(0xFFD0E1FD))
                                ) {
                                    Text(
                                        text = chat.text,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(12.dp),
                                        lineHeight = 19.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                        }

                        if (isAiLoading) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = TransitTealLight),
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("در حال پردازش...", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("راهنمایی مسیر یا دیدنی‌های زنجان...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    viewModel.sendAiMessage(messageText)
                                    messageText = ""
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(TransitTeal)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ارسال", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// Helper to request GPS location and move map camera
private fun requestAndMoveToUserLocation(
    context: Context,
    viewModel: BusViewModel,
    mapView: MapView?
) {
    try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val provider = when {
            isGps -> LocationManager.GPS_PROVIDER
            isNetwork -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider != null) {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                val lastLoc: Location? = locationManager.getLastKnownLocation(provider)
                if (lastLoc != null) {
                    viewModel.updateUserLocation(lastLoc.latitude, lastLoc.longitude)
                    mapView?.controller?.animateTo(GeoPoint(lastLoc.latitude, lastLoc.longitude), 16.0, 1000L)
                } else {
                    locationManager.requestSingleUpdate(provider, object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            viewModel.updateUserLocation(location.latitude, location.longitude)
                            mapView?.controller?.animateTo(GeoPoint(location.latitude, location.longitude), 16.0, 1000L)
                        }
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }, null)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

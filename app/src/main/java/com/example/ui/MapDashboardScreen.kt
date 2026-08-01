package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.viewmodel.AppRole
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.TransitTeal
import com.example.ui.theme.TransitTealLight
import com.example.util.CustomMarkerHelper
import com.example.viewmodel.BusViewModel
import com.example.viewmodel.MapUiState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDashboardScreen(
    viewModel: BusViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ViewModel States
    val uiState by viewModel.uiState.collectAsState()
    val originGeoPoint by viewModel.originGeoPoint.collectAsState()
    val destGeoPoint by viewModel.destGeoPoint.collectAsState()
    val routeDistanceKm by viewModel.routeDistanceKm.collectAsState()
    val routeDurationMin by viewModel.routeDurationMin.collectAsState()
    val routePolylinePoints by viewModel.routePolylinePoints.collectAsState()
    val isRouteLoading by viewModel.isRouteLoading.collectAsState()

    val transitPlan by viewModel.transitPlan.collectAsState()
    val liveBuses by viewModel.liveBuses.collectAsState()

    val activeRole by viewModel.activeRole.collectAsState()
    val busLines by viewModel.busLines.collectAsState()
    val driverLineId by viewModel.driverLineId.collectAsState()
    val driverBusId by viewModel.driverBusId.collectAsState()
    val driverId by viewModel.driverId.collectAsState()
    val isDriverOnShift by viewModel.isDriverOnShift.collectAsState()
    val supervisorSelectedLineId by viewModel.supervisorSelectedLineId.collectAsState()
    val supervisorActiveBuses by viewModel.supervisorActiveBuses.collectAsState()
    val passengerSelectedLineId by viewModel.passengerSelectedLineId.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val originStop by viewModel.originStop.collectAsState()
    val destStop by viewModel.destStop.collectAsState()
    val userCoordinates by viewModel.userCoordinates.collectAsState()

    // FusedLocationProviderClient for GPS
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // MapView reference
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Real-time map center coordinates for location selection
    var centerPoint by remember { mutableStateOf(GeoPoint(36.6800, 48.4700)) }

    // Custom Bitmaps for Markers
    val originMarkerDrawable = remember(context) {
        CustomMarkerHelper.createRideMarker(context, "مبدأ", isOrigin = true)
    }
    val destMarkerDrawable = remember(context) {
        CustomMarkerHelper.createRideMarker(context, "مقصد", isOrigin = false)
    }

    // Map of Bus Markers for smooth updates
    val busMarkers = remember { mutableMapOf<String, Marker>() }

    // UI Panels
    var showAiAssistant by remember { mutableStateOf(false) }
    var isSheetExpanded by remember { mutableStateOf(true) }
    var showGpsDialog by remember { mutableStateOf(false) }
    var pendingLocationFetch by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    val isGpsEnabled = remember(context) {
        {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        }
    }

    // Nearest bus stop or landmark to map center
    val nearestStationToCenter by remember(centerPoint) {
        derivedStateOf {
            viewModel.findNearestStop(centerPoint.latitude, centerPoint.longitude)
        }
    }

    // Runtime Permission Request Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (!isGpsEnabled()) {
                showGpsDialog = true
            } else {
                pendingLocationFetch = true
            }
        } else {
            Toast.makeText(context, "دسترسی به موقعیت داده نشد", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to request GPS user location and smoothly animate map camera
    val moveToUserLocation = remember(context, viewModel, mapViewRef, fusedLocationClient, isGpsEnabled) {
        {
            val animateToTarget = { lat: Double, lng: Double, msg: String ->
                val targetPoint = GeoPoint(lat, lng)
                viewModel.updateUserLocation(lat, lng)
                centerPoint = targetPoint
                mapViewRef?.controller?.animateTo(targetPoint, 16.5, 1000L)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }

            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (!hasFine && !hasCoarse) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else if (!isGpsEnabled()) {
                showGpsDialog = true
            } else {
                try {
                    val cts = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc: Location? ->
                            if (loc != null) {
                                animateToTarget(loc.latitude, loc.longitude, "موقعیت زنده شما دریافت شد")
                            } else {
                                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                                    if (lastLoc != null) {
                                        animateToTarget(lastLoc.latitude, lastLoc.longitude, "آخرین موقعیت مکانی دریافت شد")
                                    } else {
                                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                                        var foundLoc: Location? = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                        if (foundLoc == null) foundLoc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                                        if (foundLoc != null) {
                                            animateToTarget(foundLoc.latitude, foundLoc.longitude, "موقعیت مکانی شما دریافت شد")
                                        } else {
                                            showGpsDialog = true
                                        }
                                    }
                                }.addOnFailureListener {
                                    showGpsDialog = true
                                }
                            }
                        }
                        .addOnFailureListener {
                            showGpsDialog = true
                        }
                } catch (e: Exception) {
                    showGpsDialog = true
                }
            }
        }
    }

    LaunchedEffect(pendingLocationFetch) {
        if (pendingLocationFetch) {
            pendingLocationFetch = false
            moveToUserLocation()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if ((hasFine || hasCoarse) && isGpsEnabled()) {
                    if (showGpsDialog || pendingLocationFetch) {
                        showGpsDialog = false
                        pendingLocationFetch = false
                        moveToUserLocation()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Zoom camera to fit route bounds during ROUTE_PREVIEW
    LaunchedEffect(routePolylinePoints, uiState) {
        if (uiState == MapUiState.ROUTE_PREVIEW && routePolylinePoints.size >= 2 && mapViewRef != null) {
            try {
                val boundingBox = BoundingBox.fromGeoPoints(routePolylinePoints)
                mapViewRef?.zoomToBoundingBox(boundingBox, true, 150)
            } catch (e: Exception) {
                originGeoPoint?.let {
                    mapViewRef?.controller?.animateTo(it)
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = modifier.fillMaxSize()) {

            // ==========================================
            // 1. OSMDROID MAPVIEW WITH REAL-TIME OVERLAYS
            // ==========================================
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)

                        controller.setZoom(14.5)
                        controller.setCenter(GeoPoint(36.6800, 48.4700))

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
                    mapView.overlays.clear()

                    val plan = transitPlan
                    if (plan != null) {
                        // 1. Segment 1: Walk to Station (Dashed Blue Line)
                        if (plan.walkToStation.points.isNotEmpty()) {
                            val walk1Poly = Polyline(mapView).apply {
                                outlinePaint.color = AndroidColor.parseColor("#3B82F6")
                                outlinePaint.strokeWidth = 10f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 12f), 0f)
                                setPoints(plan.walkToStation.points)
                            }
                            mapView.overlays.add(walk1Poly)
                        }

                        // 2. Segment 2: Bus Ride (Solid Bus Line)
                        if (plan.busRide.points.isNotEmpty()) {
                            val busPolyColor = try {
                                AndroidColor.parseColor(plan.busLine.colorHex)
                            } catch (e: Exception) {
                                AndroidColor.parseColor("#2563EB")
                            }
                            val busPoly = Polyline(mapView).apply {
                                outlinePaint.color = busPolyColor
                                outlinePaint.strokeWidth = 16f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                setPoints(plan.busRide.points)
                            }
                            mapView.overlays.add(busPoly)
                        }

                        // 3. Segment 3: Walk to Dest (Dashed Red/Orange Line)
                        if (plan.walkToDest.points.isNotEmpty()) {
                            val walk2Poly = Polyline(mapView).apply {
                                outlinePaint.color = AndroidColor.parseColor("#EF4444")
                                outlinePaint.strokeWidth = 10f
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 12f), 0f)
                                setPoints(plan.walkToDest.points)
                            }
                            mapView.overlays.add(walk2Poly)
                        }

                        // 4. Boarding Station Marker ("ایستگاه سوار شدن")
                        val boardingMarkerDrawable = CustomMarkerHelper.createStationMarker(context, "ایستگاه سوار شدن: ${plan.originStation.name}", isBoarding = true)
                        val boardingMarker = Marker(mapView).apply {
                            position = plan.originStation.toGeoPoint()
                            icon = boardingMarkerDrawable
                            title = plan.originStation.name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(boardingMarker)

                        // 5. Alighting Station Marker ("ایستگاه پیاده شدن")
                        val alightingMarkerDrawable = CustomMarkerHelper.createStationMarker(context, "ایستگاه پیاده شدن: ${plan.destStation.name}", isBoarding = false)
                        val alightingMarker = Marker(mapView).apply {
                            position = plan.destStation.toGeoPoint()
                            icon = alightingMarkerDrawable
                            title = plan.destStation.name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(alightingMarker)
                    } else if (routePolylinePoints.isNotEmpty()) {
                        // Fallback simple polyline
                        val routePolyline = Polyline(mapView).apply {
                            outlinePaint.color = AndroidColor.parseColor("#1D4ED8")
                            outlinePaint.strokeWidth = 14f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            setPoints(routePolylinePoints)
                        }
                        mapView.overlays.add(routePolyline)
                    }

                    // User Origin Pin
                    originGeoPoint?.let { origin ->
                        val originMarker = Marker(mapView).apply {
                            position = origin
                            icon = originMarkerDrawable
                            title = "مبدأ کاربر"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(originMarker)
                    }

                    // User Destination Pin
                    destGeoPoint?.let { dest ->
                        val destMarker = Marker(mapView).apply {
                            position = dest
                            icon = destMarkerDrawable
                            title = "مقصد کاربر"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(destMarker)
                    }

                    // User Location Marker
                    val userMarker = Marker(mapView).apply {
                        position = GeoPoint(userCoordinates.first, userCoordinates.second)
                        title = "موقعیت شما"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    mapView.overlays.add(userMarker)

                    // Live Buses Markers from ActiveBuses (Heading, Smooth Animation & Timestamp Staleness)
                    val nowMs = System.currentTimeMillis()
                    val validBuses = liveBuses.filter { bus ->
                        if (!bus.isActive || bus.lat == 0.0 || bus.lng == 0.0) return@filter false
                        val ageMs = if (bus.timestamp > 0) nowMs - bus.timestamp else 0L
                        // Hide markers if older than 10 minutes (600,000 ms)
                        ageMs < 600000L
                    }
                    val activeBusIds = validBuses.map { it.busId }.toSet()
                    val removedIds = busMarkers.keys.filter { it !in activeBusIds }
                    removedIds.forEach { id ->
                        busMarkers[id]?.let { mapView.overlays.remove(it) }
                        busMarkers.remove(id)
                    }

                    validBuses.forEach { bus ->
                        val busColorHex = plan?.busLine?.colorHex ?: "#2563EB"
                        val lineNum = plan?.busLine?.number ?: bus.lineId.replace("line_", "")
                        val busIconDrawable = CustomMarkerHelper.createLiveBusMarker(context, lineNum, busColorHex)
                        val targetPos = bus.toGeoPoint()
                        val targetRot = bus.heading.toFloat()

                        val ageMs = if (bus.timestamp > 0) nowMs - bus.timestamp else 0L
                        // If timestamp is older than 2 minutes (120,000 ms), dim marker alpha
                        val targetAlpha = if (ageMs > 120000L) 0.45f else 1.0f

                        val existingMarker = busMarkers[bus.busId]
                        if (existingMarker != null) {
                            existingMarker.icon = busIconDrawable
                            existingMarker.title = "اتوبوس خط $lineNum (${bus.speedKmh} km/h)"
                            existingMarker.alpha = targetAlpha
                            if (!mapView.overlays.contains(existingMarker)) {
                                mapView.overlays.add(existingMarker)
                            }

                            // Smooth animation for position and rotation
                            val startLat = existingMarker.position.latitude
                            val startLng = existingMarker.position.longitude
                            val startRot = existingMarker.rotation

                            val endLat = targetPos.latitude
                            val endLng = targetPos.longitude
                            var rotDiff = (targetRot - startRot) % 360f
                            if (rotDiff > 180f) rotDiff -= 360f
                            if (rotDiff < -180f) rotDiff += 360f

                            val distMoved = Math.abs(startLat - endLat) > 0.000005 || Math.abs(startLng - endLng) > 0.000005
                            val rotMoved = Math.abs(rotDiff) > 0.5f

                            if (distMoved || rotMoved) {
                                android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                                    duration = 750
                                    addUpdateListener { anim ->
                                        val f = anim.animatedFraction
                                        val curLat = startLat + (endLat - startLat) * f
                                        val curLng = startLng + (endLng - startLng) * f
                                        val curRot = startRot + rotDiff * f
                                        existingMarker.position = GeoPoint(curLat, curLng)
                                        existingMarker.rotation = curRot
                                        mapView.invalidate()
                                    }
                                    start()
                                }
                            } else {
                                existingMarker.position = targetPos
                                existingMarker.rotation = targetRot
                            }
                        } else {
                            val newMarker = Marker(mapView).apply {
                                position = targetPos
                                icon = busIconDrawable
                                title = "اتوبوس خط $lineNum (${bus.speedKmh} km/h)"
                                rotation = targetRot
                                alpha = targetAlpha
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            busMarkers[bus.busId] = newMarker
                            mapView.overlays.add(newMarker)
                        }
                    }

                    mapView.invalidate()
                }
            )

            // ==========================================
            // 2. FIXED CENTER PIN MARKER
            // ==========================================
            if (uiState != MapUiState.ROUTE_PREVIEW) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = (-24).dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = when (uiState) {
                                    MapUiState.SELECT_ORIGIN -> nearestStationToCenter?.name ?: "مبدأ را روی نقشه مشخص کنید"
                                    MapUiState.SELECT_DESTINATION -> nearestStationToCenter?.name ?: "مقصد را روی نقشه مشخص کنید"
                                    else -> ""
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Center Location Pin",
                            tint = if (uiState == MapUiState.SELECT_ORIGIN) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(14.dp, 4.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                        )
                    }
                }
            }

            // ==========================================
            // 3. TOP SEARCH BAR & GEOLOCATION SUGGESTIONS
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                // Role Selector Top Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val roles = listOf(
                            AppRole.PASSENGER to "🙋‍♂️ مسافر",
                            AppRole.DRIVER to "🚌 راننده",
                            AppRole.SUPERVISOR to "👁️ ناظر"
                        )
                        roles.forEach { (roleItem, title) ->
                            val isSel = activeRole == roleItem
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .padding(2.dp)
                                    .clickable { viewModel.setActiveRole(roleItem) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSel) Color(0xFF1D4ED8) else Color.Transparent
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = {
                                Text(
                                    text = if (uiState == MapUiState.SELECT_ORIGIN) "جستجوی مبدأ (نام خیابان، میدان)..." else "جستجوی مقصد...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                // Search Results Dropdown List
                AnimatedVisibility(
                    visible = searchQuery.length >= 2,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 280.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        if (isSearching) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("در حال جستجو...", fontSize = 13.sp, color = Color(0xFF64748B))
                            }
                        } else if (searchResults.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("موقعیتی با این نام پیدا نشد", fontSize = 13.sp, color = Color(0xFF64748B))
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(searchResults) { result ->
                                    val title = result.name ?: result.displayName?.split(",")?.firstOrNull() ?: "موقعیت ناشناخته"
                                    val fullAddress = result.displayName ?: ""

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.selectSearchResult(result) { targetGeo ->
                                                    mapViewRef?.controller?.animateTo(targetGeo, 16.0, 1000L)
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFEFF6FF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = null,
                                                tint = Color(0xFF1D4ED8),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = fullAddress,
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Divider(color = Color(0xFFF1F5F9))
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. FLOATING MAP ACTION BUTTONS
            // ==========================================
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
                    Icon(imageVector = Icons.Default.Add, contentDescription = "بزرگنمایی", modifier = Modifier.size(20.dp))
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
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "کوچکنمایی", modifier = Modifier.size(20.dp))
                }

                // GPS My Location Floating Button
                OutlinedIconButton(
                    onClick = {
                        moveToUserLocation()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFF1D4ED8))
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "موقعیت من", modifier = Modifier.size(20.dp))
                }

                // AI Travel Assistant
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

                // Logout
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

            // ==========================================
            // 5. DYNAMIC RIDE-HAILING BOTTOM SHEET
            // ==========================================
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
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .width(42.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFCBD5E1))
                        )

                        when (activeRole) {
                            AppRole.DRIVER -> {
                                DriverShiftCard(
                                    busLines = busLines,
                                    selectedLineId = driverLineId,
                                    busId = driverBusId,
                                    driverId = driverId,
                                    isOnShift = isDriverOnShift,
                                    onLineSelect = { viewModel.setDriverLine(it) },
                                    onBusIdChange = { viewModel.setDriverBusId(it) },
                                    onDriverIdChange = { viewModel.setDriverId(it) },
                                    onStartShift = { viewModel.startDriverShift() },
                                    onEndShift = { viewModel.endDriverShift() }
                                )
                            }
                            AppRole.SUPERVISOR -> {
                                SupervisorCard(
                                    busLines = busLines,
                                    selectedLineId = supervisorSelectedLineId,
                                    activeBuses = supervisorActiveBuses,
                                    onSelectLine = { viewModel.selectSupervisorLine(it) }
                                )
                            }
                            AppRole.PASSENGER -> {
                                AnimatedContent(
                                    targetState = uiState,
                                    label = "BottomSheetStateTransition"
                                ) { state ->
                            when (state) {
                                // STATE 1: SELECT ORIGIN
                                MapUiState.SELECT_ORIGIN -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFD1FAE5)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.TripOrigin,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "انتخاب مبدأ",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = nearestStationToCenter?.name ?: String.format("مختصات: %.4f, %.4f", centerPoint.latitude, centerPoint.longitude),
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(18.dp))

                                        Button(
                                            onClick = { viewModel.confirmOrigin(centerPoint) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "تایید مبدأ",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // STATE 2: SELECT DESTINATION
                                MapUiState.SELECT_DESTINATION -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.TripOrigin, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = "مبدأ: ${originStop?.name ?: "انتخاب شده"}", fontSize = 12.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.weight(1f))
                                                Text(
                                                    text = "تغییر",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF1D4ED8),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable { viewModel.setUiState(MapUiState.SELECT_ORIGIN) }
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFEE2E2)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "انتخاب مقصد",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = nearestStationToCenter?.name ?: String.format("مختصات: %.4f, %.4f", centerPoint.latitude, centerPoint.longitude),
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(18.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.setUiState(MapUiState.SELECT_ORIGIN) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(52.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text("بازگشت", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                            }

                                            Button(
                                                onClick = { viewModel.confirmDestination(centerPoint) },
                                                modifier = Modifier
                                                    .weight(2f)
                                                    .height(52.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("تایید مقصد", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                // STATE 3: ROUTE PREVIEW
                                MapUiState.ROUTE_PREVIEW -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        val plan = transitPlan

                                        // Drawer Expansion Toggle Bar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isSheetExpanded = !isSheetExpanded }
                                                .padding(bottom = 10.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = "تغییر حالت کشو",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isSheetExpanded) "لمس برای کوچک کردن کشو" else "لمس برای باز کردن جزئیات مسیر کشویی",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B)
                                            )
                                        }

                                        if (plan != null) {
                                            // Header Bus Line Badge
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    color = try { Color(android.graphics.Color.parseColor(plan.busLine.colorHex)) } catch (e: Exception) { Color(0xFF2563EB) },
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        text = "خط ${plan.busLine.number}",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = plan.busLine.name,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            AnimatedVisibility(
                                                visible = isSheetExpanded,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut() + shrinkVertically()
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    if (liveBuses.isEmpty() || plan.matchedBus == null) {
                                                        Card(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(bottom = 10.dp),
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                                            shape = RoundedCornerShape(12.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.DirectionsBus,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFFDC2626),
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = "در حال حاضر اتوبوسی در این خط فعال نیست",
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF991B1B)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // 3 Stat Cards (Total Duration, Bus ETA, Distance)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Card(
                                                            modifier = Modifier.weight(1f),
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                                            shape = RoundedCornerShape(14.dp)
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(10.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                            ) {
                                                                Text("کل زمان", fontSize = 10.sp, color = Color(0xFF64748B))
                                                                Text(String.format("%.0f دقیقه", plan.totalDurationMin), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                                            }
                                                        }

                                                        Card(
                                                            modifier = Modifier.weight(1f),
                                                            colors = CardDefaults.cardColors(containerColor = if (plan.matchedBus != null && plan.matchedBus.speed <= 1.0) Color(0xFFFEE2E2) else Color(0xFFFEF3C7)),
                                                            shape = RoundedCornerShape(14.dp)
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(10.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                            ) {
                                                                Text("رسیدن اتوبوس", fontSize = 10.sp, color = Color(0xFF64748B))
                                                                val matchedBus = plan.matchedBus
                                                                val etaText = when {
                                                                    matchedBus == null -> "نامشخص"
                                                                    matchedBus.speed <= 1.0 -> "در حال توقف"
                                                                    plan.busEtaMin > 0 -> "~ ${plan.busEtaMin} دقیقه"
                                                                    else -> "در حال توقف"
                                                                }
                                                                Text(
                                                                    text = etaText,
                                                                    fontSize = if (matchedBus != null) 12.sp else 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (matchedBus != null && matchedBus.speed <= 1.0) Color(0xFFDC2626) else Color(0xFFD97706),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }

                                                        Card(
                                                            modifier = Modifier.weight(1f),
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                                                            shape = RoundedCornerShape(14.dp)
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(10.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally
                                                            ) {
                                                                Text("مسافت کل", fontSize = 10.sp, color = Color(0xFF64748B))
                                                                Text(String.format("%.1f km", plan.totalDistanceKm), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                                            }
                                                        }
                                                    }

                                                    if (plan.matchedBus != null) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = CardDefaults.cardColors(containerColor = if (plan.matchedBus.speed <= 1.0) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)),
                                                            border = BorderStroke(1.dp, if (plan.matchedBus.speed <= 1.0) Color(0xFFFCA5A5) else Color(0xFFBFDBFE)),
                                                            shape = RoundedCornerShape(12.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.DirectionsBus,
                                                                    contentDescription = null,
                                                                    tint = if (plan.matchedBus.speed <= 1.0) Color(0xFFDC2626) else Color(0xFF1D4ED8),
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                val bus = plan.matchedBus
                                                                val fullEtaMsg = if (bus.speed <= 1.0) {
                                                                    "وضعیت اتوبوس: در حال توقف"
                                                                } else {
                                                                    "اتوبوس تا این ایستگاه ~ ${plan.busEtaMin} دقیقه دیگر می‌رسد"
                                                                }
                                                                Text(
                                                                    text = fullEtaMsg,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (bus.speed <= 1.0) Color(0xFF991B1B) else Color(0xFF1E40AF)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(12.dp))

                                                    // Step-by-Step Transit Instructions with exact Persian breakdowns
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                        shape = RoundedCornerShape(16.dp)
                                                    ) {
                                                        Column(modifier = Modifier.padding(14.dp)) {
                                                            // Step 1: Walk to origin station
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text("🚶‍♂️", fontSize = 18.sp)
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Column {
                                                                    Text("۱. ${plan.walkToStation.title}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                                                    Text("مسیر پیاده تا ایستگاه مبدأ: ${plan.walkToStation.description}", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Medium)
                                                                }
                                                            }
                                                            Divider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFE2E8F0))

                                                            // Step 2: Bus Ride
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text("🚌", fontSize = 18.sp)
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Column {
                                                                    Text("۲. سوار شدن در ایستگاه ${plan.originStation.name} ➔ پیاده شدن در ${plan.destStation.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                                                    Text("مسیر حرکت اتوبوس: ${plan.busRide.description}", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.Medium)
                                                                }
                                                            }
                                                            Divider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFE2E8F0))

                                                            // Step 3: Walk to final destination
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text("🚶‍♀️", fontSize = 18.sp)
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Column {
                                                                    Text("۳. ${plan.walkToDest.title}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                                                    Text("مسیر پیاده از ایستگاه مقصد تا مقصد: ${plan.walkToDest.description}", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Collapsed view summary
                                            AnimatedVisibility(
                                                visible = !isSheetExpanded,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut() + shrinkVertically()
                                            ) {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                                    shape = RoundedCornerShape(14.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "🚶‍♂️ ${plan.walkToStation.description} • 🚌 ${plan.busRide.description} • 🚶‍♀️ ${plan.walkToDest.description}",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF1E293B),
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (isRouteLoading) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("در حال محاسبه بهترین مسیر اتوبوس...", fontSize = 12.sp, color = Color(0xFF64748B))
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.resetSelection() },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(50.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text("تغییر مسیر", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                            }

                                            Button(
                                                onClick = { /* Start Navigation */ },
                                                modifier = Modifier
                                                    .weight(2f)
                                                    .height(50.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("شروع مسیریابی اتوبوس", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                        }
                    }
                }
            }

            // ==========================================
            // 6. GEMINI AI TRAVEL ASSISTANT DRAWER
            // ==========================================
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
                            placeholder = { Text("سوال یا درخواست شما...", fontSize = 12.sp) },
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

            if (showGpsDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showGpsDialog = false
                        pendingLocationFetch = false
                    },
                    title = {
                        Text(
                            text = "برای استفاده از موقعیت، لطفاً GPS را روشن کنید",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                    },
                    text = {
                        Text(
                            text = "برای دریافت و نمایش موقعیت زنده شما روی نقشه، لازم است سیستم موقعیت‌یاب دستگاه (GPS) فعال باشد.",
                            fontSize = 13.sp,
                            color = Color(0xFF334155)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showGpsDialog = false
                                pendingLocationFetch = true
                                try {
                                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "امکان باز کردن تنظیمات موقعیت وجود ندارد", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                        ) {
                            Text("روشن کردن", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showGpsDialog = false
                                pendingLocationFetch = false
                            }
                        ) {
                            Text("انصراف", color = Color(0xFF64748B))
                        }
                    },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}

@Composable
fun DriverShiftCard(
    busLines: List<com.example.model.BusLine>,
    selectedLineId: String,
    busId: String,
    driverId: String,
    isOnShift: Boolean,
    onLineSelect: (String) -> Unit,
    onBusIdChange: (String) -> Unit,
    onDriverIdChange: (String) -> Unit,
    onStartShift: () -> Unit,
    onEndShift: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLine = busLines.find { it.id == selectedLineId } ?: busLines.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "پنل راننده اتوبوسرانی",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isOnShift) Color(0xFFD1FAE5) else Color(0xFFF1F5F9)
            ) {
                Text(
                    text = if (isOnShift) "🟢 روی شیفت" else "⚪ آفلاین",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnShift) Color(0xFF047857) else Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "انتخاب خط اتوبوسرانی:", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { if (!isOnShift) expanded = true },
                enabled = !isOnShift,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
            ) {
                Text(
                    text = selectedLine?.let { "${it.name} (${it.city})" } ?: "انتخاب خط",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                busLines.forEach { line ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(text = line.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "شهر: ${line.city} | ایستگاه‌ها: ${line.stations.size}", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        },
                        onClick = {
                            onLineSelect(line.id)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = busId,
                onValueChange = onBusIdChange,
                enabled = !isOnShift,
                label = { Text("شماره اتوبوس") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = driverId,
                onValueChange = onDriverIdChange,
                enabled = !isOnShift,
                label = { Text("نام راننده") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isOnShift) {
            Button(
                onClick = onEndShift,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("پایان شیفت کاری", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Button(
                onClick = onStartShift,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("شروع شیفت کاری (ارسال موقعیت زنده)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun SupervisorCard(
    busLines: List<com.example.model.BusLine>,
    selectedLineId: String?,
    activeBuses: List<com.example.model.LiveBus>,
    onSelectLine: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "پنل نظارت و پایش خطوط اتوبوسرانی",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(text = "انتخاب خط جهت مشاهده اتوبوس‌ها:", fontSize = 12.sp, color = Color(0xFF64748B))

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedLineId == null,
                    onClick = { onSelectLine(null) },
                    label = { Text("همه خطوط") }
                )
            }
            items(busLines) { line ->
                val isSel = line.id == selectedLineId
                FilterChip(
                    selected = isSel,
                    onClick = { onSelectLine(line.id) },
                    label = { Text(line.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedLineId != null) {
            val selectedLine = busLines.find { it.id == selectedLineId }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "اطلاعات خط: ${selectedLine?.name ?: ""}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "شهر/استان: ${selectedLine?.city ?: "زنجان"} - ${selectedLine?.province ?: "زنجان"} | ایستگاه‌ها: ${selectedLine?.stations?.size ?: 0}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "اتوبوس‌های آنلاین در این خط: ${activeBuses.size} دستگاه",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "اطلاعات: همه خطوط اتوبوسرانی",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "کل اتوبوس‌های آنلاین: ${activeBuses.size} دستگاه",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (activeBuses.isEmpty()) {
            Text(
                text = "در حال حاضر اتوبوسی در این خط فعال نیست",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                activeBuses.forEach { bus ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = "اتوبوس کد: ${bus.busId} | راننده: ${bus.driverId}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(text = "سرعت: ${bus.speed.toInt()} km/h | خط: ${bus.lineId}", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            }
        }
    }
}

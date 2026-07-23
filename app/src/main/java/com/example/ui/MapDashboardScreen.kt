package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.location.Location
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
import kotlin.math.roundToInt

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

    val originStop by viewModel.originStop.collectAsState()
    val destStop by viewModel.destStop.collectAsState()
    val originQuery by viewModel.originQuery.collectAsState()
    val destQuery by viewModel.destQuery.collectAsState()
    val routes by viewModel.routes.collectAsState()
    val userCoordinates by viewModel.userCoordinates.collectAsState()

    // FusedLocationProviderClient for GPS
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // MapView reference
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Real-time map center coordinates for location selection
    var centerPoint by remember { mutableStateOf(GeoPoint(36.6800, 48.5100)) }

    // UI Panels
    var showAiAssistant by remember { mutableStateOf(false) }

    // Nearest bus stop or landmark to map center
    val nearestStationToCenter by remember(centerPoint, routes) {
        derivedStateOf {
            viewModel.findNearestStop(centerPoint.latitude, centerPoint.longitude)
        }
    }

    // Helper to request GPS user location and smoothly animate map camera
    val moveToUserLocation = remember(context, viewModel, mapViewRef) {
        {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        viewModel.updateUserLocation(loc.latitude, loc.longitude)
                        mapViewRef?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude), 15.5, 1000L)
                    } else {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                if (location != null) {
                                    viewModel.updateUserLocation(location.latitude, location.longitude)
                                    mapViewRef?.controller?.animateTo(GeoPoint(location.latitude, location.longitude), 15.5, 1000L)
                                }
                            }
                    }
                }
            }
        }
    }

    // Runtime Permission Request Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            moveToUserLocation()
        }
    }

    // Zoom camera to fit route bounds during ROUTE_PREVIEW
    LaunchedEffect(routePolylinePoints, uiState) {
        if (uiState == MapUiState.ROUTE_PREVIEW && routePolylinePoints.size >= 2 && mapViewRef != null) {
            try {
                val boundingBox = BoundingBox.fromGeoPoints(routePolylinePoints)
                mapViewRef?.zoomToBoundingBox(boundingBox, true, 140)
            } catch (e: Exception) {
                // Fallback center
                originGeoPoint?.let {
                    mapViewRef?.controller?.animateTo(it)
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = modifier.fillMaxSize()) {

            // ==========================================
            // 1. OSMDROID MAPVIEW (CLEAN & NO CLUTTER)
            // ==========================================
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)

                        // Zanjan Default Location
                        controller.setZoom(14.5)
                        controller.setCenter(GeoPoint(36.6800, 48.5100))

                        // Scroll & Zoom Listener to keep track of Map Center
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
                    // Remove old overlays (Strictly clean map)
                    mapView.overlays.clear()

                    // A. Draw Route Polyline (if ROUTE_PREVIEW)
                    if (routePolylinePoints.isNotEmpty()) {
                        val routePolyline = Polyline(mapView).apply {
                            outlinePaint.color = AndroidColor.parseColor("#1D4ED8") // Modern Royal Blue
                            outlinePaint.strokeWidth = 14f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND

                            setPoints(routePolylinePoints)
                        }
                        mapView.overlays.add(routePolyline)
                    }

                    // B. Origin Pin Marker (When set)
                    originGeoPoint?.let { origin ->
                        val originMarker = Marker(mapView).apply {
                            position = origin
                            title = "مبدأ: ${originStop?.name ?: "موقعیت مبدأ"}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(originMarker)
                    }

                    // C. Destination Pin Marker (When set)
                    destGeoPoint?.let { dest ->
                        val destMarker = Marker(mapView).apply {
                            position = dest
                            title = "مقصد: ${destStop?.name ?: "موقعیت مقصد"}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(destMarker)
                    }

                    // D. User Location Marker
                    val userMarker = Marker(mapView).apply {
                        position = GeoPoint(userCoordinates.first, userCoordinates.second)
                        title = "موقعیت شما"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    mapView.overlays.add(userMarker)

                    mapView.invalidate()
                }
            )

            // ==========================================
            // 2. FIXED CENTER PIN MARKER (RIDE-HAILING UX)
            // ==========================================
            if (uiState != MapUiState.ROUTE_PREVIEW) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = (-24).dp) // Tip points directly at center
                    ) {
                        // Location tooltip above pin
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = when (uiState) {
                                    MapUiState.SELECT_ORIGIN -> nearestStationToCenter?.name ?: "مبدأ را انتخاب کنید"
                                    MapUiState.SELECT_DESTINATION -> nearestStationToCenter?.name ?: "مقصد را انتخاب کنید"
                                    else -> ""
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        // Center Pin Icon
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Center Target Location Pin",
                            tint = if (uiState == MapUiState.SELECT_ORIGIN) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(46.dp)
                        )

                        // Pin Tip Shadow
                        Box(
                            modifier = Modifier
                                .size(12.dp, 4.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                    }
                }
            }

            // ==========================================
            // 3. FLOATING ACTION CONTROLS (RIGHT/LEFT)
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
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            moveToUserLocation()
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
            // 4. DYNAMIC RIDE-HAILING BOTTOM SHEET
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
                        // Drag indicator line
                        Box(
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .width(42.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFCBD5E1))
                        )

                        AnimatedContent(
                            targetState = uiState,
                            label = "BottomSheetStateTransition"
                        ) { state ->
                            when (state) {
                                // ------------------------------------------
                                // STATE 1: SELECT ORIGIN
                                // ------------------------------------------
                                MapUiState.SELECT_ORIGIN -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFD1FAE5)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.TripOrigin,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "انتخاب مبدا",
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
                                            onClick = {
                                                viewModel.confirmOrigin(centerPoint)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "تایید مبدا",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // ------------------------------------------
                                // STATE 2: SELECT DESTINATION
                                // ------------------------------------------
                                MapUiState.SELECT_DESTINATION -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Selected Origin Summary Badge
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
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFEE2E2)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(20.dp)
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
                                                onClick = {
                                                    viewModel.confirmDestination(centerPoint)
                                                },
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

                                // ------------------------------------------
                                // STATE 3: ROUTE PREVIEW
                                // ------------------------------------------
                                MapUiState.ROUTE_PREVIEW -> {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "پیش‌نمایش مسیر و اطلاعات سفر",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Route Info Cards (Distance & Duration)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Distance Card
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Straighten, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(24.dp))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text("مسافت", fontSize = 11.sp, color = Color(0xFF64748B))
                                                        val distText = routeDistanceKm?.let { String.format("%.1f کیلومتر", it) } ?: "در حال محاسبه..."
                                                        Text(distText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                                    }
                                                }
                                            }

                                            // Duration Card
                                            Card(
                                                modifier = Modifier.weight(1f),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(24.dp))
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text("زمان تقریبی", fontSize = 11.sp, color = Color(0xFF64748B))
                                                        val durText = routeDurationMin?.let { String.format("%.0f دقیقه", it) } ?: "در حال محاسبه..."
                                                        Text(durText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

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
                                                Text("در حال دریافت بهترین مسیر از OSRM...", fontSize = 12.sp, color = Color(0xFF64748B))
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
                                                    .height(52.dp),
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text("تغییر مسیر", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                            }

                                            Button(
                                                onClick = {
                                                    // Request / Confirm ride action
                                                },
                                                modifier = Modifier
                                                    .weight(2f)
                                                    .height(52.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Text("ادامه و درخواست", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
            // 5. GEMINI AI TRAVEL ASSISTANT DRAWER
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
        }
    }
}

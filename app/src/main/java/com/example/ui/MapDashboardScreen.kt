package com.example.ui

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Color as AndroidColor
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
import com.example.ui.theme.TransitTeal
import com.example.ui.theme.TransitTealLight
import com.example.viewmodel.BusViewModel
import com.example.viewmodel.ChatMessage
import kotlinx.coroutines.delay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
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

    // Bus locations & simulated countdown ETA
    var etaSeconds by remember { mutableStateOf(340) }

    // UI Panel triggers
    var showAiAssistant by remember { mutableStateOf(false) }
    var focusedField by remember { mutableStateOf<String?>(null) } // "origin" or "dest"

    // MapView instance reference for zoom and center commands
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

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
            
            // 1. NATIVE OSMDROID OPENSTREETMAP VIEW
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        
                        // Zanjan default center (36.68, 48.51) with zoom 13.5
                        controller.setZoom(13.5)
                        controller.setCenter(GeoPoint(36.6800, 48.5100))

                        // Handle map tap to pick origin/destination stations
                        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                p?.let {
                                    viewModel.onMapClick(it.latitude, it.longitude)
                                }
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        })
                        overlays.add(eventsOverlay)

                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    // Clear previous dynamic overlays (preserve click event overlay at index 0)
                    val clickOverlay = mapView.overlays.firstOrNull { it is MapEventsOverlay }
                    mapView.overlays.clear()
                    clickOverlay?.let { mapView.overlays.add(it) }

                    // A. Draw Bus Route Polyline (if selected)
                    selectedRoute?.let { route ->
                        val busPolyline = Polyline(mapView).apply {
                            outlinePaint.color = AndroidColor.parseColor("#1A73E8")
                            outlinePaint.strokeWidth = 12f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                            
                            val points = route.coordinates.map { coord ->
                                // Note: coordinates in GeoJSON format are [lng, lat]
                                GeoPoint(coord[1], coord[0])
                            }
                            setPoints(points)
                        }
                        mapView.overlays.add(busPolyline)
                    }

                    // B. Draw Walking Navigation Route Polyline (if present)
                    if (walkRouteCoordinates.isNotEmpty()) {
                        val walkPolyline = Polyline(mapView).apply {
                            outlinePaint.color = AndroidColor.parseColor("#2563EB")
                            outlinePaint.strokeWidth = 8f
                            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(16f, 16f), 0f)
                            outlinePaint.strokeCap = Paint.Cap.ROUND

                            val points = walkRouteCoordinates.map { coord ->
                                GeoPoint(coord[1], coord[0])
                            }
                            setPoints(points)
                        }
                        mapView.overlays.add(walkPolyline)
                    }

                    // C. Add Station Markers
                    val allStations = routes.flatMap { it.stops }.distinctBy { it.id }
                    allStations.forEach { station ->
                        val stationMarker = Marker(mapView).apply {
                            position = GeoPoint(station.lat, station.lng)
                            title = station.name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setOnMarkerClickListener { _, _ ->
                                viewModel.onMapClick(station.lat, station.lng)
                                showInfoWindow()
                                true
                            }
                        }
                        mapView.overlays.add(stationMarker)
                    }

                    // D. Origin (Boarding) Stop Marker
                    originStop?.let { origin ->
                        val originMarker = Marker(mapView).apply {
                            position = GeoPoint(origin.lat, origin.lng)
                            title = "مبدأ: ${origin.name}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            showInfoWindow()
                        }
                        mapView.overlays.add(originMarker)
                    }

                    // E. Destination (Deboarding) Stop Marker
                    destStop?.let { dest ->
                        val destMarker = Marker(mapView).apply {
                            position = GeoPoint(dest.lat, dest.lng)
                            title = "مقصد: ${dest.name}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            showInfoWindow()
                        }
                        mapView.overlays.add(destMarker)
                    }

                    // F. User Location Marker
                    val userMarker = Marker(mapView).apply {
                        position = GeoPoint(userCoordinates.first, userCoordinates.second)
                        title = "موقعیت شما"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    mapView.overlays.add(userMarker)

                    // G. Live Bus Markers
                    liveLocations.forEach { bus ->
                        val busMarker = Marker(mapView).apply {
                            position = GeoPoint(bus.lat, bus.lng)
                            title = "اتوبوس 🚌 (خط ${bus.routeId})"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        mapView.overlays.add(busMarker)
                    }

                    mapView.invalidate()
                }
            )

            // 2. FLOATING TOP SEARCH PANEL
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
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B82F6))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextField(
                                value = originQuery,
                                onValueChange = {
                                    viewModel.setOrigin(it)
                                    focusedField = "origin"
                                },
                                placeholder = { Text("مبدا: موقعیت فعلی یا انتخاب ایستگاه", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
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
                            Spacer(modifier = Modifier.width(8.dp))
                            TextField(
                                value = destQuery,
                                onValueChange = {
                                    viewModel.setDestination(it)
                                    focusedField = "dest"
                                },
                                placeholder = { Text("مقصد کجا می‌روید؟ (مثلاً: سبزه میدان)", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
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

                        // Autocomplete suggestions based on focused fields
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                                items(suggestions) { stop ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (focusedField == "origin") {
                                                    viewModel.setOrigin(stop.name)
                                                } else {
                                                    viewModel.setDestination(stop.name)
                                                }
                                                focusedField = null
                                            }
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = TransitTeal, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = stop.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. FLOAT TOGGLE CONTROL BUTTONS (Zoom, Locate Me, AI Assistant, Logout)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
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
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
                }

                // Re-center Map button (Locate Me)
                OutlinedIconButton(
                    onClick = {
                        mapViewRef?.controller?.animateTo(GeoPoint(userCoordinates.first, userCoordinates.second))
                        mapViewRef?.controller?.setZoom(15.0)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFF334155))
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Re-center Map", modifier = Modifier.size(20.dp))
                }

                // AI Assistant toggle
                OutlinedIconButton(
                    onClick = { showAiAssistant = !showAiAssistant },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFF1A73E8))
                ) {
                    Icon(
                        imageVector = if (showAiAssistant) Icons.Default.Map else Icons.Default.ChatBubble,
                        contentDescription = "AI Companion",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Logout button
                OutlinedIconButton(
                    onClick = { viewModel.logout() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Logout", modifier = Modifier.size(20.dp))
                }
            }

            // 4. TRANSIT INFORMATION PANEL (BOTTOM SHEET - APPEARS ON ROUTE ACTIVE)
            AnimatedVisibility(
                visible = isTripActive && !showAiAssistant,
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
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
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
                                .width(48.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedRoute?.name ?: "مسیر مشخص شده",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "شماره خط: " + (selectedRoute?.number ?: "") + " • زنجان",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = Color(0xFF1A73E8), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                val distText = walkDistance?.let { 
                                    if (it >= 1000) "${String.format("%.1f", it / 1000.0)} کیلومتر" else "${it.roundToInt()} متر"
                                } ?: "۴۵۰ متر"
                                Text(text = "فاصله پیاده‌روی", fontSize = 11.sp, color = Color(0xFF64748B))
                                Text(text = distText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                val durationText = walkDuration?.let { 
                                    "${(it / 60).roundToInt()} دقیقه"
                                } ?: "۴ دقیقه"
                                Text(text = "زمان تقریبی راه", fontSize = 11.sp, color = Color(0xFF64748B))
                                Text(text = durationText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "ایستگاه سوار شدن", fontSize = 11.sp, color = Color(0xFF64748B))
                                Text(text = originStop?.name ?: "سبزه میدان", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.clearTrip() },
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("پایان سفر", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Button(
                                onClick = { /* Start tracking / guidance */ },
                                modifier = Modifier
                                    .weight(2f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("شروع سفر", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // 5. SMART TRAVEL ASSISTANT (GEMINI AI CHAT DRAWER)
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
                            Icon(Icons.Default.Close, contentDescription = "Close")
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
                                            Text("در حال نوشتن...", fontSize = 12.sp)
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
                            placeholder = { Text("مثال: چطور از سبزه میدان برم بیمارستان موسوی؟", fontSize = 12.sp) },
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
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

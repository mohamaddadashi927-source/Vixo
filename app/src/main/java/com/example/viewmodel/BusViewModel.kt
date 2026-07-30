package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.BusLocation
import com.example.model.BusRoute
import com.example.model.BusStop
import com.example.model.ElahiehPreseededData
import com.example.model.LiveBus
import com.example.model.TransitPlan
import com.example.network.Content
import com.example.network.FirebaseService
import com.example.network.GeminiService
import com.example.network.NominatimResult
import com.example.network.NominatimRetrofitClient
import com.example.network.OSRMRetrofitClient
import com.example.network.Part
import com.example.repository.BusRepository
import com.example.util.RouteEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray

import org.osmdroid.util.GeoPoint

enum class MapUiState {
    SELECT_ORIGIN,
    SELECT_DESTINATION,
    ROUTE_PREVIEW
}

enum class AppRole {
    PASSENGER,  // مسافر
    DRIVER,     // راننده
    SUPERVISOR  // ناظر
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

class BusViewModel : ViewModel() {
    private val firebaseService = FirebaseService()
    private val geminiService = GeminiService()
    private val busRepository = BusRepository(firebaseService)

    // --- Transit Engine Plan ---
    private val _transitPlan = MutableStateFlow<TransitPlan?>(null)
    val transitPlan: StateFlow<TransitPlan?> = _transitPlan.asStateFlow()

    private val _liveBuses = MutableStateFlow<List<LiveBus>>(emptyList())
    val liveBuses: StateFlow<List<LiveBus>> = _liveBuses.asStateFlow()

    // --- Nominatim Geocoding Search ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<NominatimResult>>(emptyList())
    val searchResults: StateFlow<List<NominatimResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    // --- Ride-Hailing State Machine ---
    private val _uiState = MutableStateFlow(MapUiState.SELECT_ORIGIN)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val _originGeoPoint = MutableStateFlow<GeoPoint?>(null)
    val originGeoPoint: StateFlow<GeoPoint?> = _originGeoPoint.asStateFlow()

    private val _destGeoPoint = MutableStateFlow<GeoPoint?>(null)
    val destGeoPoint: StateFlow<GeoPoint?> = _destGeoPoint.asStateFlow()

    private val _routeDistanceKm = MutableStateFlow<Double?>(null)
    val routeDistanceKm: StateFlow<Double?> = _routeDistanceKm.asStateFlow()

    private val _routeDurationMin = MutableStateFlow<Double?>(null)
    val routeDurationMin: StateFlow<Double?> = _routeDurationMin.asStateFlow()

    private val _routePolylinePoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val routePolylinePoints: StateFlow<List<GeoPoint>> = _routePolylinePoints.asStateFlow()

    private val _isRouteLoading = MutableStateFlow(false)
    val isRouteLoading: StateFlow<Boolean> = _isRouteLoading.asStateFlow()

    // --- Authentication States ---
    private val _authState = MutableStateFlow<AuthState>(
        if (firebaseService.isUserLoggedIn()) AuthState.Authenticated else AuthState.Unauthenticated
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val userEmail: String
        get() = firebaseService.getUserEmail()

    // --- Transit States ---
    private val _routes = MutableStateFlow<List<BusRoute>>(ElahiehPreseededData.routes)
    val routes: StateFlow<List<BusRoute>> = _routes.asStateFlow()

    private val _liveBusLocations = MutableStateFlow<List<BusLocation>>(emptyList())
    val liveBusLocations: StateFlow<List<BusLocation>> = _liveBusLocations.asStateFlow()

    // --- Search & Travel States ---
    private val _originQuery = MutableStateFlow("")
    val originQuery: StateFlow<String> = _originQuery.asStateFlow()

    private val _destQuery = MutableStateFlow("")
    val destQuery: StateFlow<String> = _destQuery.asStateFlow()

    private val _originStop = MutableStateFlow<BusStop?>(null)
    val originStop: StateFlow<BusStop?> = _originStop.asStateFlow()

    private val _destStop = MutableStateFlow<BusStop?>(null)
    val destStop: StateFlow<BusStop?> = _destStop.asStateFlow()

    private val _selectedRoute = MutableStateFlow<BusRoute?>(null)
    val selectedRoute: StateFlow<BusRoute?> = _selectedRoute.asStateFlow()

    private val _isTripActive = MutableStateFlow(false)
    val isTripActive: StateFlow<Boolean> = _isTripActive.asStateFlow()

    // --- Walking Navigation (OSRM) States ---
    private val _walkDistanceMeters = MutableStateFlow<Double?>(null)
    val walkDistanceMeters: StateFlow<Double?> = _walkDistanceMeters.asStateFlow()

    private val _walkDurationSeconds = MutableStateFlow<Double?>(null)
    val walkDurationSeconds: StateFlow<Double?> = _walkDurationSeconds.asStateFlow()

    private val _walkRouteCoordinates = MutableStateFlow<List<List<Double>>>(emptyList())
    val walkRouteCoordinates: StateFlow<List<List<Double>>> = _walkRouteCoordinates.asStateFlow()

    // User coordinates in Zanjan area
    private val _userCoordinates = MutableStateFlow(Pair(36.6800, 48.4700))
    val userCoordinates: StateFlow<Pair<Double, Double>> = _userCoordinates.asStateFlow()

    // --- Gemini AI Travel Assistant States ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage("سلام! من سفربانم، دستیار هوش مصنوعی سفر شما. چطور می‌توانم در مورد مسیرهای اتوبوس و جاهای دیدنی زنجان کمکتان کنم؟", false))
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // --- Map Actions Dispatcher ---
    private val _isMapLoaded = MutableStateFlow(false)
    val isMapLoaded: StateFlow<Boolean> = _isMapLoaded.asStateFlow()

    // --- Active App Role (Passenger, Driver, Supervisor) ---
    private val _activeRole = MutableStateFlow(AppRole.PASSENGER)
    val activeRole: StateFlow<AppRole> = _activeRole.asStateFlow()

    fun setActiveRole(role: AppRole) {
        _activeRole.value = role
    }

    // --- Line-based Bus Lines State ---
    private val _busLines = MutableStateFlow<List<com.example.model.BusLine>>(com.example.data.ZanjanBusData.allLines)
    val busLines: StateFlow<List<com.example.model.BusLine>> = _busLines.asStateFlow()

    // --- Driver Shift States ---
    private val _driverLineId = MutableStateFlow<String>("line_elahieh_phase1_to_sabzeh")
    val driverLineId: StateFlow<String> = _driverLineId.asStateFlow()

    private val _driverBusId = MutableStateFlow<String>("102")
    val driverBusId: StateFlow<String> = _driverBusId.asStateFlow()

    private val _driverId = MutableStateFlow<String>("driver_01")
    val driverId: StateFlow<String> = _driverId.asStateFlow()

    private val _isDriverOnShift = MutableStateFlow<Boolean>(false)
    val isDriverOnShift: StateFlow<Boolean> = _isDriverOnShift.asStateFlow()

    private var driverShiftJob: Job? = null

    fun setDriverLine(lineId: String) {
        _driverLineId.value = lineId
    }

    fun setDriverBusId(busId: String) {
        _driverBusId.value = busId
    }

    fun setDriverId(id: String) {
        _driverId.value = id
    }

    fun startDriverShift() {
        _isDriverOnShift.value = true
        val lineId = _driverLineId.value
        val busId = _driverBusId.value.ifBlank { "101" }
        val dId = _driverId.value.ifBlank { "driver_01" }

        driverShiftJob?.cancel()
        driverShiftJob = viewModelScope.launch {
            val line = _busLines.value.find { firebaseService.cleanLineId(it.id) == firebaseService.cleanLineId(lineId) }
                ?: com.example.data.ZanjanBusData.allLines.first()
            val points = if (line.polyline.isNotEmpty()) line.polyline else listOf(GeoPoint(36.68, 48.47))
            var idx = 0

            while (_isDriverOnShift.value) {
                val pt = points[idx % points.size]

                busRepository.updateDriverLocationOnShift(
                    driverId = dId,
                    busId = busId,
                    lineId = lineId,
                    lat = pt.latitude,
                    lng = pt.longitude,
                    speed = 28.5,
                    heading = 90.0,
                    isActive = true
                )

                idx++
                delay(3000L)
            }
        }
    }

    fun endDriverShift() {
        _isDriverOnShift.value = false
        driverShiftJob?.cancel()
        val lineId = _driverLineId.value
        val busId = _driverBusId.value.ifBlank { "101" }
        val dId = _driverId.value.ifBlank { "driver_01" }

        viewModelScope.launch {
            busRepository.updateDriverLocationOnShift(
                driverId = dId,
                busId = busId,
                lineId = lineId,
                lat = 0.0,
                lng = 0.0,
                speed = 0.0,
                heading = 0.0,
                isActive = false
            )
        }
    }

    // --- Supervisor States & Methods ---
    private val _supervisorSelectedLineId = MutableStateFlow<String?>(null)
    val supervisorSelectedLineId: StateFlow<String?> = _supervisorSelectedLineId.asStateFlow()

    private val _supervisorActiveBuses = MutableStateFlow<List<LiveBus>>(emptyList())
    val supervisorActiveBuses: StateFlow<List<LiveBus>> = _supervisorActiveBuses.asStateFlow()

    private var supervisorBusJob: Job? = null

    fun selectSupervisorLine(lineId: String?) {
        _supervisorSelectedLineId.value = lineId
        supervisorBusJob?.cancel()
        if (lineId.isNullOrBlank()) {
            _supervisorActiveBuses.value = emptyList()
            return
        }
        supervisorBusJob = viewModelScope.launch {
            busRepository.observeActiveBusesForLine(lineId).collect { buses ->
                _supervisorActiveBuses.value = buses
            }
        }
    }

    // --- Passenger Selected Line State & Method ---
    private val _passengerSelectedLineId = MutableStateFlow<String?>(null)
    val passengerSelectedLineId: StateFlow<String?> = _passengerSelectedLineId.asStateFlow()

    private var passengerBusJob: Job? = null

    fun selectPassengerLine(lineId: String?) {
        _passengerSelectedLineId.value = lineId
        passengerBusJob?.cancel()
        if (lineId.isNullOrBlank()) return
        passengerBusJob = viewModelScope.launch {
            busRepository.observeActiveBusesForLine(lineId).collect { buses ->
                _liveBuses.value = buses
            }
        }
    }

    init {
        // Safety fallback timer to ensure map overlay reveals within 600ms
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            if (!_isMapLoaded.value) {
                onMapLoaded()
            }
        }
        
        // Listen to routes from Firebase line observations
        viewModelScope.launch {
            busRepository.getAllLinesFromFirebase()
        }

        viewModelScope.launch {
            busRepository.observeBusLines()
                .catch { e -> _routes.value = ElahiehPreseededData.routes }
                .collect { lines ->
                    if (lines.isNotEmpty()) {
                        _busLines.value = lines
                        _routes.value = lines.map { line ->
                            BusRoute(
                                id = line.id,
                                name = line.name,
                                number = line.number,
                                stops = line.stations.map { st -> BusStop(st.id, st.name, st.lat, st.lng) },
                                coordinates = line.polyline.map { pt -> listOf(pt.longitude, pt.latitude) }
                            )
                        }
                    } else {
                        _busLines.value = com.example.data.ZanjanBusData.allLines
                        _routes.value = ElahiehPreseededData.routes
                    }
                }
        }

        // Listen to live bus positions from Firebase with line matching filter
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                busRepository.observeLiveBuses(),
                _transitPlan,
                _selectedRoute
            ) { buses, plan, route ->
                val selectedLineId = plan?.busLine?.id ?: route?.id
                if (selectedLineId.isNullOrBlank()) {
                    buses
                } else {
                    buses.filter { isLineMatch(it.lineId, selectedLineId) }
                }
            }.catch { /* Handle error gracefully */ }
            .collect { filteredBuses ->
                _liveBuses.value = filteredBuses

                // Real-time recalculation of transit plan matched bus and ETA
                val currentPlan = _transitPlan.value
                if (currentPlan != null) {
                    val (updatedBus, etaInfo) = RouteEngine.matchLiveBusAndEta(
                        busLine = currentPlan.busLine,
                        originStation = currentPlan.originStation,
                        liveBuses = filteredBuses,
                        walkTimeToStationMin = currentPlan.walkToStation.durationMin
                    )
                    _transitPlan.value = currentPlan.copy(
                        matchedBus = updatedBus,
                        busEtaMin = etaInfo.etaMinutes,
                        busEtaText = etaInfo.displayText
                    )
                }
            }
        }
    }

    private fun isLineMatch(busLineId: String, selectedLineId: String?): Boolean {
        if (selectedLineId.isNullOrBlank()) return true
        val cleanBus = busLineId.trim()
        val cleanSelected = selectedLineId.trim()
        if (cleanBus.isEmpty()) return false
        if (cleanBus == cleanSelected) return true
        val normBus = cleanBus.removeSuffix("_forward").removeSuffix("_backward")
        val normSelected = cleanSelected.removeSuffix("_forward").removeSuffix("_backward")
        return normBus == normSelected
    }

    // --- Authentication Methods ---
    fun loginAsGuest() {
        _authState.value = AuthState.Loading
        firebaseService.loginAsGuest {
            _authState.value = AuthState.Authenticated
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("لطفاً تمامی فیلدها را پر کنید.")
            return
        }
        _authState.value = AuthState.Loading
        firebaseService.loginUser(email, password, {
            _authState.value = AuthState.Authenticated
        }, { error ->
            _authState.value = AuthState.Error(error)
        })
    }

    fun register(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("لطفاً تمامی فیلدها را پر کنید.")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("رمز عبور باید حداقل ۶ کاراکتر باشد.")
            return
        }
        _authState.value = AuthState.Loading
        firebaseService.registerUser(email, password, {
            _authState.value = AuthState.Authenticated
        }, { error ->
            _authState.value = AuthState.Error(error)
        })
    }

    fun logout() {
        firebaseService.logout()
        _authState.value = AuthState.Unauthenticated
        resetSelection()
    }

    // --- Geocoding Search Methods ---
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // Debounce query typing
            _isSearching.value = true
            try {
                val results = NominatimRetrofitClient.api.searchLocations(query.trim())
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
        searchJob?.cancel()
    }

    fun selectSearchResult(result: NominatimResult, onLocationSelected: (GeoPoint) -> Unit) {
        val lat = result.lat?.toDoubleOrNull()
        val lon = result.lon?.toDoubleOrNull()
        if (lat != null && lon != null) {
            val geoPoint = GeoPoint(lat, lon)
            onLocationSelected(geoPoint)
            
            val placeTitle = result.name ?: result.displayName?.split(",")?.firstOrNull() ?: "موقعیت انتخابی"
            
            if (_uiState.value == MapUiState.SELECT_ORIGIN) {
                _originGeoPoint.value = geoPoint
                _originQuery.value = placeTitle
                setOriginFromCoords(lat, lon)
                _uiState.value = MapUiState.SELECT_DESTINATION
            } else if (_uiState.value == MapUiState.SELECT_DESTINATION) {
                _destGeoPoint.value = geoPoint
                _destQuery.value = placeTitle
                setDestinationFromCoords(lat, lon)
                _uiState.value = MapUiState.ROUTE_PREVIEW
                
                val origin = _originGeoPoint.value
                if (origin != null) {
                    calculateBusTransitRoute(origin, geoPoint)
                }
            }
            
            clearSearch()
        }
    }

    // --- Ride-Hailing & Bus Navigation State Machine Logic ---
    fun setUiState(state: MapUiState) {
        _uiState.value = state
    }

    fun confirmOrigin(center: GeoPoint) {
        _originGeoPoint.value = center
        setOriginFromCoords(center.latitude, center.longitude)
        _uiState.value = MapUiState.SELECT_DESTINATION
    }

    fun confirmDestination(center: GeoPoint) {
        _destGeoPoint.value = center
        setDestinationFromCoords(center.latitude, center.longitude)
        _uiState.value = MapUiState.ROUTE_PREVIEW
        
        val origin = _originGeoPoint.value
        if (origin != null) {
            calculateBusTransitRoute(origin, center)
        }
    }

    fun resetSelection() {
        _originGeoPoint.value = null
        _destGeoPoint.value = null
        _transitPlan.value = null
        _routePolylinePoints.value = emptyList()
        _routeDistanceKm.value = null
        _routeDurationMin.value = null
        _uiState.value = MapUiState.SELECT_ORIGIN
        clearTrip()
    }

    fun calculateBusTransitRoute(start: GeoPoint, end: GeoPoint) {
        viewModelScope.launch {
            _isRouteLoading.value = true
            try {
                val plan = busRepository.computeTransitRoute(start, end, _liveBuses.value)
                if (plan != null) {
                    _transitPlan.value = plan
                    _routeDistanceKm.value = plan.totalDistanceKm
                    _routeDurationMin.value = plan.totalDurationMin
                    _routePolylinePoints.value = plan.walkToStation.points + plan.busRide.points + plan.walkToDest.points
                } else {
                    fetchOSRMRoute(start, end)
                }
            } catch (e: Exception) {
                fetchOSRMRoute(start, end)
            } finally {
                _isRouteLoading.value = false
            }
        }
    }

    fun fetchOSRMRoute(start: GeoPoint, end: GeoPoint) {
        viewModelScope.launch {
            _isRouteLoading.value = true
            val directDistKm = RouteEngine.haversineDistanceKm(start, end)
            try {
                val coordsParam = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
                val response = OSRMRetrofitClient.api.getWalkingRoute(coordsParam)
                val route = response.routes?.firstOrNull()

                if (route != null && route.geometry?.coordinates != null && route.geometry.coordinates.isNotEmpty()) {
                    val distMeters = route.distance ?: (directDistKm * 1000.0)
                    val distKm = distMeters / 1000.0
                    val durSeconds = route.duration ?: (distKm * 13.3 * 60)

                    val polyPoints = route.geometry.coordinates.map { GeoPoint(it[1], it[0]) }
                    val isLoop = RouteEngine.detectLoopInPolyline(polyPoints)
                    val isTooLong = distKm > 2.0 * directDistKm

                    if (isLoop || isTooLong) {
                        applySmartOSRMFallback(start, end, directDistKm)
                    } else {
                        _routeDistanceKm.value = distKm
                        _routeDurationMin.value = durSeconds / 60.0
                        _routePolylinePoints.value = polyPoints
                    }
                } else {
                    applySmartOSRMFallback(start, end, directDistKm)
                }
            } catch (e: Exception) {
                applySmartOSRMFallback(start, end, directDistKm)
            } finally {
                _isRouteLoading.value = false
            }
        }
    }

    private fun applySmartOSRMFallback(start: GeoPoint, end: GeoPoint, directDistKm: Double) {
        if (directDistKm <= 0.3) {
            _routeDistanceKm.value = directDistKm
            _routeDurationMin.value = (directDistKm * 1000.0 / 80.0) / 60.0
            _routePolylinePoints.value = listOf(start, end)
        } else {
            _routePolylinePoints.value = emptyList()
            _routeDistanceKm.value = null
            _routeDurationMin.value = null
        }
    }

    // --- Bus Selection & Pedestrian Routing ---
    fun setOrigin(query: String) {
        _originQuery.value = query
        val stop = findStopByName(query)
        _originStop.value = stop
        evaluateTravelRouting()
    }

    fun setDestination(query: String) {
        _destQuery.value = query
        val stop = findStopByName(query)
        _destStop.value = stop
        evaluateTravelRouting()
    }

    fun findNearestStop(lat: Double, lng: Double): BusStop? {
        val allStops = _routes.value.flatMap { it.stops }.distinctBy { it.id }
        return allStops.minByOrNull { st ->
            val dLat = st.lat - lat
            val dLng = st.lng - lng
            dLat * dLat + dLng * dLng
        }
    }

    fun setOriginFromCoords(lat: Double, lng: Double) {
        val nearest = findNearestStop(lat, lng)
        val dThreshold = 0.01
        val stop = if (nearest != null) {
            val dLat = nearest.lat - lat
            val dLng = nearest.lng - lng
            if (dLat * dLat + dLng * dLng < dThreshold * dThreshold) {
                nearest
            } else {
                BusStop("custom_origin_${System.currentTimeMillis()}", "مبدأ انتخابی", lat, lng)
            }
        } else {
            BusStop("custom_origin_${System.currentTimeMillis()}", "مبدأ انتخابی", lat, lng)
        }
        
        _originStop.value = stop
        _originQuery.value = stop.name
        evaluateTravelRouting()
    }

    fun setDestinationFromCoords(lat: Double, lng: Double) {
        val nearest = findNearestStop(lat, lng)
        val dThreshold = 0.01
        val stop = if (nearest != null) {
            val dLat = nearest.lat - lat
            val dLng = nearest.lng - lng
            if (dLat * dLat + dLng * dLng < dThreshold * dThreshold) {
                nearest
            } else {
                BusStop("custom_dest_${System.currentTimeMillis()}", "مقصد انتخابی", lat, lng)
            }
        } else {
            BusStop("custom_dest_${System.currentTimeMillis()}", "مقصد انتخابی", lat, lng)
        }
        
        _destStop.value = stop
        _destQuery.value = stop.name
        evaluateTravelRouting()
    }

    fun updateUserLocation(lat: Double, lng: Double) {
        _userCoordinates.value = Pair(lat, lng)
    }

    private fun findStopByName(name: String): BusStop? {
        return _routes.value.flatMap { it.stops }.firstOrNull { it.name.contains(name, ignoreCase = true) }
    }

    // Finds the best bus route that connects the chosen origin and destination stops
    private fun evaluateTravelRouting() {
        val origin = _originStop.value
        val dest = _destStop.value

        if (origin != null && dest != null) {
            // Search for a route that contains both stops
            val route = _routes.value.firstOrNull { r ->
                val hasOrigin = r.stops.any { it.id == origin.id }
                val hasDest = r.stops.any { it.id == dest.id }
                hasOrigin && hasDest
            }
            
            if (route != null) {
                _selectedRoute.value = route
                _isTripActive.value = true
                
                // Fetch the walking route from user location to the boarding stop using OSRM
                fetchWalkingRoute(userCoordinates.value.first, userCoordinates.value.second, origin.lat, origin.lng)
            } else {
                // If no direct route, default to first route for visualization
                val defaultRoute = _routes.value.firstOrNull()
                _selectedRoute.value = defaultRoute
                _isTripActive.value = true
            }
        }
    }

    private fun fetchWalkingRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double) {
        viewModelScope.launch {
            val start = GeoPoint(startLat, startLng)
            val end = GeoPoint(endLat, endLng)
            val directDistKm = RouteEngine.haversineDistanceKm(start, end)

            try {
                val coordsParam = "$startLng,$startLat;$endLng,$endLat"
                val response = OSRMRetrofitClient.api.getWalkingRoute(coordsParam)
                val route = response.routes?.firstOrNull()

                if (route != null && route.geometry?.coordinates != null && route.geometry.coordinates.isNotEmpty()) {
                    val distMeters = route.distance ?: (directDistKm * 1000.0)
                    val distKm = distMeters / 1000.0
                    val durSeconds = route.duration ?: (distKm * 13.3 * 60)

                    val polyPoints = route.geometry.coordinates.map { GeoPoint(it[1], it[0]) }
                    val isLoop = RouteEngine.detectLoopInPolyline(polyPoints)
                    val isTooLong = distKm > 2.0 * directDistKm

                    if (isLoop || isTooLong) {
                        applySmartWalkingFallback(start, end, directDistKm)
                    } else {
                        _walkDistanceMeters.value = distMeters
                        _walkDurationSeconds.value = durSeconds
                        _walkRouteCoordinates.value = route.geometry.coordinates
                    }
                } else {
                    applySmartWalkingFallback(start, end, directDistKm)
                }
            } catch (e: Exception) {
                applySmartWalkingFallback(start, end, directDistKm)
            }
        }
    }

    private fun applySmartWalkingFallback(start: GeoPoint, end: GeoPoint, directDistKm: Double) {
        if (directDistKm <= 0.3) {
            val distMeters = directDistKm * 1000.0
            _walkDistanceMeters.value = distMeters
            _walkDurationSeconds.value = (distMeters / 80.0) * 60.0
            _walkRouteCoordinates.value = listOf(
                listOf(start.longitude, start.latitude),
                listOf(end.longitude, end.latitude)
            )
        } else {
            _walkDistanceMeters.value = null
            _walkDurationSeconds.value = null
            _walkRouteCoordinates.value = emptyList()
        }
    }

    fun clearTrip() {
        _isTripActive.value = false
        _originQuery.value = ""
        _destQuery.value = ""
        _originStop.value = null
        _destStop.value = null
        _selectedRoute.value = null
        _walkDistanceMeters.value = null
        _walkDurationSeconds.value = null
        _walkRouteCoordinates.value = emptyList()
    }

    // --- Gemini AI Conversation ---
    fun sendAiMessage(prompt: String) {
        if (prompt.isBlank()) return
        
        // Append user message
        val currentChat = _chatMessages.value.toMutableList()
        currentChat.add(ChatMessage(prompt, true))
        _chatMessages.value = currentChat
        _isAiLoading.value = true

        viewModelScope.launch {
            // Translate chat history to Gemini Content structure
            val history = currentChat.dropLast(1).map { msg ->
                Content(parts = listOf(Part(text = msg.text)))
            }

            val reply = geminiService.getTravelAdvice(prompt, history)
            
            val updatedChat = _chatMessages.value.toMutableList()
            updatedChat.add(ChatMessage(reply, false))
            _chatMessages.value = updatedChat
            _isAiLoading.value = false
        }
    }

    fun onMapClick(lat: Double, lng: Double) {
        // Find closest station to user click
        val allStops = _routes.value.flatMap { it.stops }
        val closest = allStops.minByOrNull { st ->
            val dLat = st.lat - lat
            val dLng = st.lng - lng
            dLat * dLat + dLng * dLng
        }
        
        if (closest != null) {
            if (_originQuery.value.isEmpty()) {
                setOrigin(closest.name)
            } else if (_destQuery.value.isEmpty() && closest.name != _originQuery.value) {
                setDestination(closest.name)
            }
        }
    }

    fun onMapLoaded() {
        _isMapLoaded.value = true
        if (_originStop.value != null || _destStop.value != null) {
            evaluateTravelRouting()
        }
    }
}

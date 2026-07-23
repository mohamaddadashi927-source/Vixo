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

    // Simulated user coordinates in Zanjan
    private val _userCoordinates = MutableStateFlow(Pair(36.6800, 48.5100))
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

    private val _mapCommands = MutableSharedFlow<String>(replay = 5)
    val mapCommands: SharedFlow<String> = _mapCommands.asSharedFlow()

    init {
        // Start live simulator
        firebaseService.startLiveSimulation()

        // Safety fallback timer to ensure map overlay reveals within 600ms
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            if (!_isMapLoaded.value) {
                onMapLoaded()
            }
        }
        
        // Listen to routes
        viewModelScope.launch {
            firebaseService.observeBusRoutes()
                .catch { e -> _routes.value = ElahiehPreseededData.routes }
                .collect { list -> _routes.value = list }
        }

        // Listen to live bus positions
        viewModelScope.launch {
            firebaseService.observeBusLocations()
                .catch { /* Handle */ }
                .collect { locations -> 
                    _liveBusLocations.value = locations
                }
        }
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
            try {
                val coordsParam = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
                val response = OSRMRetrofitClient.api.getDrivingRoute(coordsParam)
                val route = response.routes?.firstOrNull()
                
                if (route != null) {
                    val distMeters = route.distance ?: 0.0
                    val durSeconds = route.duration ?: 0.0
                    
                    _routeDistanceKm.value = distMeters / 1000.0
                    _routeDurationMin.value = durSeconds / 60.0
                    
                    val pathCoords = route.geometry?.coordinates
                    if (pathCoords != null) {
                        _routePolylinePoints.value = pathCoords.map { coord ->
                            GeoPoint(coord[1], coord[0])
                        }
                    } else {
                        _routePolylinePoints.value = listOf(start, end)
                    }
                } else {
                    useFallbackRoute(start, end)
                }
            } catch (e: Exception) {
                useFallbackRoute(start, end)
            } finally {
                _isRouteLoading.value = false
            }
        }
    }

    private fun useFallbackRoute(start: GeoPoint, end: GeoPoint) {
        _routePolylinePoints.value = listOf(start, end)
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLng = Math.toRadians(end.longitude - start.longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(start.latitude)) * Math.cos(Math.toRadians(end.latitude)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distKm = 6371.0 * c
        _routeDistanceKm.value = distKm
        _routeDurationMin.value = (distKm / 30.0) * 60.0
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
                
                // Draw route on Map
                val coordsJson = JSONArray(route.coordinates).toString()
                dispatchMapCommand("javascript:drawBusRoute('$coordsJson')")
                dispatchMapCommand("javascript:setStops(${origin.lat}, ${origin.lng}, '${origin.name}', ${dest.lat}, ${dest.lng}, '${dest.name}')")
                
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
            try {
                val coordsParam = "$startLng,$startLat;$endLng,$endLat"
                val response = OSRMRetrofitClient.api.getWalkingRoute(coordsParam)
                val route = response.routes?.firstOrNull()
                
                if (route != null) {
                    _walkDistanceMeters.value = route.distance
                    _walkDurationSeconds.value = route.duration
                    
                    val pathCoords = route.geometry?.coordinates
                    if (pathCoords != null) {
                        _walkRouteCoordinates.value = pathCoords
                        val walkJson = JSONArray(pathCoords).toString()
                        dispatchMapCommand("javascript:drawWalkRoute('$walkJson')")
                    }
                }
            } catch (e: Exception) {
                // Fallback direct path draw on failure or offline
                val fallbackCoords = listOf(listOf(startLng, startLat), listOf(endLng, endLat))
                _walkRouteCoordinates.value = fallbackCoords
                _walkDistanceMeters.value = 450.0 // Mock fallback
                _walkDurationSeconds.value = 270.0 // Mock fallback
                dispatchMapCommand("javascript:drawWalkRoute('${JSONArray(fallbackCoords)}')")
            }
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
        dispatchMapCommand("javascript:clearRoutes()")
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

    // --- Map Bridge Helper ---
    private fun dispatchMapCommand(js: String) {
        viewModelScope.launch {
            _mapCommands.emit(js)
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

    override fun onCleared() {
        super.onCleared()
        firebaseService.stopLiveSimulation()
    }
}

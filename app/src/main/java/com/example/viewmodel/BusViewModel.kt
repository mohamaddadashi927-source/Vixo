package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.BusLocation
import com.example.model.BusRoute
import com.example.model.BusStop
import com.example.model.ElahiehPreseededData
import com.example.network.Content
import com.example.network.FirebaseService
import com.example.network.GeminiService
import com.example.network.OSRMRetrofitClient
import com.example.network.Part
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray

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

    // Simulated user coordinates for walking route (Elahieh Mashhad)
    private val _userCoordinates = MutableStateFlow(Pair(36.3660, 59.4850))
    val userCoordinates: StateFlow<Pair<Double, Double>> = _userCoordinates.asStateFlow()

    // --- Gemini AI Travel Assistant States ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage("سلام! من سفربانم، دستیار هوش مصنوعی سفر شما. چطور می‌توانم در مورد مسیرهای اتوبوس الهیه یا جاهای دیدنی مشهد کمکتان کنم؟", false))
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
                    // Dispatch bus location updates to the WebView map
                    locations.forEach { loc ->
                        if (_selectedRoute.value == null || loc.routeId == _selectedRoute.value?.id) {
                            dispatchMapCommand("javascript:updateBusLocation(${loc.lat}, ${loc.lng})")
                        }
                    }
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
        clearTrip()
    }

    // --- Bus Selection & Pedestrian Routing ---
    fun setOrigin(query: String) {
        _originQuery.value = query
        val stop = findStopByName(query)
        _originStop.value = stop
        if (stop != null) {
            dispatchMapCommand("javascript:setCenter(${stop.lat}, ${stop.lng}, 14.5)")
        }
        evaluateTravelRouting()
    }

    fun setDestination(query: String) {
        _destQuery.value = query
        val stop = findStopByName(query)
        _destStop.value = stop
        if (stop != null) {
            dispatchMapCommand("javascript:setCenter(${stop.lat}, ${stop.lng}, 14.5)")
        }
        evaluateTravelRouting()
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
        // Push user current location to map
        val userLoc = _userCoordinates.value
        dispatchMapCommand("javascript:updateUserLocation(${userLoc.first}, ${userLoc.second})")
        dispatchMapCommand("javascript:setCenter(${userLoc.first}, ${userLoc.second}, 14.0)")
        
        // Show general station markers along the routes
        val stations = _routes.value.flatMap { it.stops }.distinctBy { it.id }
        val stationsJson = JSONArray().apply {
            stations.forEach { st ->
                put(org.json.JSONObject().apply {
                    put("name", st.name)
                    put("lat", st.lat)
                    put("lng", st.lng)
                })
            }
        }.toString()
        dispatchMapCommand("javascript:showAllStations('$stationsJson')")

        // Push live bus positions
        _liveBusLocations.value.forEach { loc ->
            dispatchMapCommand("javascript:updateBusLocation(${loc.lat}, ${loc.lng})")
        }

        // Re-evaluate travel routing if origin or destination is already set
        if (_originStop.value != null || _destStop.value != null) {
            evaluateTravelRouting()
        }
    }

    override fun onCleared() {
        super.onCleared()
        firebaseService.stopLiveSimulation()
    }
}

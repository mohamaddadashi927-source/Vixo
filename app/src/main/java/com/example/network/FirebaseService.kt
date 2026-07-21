package com.example.network

import android.os.Handler
import android.os.Looper
import com.example.model.BusLocation
import com.example.model.BusRoute
import com.example.model.ElahiehPreseededData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class FirebaseService {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance("https://bus-driver-cb38a-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }

    init {
        // Automatically seed the pre-defined routes if Firebase Realtime Database is empty
        seedRoutesIfEmpty()
    }

    private var localGuestEmail: String? = null
    private var isLocalLoggedIn: Boolean = false

    // --- Authentication ---
    fun loginUser(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        try {
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    isLocalLoggedIn = false
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    val msg = e.localizedMessage ?: ""
                    if (isForbiddenOrHtmlError(msg)) {
                        localGuestEmail = email
                        isLocalLoggedIn = true
                        onSuccess()
                    } else {
                        onFailure(formatAuthError(msg))
                    }
                }
        } catch (e: Exception) {
            localGuestEmail = email
            isLocalLoggedIn = true
            onSuccess()
        }
    }

    fun registerUser(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        try {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    isLocalLoggedIn = false
                    onSuccess()
                    seedRoutesIfEmpty()
                }
                .addOnFailureListener { e ->
                    val msg = e.localizedMessage ?: ""
                    if (isForbiddenOrHtmlError(msg)) {
                        localGuestEmail = email
                        isLocalLoggedIn = true
                        onSuccess()
                    } else {
                        onFailure(formatAuthError(msg))
                    }
                }
        } catch (e: Exception) {
            localGuestEmail = email
            isLocalLoggedIn = true
            onSuccess()
        }
    }

    fun loginAsGuest(onSuccess: () -> Unit) {
        localGuestEmail = "کاربر مهمان (الهیه)"
        isLocalLoggedIn = true
        onSuccess()
    }

    fun isUserLoggedIn(): Boolean = isLocalLoggedIn || auth.currentUser != null

    fun logout() {
        isLocalLoggedIn = false
        localGuestEmail = null
        try {
            auth.signOut()
        } catch (e: Exception) {}
    }

    fun getUserEmail(): String = localGuestEmail ?: auth.currentUser?.email ?: "کاربر مهمان"

    private fun isForbiddenOrHtmlError(msg: String): Boolean {
        val lower = msg.lowercase()
        return lower.contains("403") || 
               lower.contains("forbidden") || 
               lower.contains("json conversion failed") || 
               lower.contains("doctype") || 
               lower.contains("internal error")
    }

    private fun formatAuthError(rawMsg: String): String {
        val lower = rawMsg.lowercase()
        return when {
            lower.contains("user-not-found") || lower.contains("no user record") -> "حساب کاربری با این ایمیل یافت نشد."
            lower.contains("wrong-password") || lower.contains("invalid-credential") -> "رمز عبور وارد شده اشتباه است."
            lower.contains("email-already-in-use") -> "این ایمیل قبلاً ثبت شده است."
            lower.contains("invalid-email") -> "فرمت ایمیل نامعتبر است."
            lower.contains("network") -> "خطا در اتصال به شبکه."
            else -> "امکان ارتباط با سرور وجود ندارد. از گزینه «ورود سریع مهمان» استفاده کنید."
        }
    }

    // --- Database Seeding ---
    private fun seedRoutesIfEmpty() {
        val routesRef = database.getReference("routes")
        routesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // Pre-populate with our high-fidelity data
                    ElahiehPreseededData.routes.forEach { route ->
                        routesRef.child(route.id).setValue(route)
                    }
                    // Also start the live simulation seed
                    startLiveSimulation()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- Real-time Bus Tracking ---
    // Listens to live bus locations in Firebase Database
    fun observeBusLocations(): Flow<List<BusLocation>> = callbackFlow {
        val locationsRef = database.getReference("bus_locations")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableListOf<BusLocation>()
                for (child in snapshot.children) {
                    val loc = child.getValue(BusLocation::class.java)
                    if (loc != null) {
                        locations.add(loc)
                    }
                }
                trySend(locations)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        locationsRef.addValueEventListener(listener)
        awaitClose { locationsRef.removeEventListener(listener) }
    }

    // Loads bus routes dynamically from Firebase
    fun observeBusRoutes(): Flow<List<BusRoute>> = callbackFlow {
        val routesRef = database.getReference("routes")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val routes = mutableListOf<BusRoute>()
                for (child in snapshot.children) {
                    val route = child.getValue(BusRoute::class.java)
                    if (route != null) {
                        routes.add(route)
                    }
                }
                // Fallback to pre-seeded local if database is silent/empty
                if (routes.isEmpty()) {
                    trySend(ElahiehPreseededData.routes)
                } else {
                    trySend(routes)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(ElahiehPreseededData.routes)
            }
        }
        routesRef.addValueEventListener(listener)
        awaitClose { routesRef.removeEventListener(listener) }
    }

    // --- Smooth Real-Time Simulation Engine ---
    // Simulates bus driver driving along coordinates, updating the location node in Realtime Database.
    // This allows passenger app to listen to actual firebase coordinates!
    private var simulationHandler: Handler? = null
    private var simulationRunnable: Runnable? = null

    fun startLiveSimulation() {
        if (simulationHandler != null) return // Already running
        
        simulationHandler = Handler(Looper.getMainLooper())
        
        val routes = ElahiehPreseededData.routes
        val indices = IntArray(routes.size) { 0 }
        val directions = IntArray(routes.size) { 1 } // 1 for forward, -1 for backward

        simulationRunnable = object : Runnable {
            override fun run() {
                val locationsRef = database.getReference("bus_locations")
                
                routes.forEachIndexed { i, route ->
                    val coords = route.coordinates
                    if (coords.isNotEmpty()) {
                        var currIndex = indices[i]
                        var dir = directions[i]
                        
                        // Move to next coordinate
                        currIndex += dir
                        if (currIndex >= coords.size || currIndex < 0) {
                            dir = -dir
                            directions[i] = dir
                            currIndex += dir * 2 // turn around
                        }
                        
                        // Safe index clamping
                        currIndex = currIndex.coerceIn(0, coords.size - 1)
                        indices[i] = currIndex
                        
                        val point = coords[currIndex]
                        val lng = point[0]
                        val lat = point[1]

                        // Calculate mock bearing
                        val nextIndex = (currIndex + dir).coerceIn(0, coords.size - 1)
                        val nextPoint = coords[nextIndex]
                        val bearing = calculateBearing(lat, lng, nextPoint[1], nextPoint[0])

                        val busLocation = BusLocation(
                            routeId = route.id,
                            lat = lat,
                            lng = lng,
                            speedKmh = (30..50).random(),
                            bearing = bearing,
                            lastUpdated = System.currentTimeMillis()
                        )
                        
                        locationsRef.child(route.id).setValue(busLocation)
                    }
                }
                
                // Update location every 3.5 seconds for a snappy real-time movement
                simulationHandler?.postDelayed(this, 3500)
            }
        }
        
        simulationHandler?.post(simulationRunnable!!)
    }

    fun stopLiveSimulation() {
        simulationRunnable?.let { simulationHandler?.removeCallbacks(it) }
        simulationHandler = null
        simulationRunnable = null
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }
}

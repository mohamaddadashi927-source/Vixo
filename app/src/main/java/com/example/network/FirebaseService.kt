package com.example.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.ZanjanBusData
import com.example.model.BusLine
import com.example.model.BusLocation
import com.example.model.BusRoute
import com.example.model.ElahiehPreseededData
import com.example.util.RouteEngine
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
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
        Log.d("FirebaseAuth", "Attempting login for email: $email")
        try {
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    Log.d("FirebaseAuth", "✅ Login successful for UID: ${authResult.user?.uid}")
                    isLocalLoggedIn = false
                    localGuestEmail = null
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    val rawMsg = e.localizedMessage ?: e.message ?: "Unknown login error"
                    Log.e("FirebaseAuth", "❌ Login failed for $email: $rawMsg", e)
                    onFailure(formatAuthError(rawMsg, e))
                }
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "❌ Exception during login call: ${e.message}", e)
            onFailure("خطا در برقراری ارتباط با سرویس احراز هویت.")
        }
    }

    fun registerUser(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        Log.d("FirebaseAuth", "Attempting registration for email: $email")
        try {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    Log.d("FirebaseAuth", "✅ User registered successfully in Firebase Auth! UID: ${authResult.user?.uid}")
                    isLocalLoggedIn = false
                    localGuestEmail = null
                    onSuccess()
                    seedRoutesIfEmpty()
                }
                .addOnFailureListener { e ->
                    val rawMsg = e.localizedMessage ?: e.message ?: "Unknown registration error"
                    Log.e("FirebaseAuth", "❌ Registration failed in Firebase for $email: $rawMsg", e)
                    onFailure(formatAuthError(rawMsg, e))
                }
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "❌ Exception during registration call: ${e.message}", e)
            onFailure("خطا در ثبت‌نام کاربر در فایربیس.")
        }
    }

    fun loginAsGuest(onSuccess: () -> Unit) {
        Log.d("FirebaseAuth", "Logging in as local guest")
        localGuestEmail = "کاربر مهمان (الهیه)"
        isLocalLoggedIn = true
        onSuccess()
    }

    fun isUserLoggedIn(): Boolean = isLocalLoggedIn || auth.currentUser != null

    fun logout() {
        Log.d("FirebaseAuth", "Logging out user")
        isLocalLoggedIn = false
        localGuestEmail = null
        try {
            auth.signOut()
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Logout exception: ${e.message}")
        }
    }

    fun getUserEmail(): String = localGuestEmail ?: auth.currentUser?.email ?: "کاربر مهمان"

    private fun formatAuthError(rawMsg: String, exception: Exception? = null): String {
        val lower = rawMsg.lowercase()
        val errorCode = (exception as? FirebaseAuthException)?.errorCode?.lowercase() ?: ""
        
        return when {
            errorCode.contains("user_not_found") || lower.contains("user-not-found") || lower.contains("no user record") -> 
                "حساب کاربری با این ایمیل یافت نشد."
            errorCode.contains("wrong_password") || lower.contains("wrong-password") || lower.contains("invalid-credential") || lower.contains("invalid_login_credentials") -> 
                "رمز عبور یا ایمیل وارد شده اشتباه است."
            errorCode.contains("email_already_in_use") || lower.contains("email-already-in-use") || lower.contains("already exists") -> 
                "این ایمیل قبلاً در سیستم ثبت شده است."
            errorCode.contains("invalid_email") || lower.contains("invalid-email") || lower.contains("badly formatted") -> 
                "فرمت ایمیل وارد شده نامعتبر است."
            errorCode.contains("weak_password") || lower.contains("weak-password") || lower.contains("password should be at least") -> 
                "رمز عبور باید حداقل ۶ کاراکتر باشد."
            lower.contains("network") || lower.contains("unreachable") || lower.contains("connection") -> 
                "خطا در اتصال به شبکه. لطفاً اینترنت خود را بررسی کنید."
            lower.contains("too-many-requests") || lower.contains("blocked") -> 
                "تعداد درخواست‌های ناموفق زیاد است. لطفاً چند دقیقه دیگر تلاش کنید."
            else -> "خطا در احراز هویت: $rawMsg"
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

        val busLinesRef = database.getReference("bus_lines")
        busLinesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    ZanjanBusData.allLines.forEach { line ->
                        busLinesRef.child(line.id).setValue(line)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- Real-time Bus Tracking ---
    // Listens to live bus locations in Firebase Database (supports active_buses and bus_locations)
    fun observeBusLocations(): Flow<List<BusLocation>> = callbackFlow {
        val locationsRef = database.getReference("bus_locations")
        val activeBusesRef = database.getReference("active_buses")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableListOf<BusLocation>()
                for (child in snapshot.children) {
                    val id = child.child("id").getValue(String::class.java)
                        ?: child.child("busId").getValue(String::class.java) ?: child.key ?: ""
                    val lineId = child.child("lineId").getValue(String::class.java) ?: ""
                    val lat = child.child("lat").getValue(Double::class.java)
                        ?: child.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("lng").getValue(Double::class.java)
                        ?: child.child("longitude").getValue(Double::class.java) ?: 0.0
                    val direction = child.child("direction").getValue(String::class.java) ?: "forward"
                    val speed = child.child("speed").getValue(Double::class.java) ?: 30.0

                    if (lat != 0.0 && lng != 0.0) {
                        locations.add(BusLocation(routeId = lineId, lat = lat, lng = lng, speedKmh = speed.toInt(), bearing = 0f, lastUpdated = System.currentTimeMillis()))
                    }
                }
                trySend(locations)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        activeBusesRef.addValueEventListener(listener)
        locationsRef.addValueEventListener(listener)

        awaitClose {
            activeBusesRef.removeEventListener(listener)
            locationsRef.removeEventListener(listener)
        }
    }

    // Loads bus lines dynamically from Firebase bus_lines node
    fun observeBusLines(): Flow<List<BusLine>> = callbackFlow {
        val linesRef = database.getReference("bus_lines")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lines = mutableListOf<BusLine>()
                for (child in snapshot.children) {
                    val parsedList = parseBusLinesFromSnapshot(child)
                    lines.addAll(parsedList)
                }
                if (lines.isEmpty()) {
                    trySend(ZanjanBusData.allLines)
                } else {
                    trySend(lines)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseService", "bus_lines listener cancelled: ${error.message}")
                trySend(ZanjanBusData.allLines)
            }
        }
        linesRef.addValueEventListener(listener)
        awaitClose { linesRef.removeEventListener(listener) }
    }

    private fun parseBusLinesFromSnapshot(snapshot: DataSnapshot): List<BusLine> {
        val result = mutableListOf<BusLine>()
        val parentId = snapshot.child("id").getValue(String::class.java) ?: snapshot.key ?: "line"

        val hasForward = snapshot.hasChild("forward")
        val hasBackward = snapshot.hasChild("backward")

        if (hasForward || hasBackward) {
            if (hasForward) {
                parseSingleDirSnapshot(snapshot, snapshot.child("forward"), "${parentId}_forward", "forward")?.let { result.add(it) }
            }
            if (hasBackward) {
                parseSingleDirSnapshot(snapshot, snapshot.child("backward"), "${parentId}_backward", "backward")?.let { result.add(it) }
            }
        } else {
            parseSingleDirSnapshot(snapshot, snapshot, parentId, "forward")?.let { result.add(it) }
        }
        return result
    }

    private fun parseSingleDirSnapshot(parentSnap: DataSnapshot, dirSnap: DataSnapshot, id: String, defaultDir: String): BusLine? {
        try {
            val name = dirSnap.child("name").getValue(String::class.java)
                ?: parentSnap.child("name").getValue(String::class.java) ?: "خط اتوبوس"
            val number = dirSnap.child("number").getValue(String::class.java)
                ?: parentSnap.child("number").getValue(String::class.java) ?: "۱۰۱"
            val colorHex = dirSnap.child("colorHex").getValue(String::class.java)
                ?: parentSnap.child("colorHex").getValue(String::class.java) ?: "#2563EB"

            val polyline = mutableListOf<org.osmdroid.util.GeoPoint>()
            val pathSnap = if (dirSnap.hasChild("path")) dirSnap.child("path") else dirSnap.child("polyline")
            for (ptSnap in pathSnap.children) {
                val lat = ptSnap.child("latitude").getValue(Double::class.java)
                    ?: ptSnap.child("lat").getValue(Double::class.java) ?: 0.0
                val lng = ptSnap.child("longitude").getValue(Double::class.java)
                    ?: ptSnap.child("lng").getValue(Double::class.java) ?: 0.0
                if (lat != 0.0 || lng != 0.0) {
                    polyline.add(org.osmdroid.util.GeoPoint(lat, lng))
                }
            }

            val rawStations = mutableListOf<com.example.model.Station>()
            val stopsSnap = if (dirSnap.hasChild("stops")) dirSnap.child("stops") else dirSnap.child("stations")
            for (stSnap in stopsSnap.children) {
                val stId = stSnap.child("id").getValue(String::class.java) ?: ""
                val stLat = stSnap.child("lat").getValue(Double::class.java)
                    ?: stSnap.child("latitude").getValue(Double::class.java) ?: 0.0
                val stLng = stSnap.child("lng").getValue(Double::class.java)
                    ?: stSnap.child("longitude").getValue(Double::class.java) ?: 0.0
                val stOrderIndex = (stSnap.child("orderIndex").getValue(Long::class.java) ?: 0L).toInt()
                val stDirection = stSnap.child("direction").getValue(String::class.java) ?: defaultDir
                val stName = stSnap.child("name").getValue(String::class.java) ?: "ایستگاه"
                if (stLat != 0.0 || stLng != 0.0) {
                    rawStations.add(com.example.model.Station(stId, stLat, stLng, id, stOrderIndex, stDirection, stName))
                }
            }

            // Snap stops to path polyline points
            val snappedStations = if (polyline.isNotEmpty()) {
                rawStations.map { station ->
                    val closestIdx = RouteEngine.findClosestPolylineIndex(polyline, station.toGeoPoint())
                    val pt = polyline[closestIdx]
                    station.copy(lat = pt.latitude, lng = pt.longitude)
                }
            } else {
                rawStations
            }

            return BusLine(
                id = id,
                name = name,
                number = number,
                colorHex = colorHex,
                startTerminalName = snappedStations.firstOrNull()?.name ?: "",
                startTerminalPoint = polyline.firstOrNull() ?: org.osmdroid.util.GeoPoint(36.70, 48.46),
                endTerminalName = snappedStations.lastOrNull()?.name ?: "",
                endTerminalPoint = polyline.lastOrNull() ?: org.osmdroid.util.GeoPoint(36.70, 48.46),
                stations = snappedStations,
                polyline = polyline
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "Error parsing dir snapshot: ${e.message}")
            return null
        }
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

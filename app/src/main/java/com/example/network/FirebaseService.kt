package com.example.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.ZanjanBusData
import com.example.model.BusLine
import com.example.model.BusLocation
import com.example.model.BusRoute
import com.example.model.ElahiehPreseededData
import com.example.model.LiveBus
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
        // Read-only service
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
        // Read-only service: no writing or seeding to Firebase
    }

    // --- Real-time Bus Tracking ---
    // Listens to live bus locations in Firebase Database at "ActiveBuses" node
    fun observeActiveBuses(): Flow<List<LiveBus>> = callbackFlow {
        val activeBusesRef = database.getReference("ActiveBuses")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val buses = mutableListOf<LiveBus>()
                for (child in snapshot.children) {
                    val busId = child.child("busId").getValue(String::class.java)
                        ?: child.key ?: ""
                    val driverId = child.child("driverId").getValue(String::class.java) ?: ""
                    val lineId = child.child("lineId").getValue(String::class.java) ?: ""
                    val lat = child.child("lat").getValue(Double::class.java)
                        ?: child.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("lng").getValue(Double::class.java)
                        ?: child.child("longitude").getValue(Double::class.java) ?: 0.0
                    val speed = child.child("speed").getValue(Double::class.java) ?: 0.0
                    val heading = child.child("heading").getValue(Double::class.java)
                        ?: child.child("bearing").getValue(Double::class.java) ?: 0.0
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                    val isActive = child.child("isActive").getValue(Boolean::class.java) ?: true

                    if (lat != 0.0 && lng != 0.0) {
                        buses.add(
                            LiveBus(
                                busId = busId,
                                driverId = driverId,
                                lineId = lineId,
                                lat = lat,
                                lng = lng,
                                speed = speed,
                                heading = heading,
                                timestamp = timestamp,
                                isActive = isActive
                            )
                        )
                    }
                }
                trySend(buses)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseService", "ActiveBuses listener cancelled: ${error.message}")
            }
        }

        activeBusesRef.addValueEventListener(listener)

        awaitClose {
            activeBusesRef.removeEventListener(listener)
        }
    }

    fun observeBusLocations(): Flow<List<BusLocation>> = callbackFlow {
        val activeBusesRef = database.getReference("ActiveBuses")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val locations = mutableListOf<BusLocation>()
                for (child in snapshot.children) {
                    val lineId = child.child("lineId").getValue(String::class.java) ?: ""
                    val lat = child.child("lat").getValue(Double::class.java) ?: 0.0
                    val lng = child.child("lng").getValue(Double::class.java) ?: 0.0
                    val speed = child.child("speed").getValue(Double::class.java) ?: 0.0
                    val heading = child.child("heading").getValue(Double::class.java) ?: 0.0

                    if (lat != 0.0 && lng != 0.0) {
                        locations.add(
                            BusLocation(
                                routeId = lineId,
                                lat = lat,
                                lng = lng,
                                speedKmh = speed.toInt(),
                                bearing = heading.toFloat(),
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
                trySend(locations)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        activeBusesRef.addValueEventListener(listener)

        awaitClose {
            activeBusesRef.removeEventListener(listener)
        }
    }

    // Loads bus lines dynamically from Firebase transport and bus_lines nodes
    fun observeBusLines(): Flow<List<BusLine>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lines = parseAllLinesFromTransport(snapshot)
                if (lines.isNotEmpty()) {
                    trySend(lines)
                } else {
                    database.getReference("bus_lines").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snap2: DataSnapshot) {
                            val lines2 = mutableListOf<BusLine>()
                            for (child in snap2.children) {
                                lines2.addAll(parseBusLinesFromSnapshot(child))
                            }
                            if (lines2.isNotEmpty()) {
                                trySend(lines2)
                            } else {
                                trySend(ZanjanBusData.allLines)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            trySend(ZanjanBusData.allLines)
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseService", "transport listener cancelled: ${error.message}")
                trySend(ZanjanBusData.allLines)
            }
        }

        val transportRef = database.getReference("transport")
        transportRef.addValueEventListener(listener)

        awaitClose { transportRef.removeEventListener(listener) }
    }

    suspend fun getAllLinesFromFirebase(): List<BusLine> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val dbRef = database.reference
        dbRef.child("transport").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lines = parseAllLinesFromTransport(snapshot)
                if (lines.isNotEmpty()) {
                    if (continuation.isActive) continuation.resume(lines, null)
                } else {
                    dbRef.child("bus_lines").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snap2: DataSnapshot) {
                            val lines2 = mutableListOf<BusLine>()
                            for (child in snap2.children) {
                                lines2.addAll(parseBusLinesFromSnapshot(child))
                            }
                            if (lines2.isNotEmpty()) {
                                if (continuation.isActive) continuation.resume(lines2, null)
                            } else {
                                if (continuation.isActive) continuation.resume(ZanjanBusData.allLines, null)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            if (continuation.isActive) continuation.resume(ZanjanBusData.allLines, null)
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (continuation.isActive) continuation.resume(ZanjanBusData.allLines, null)
            }
        })
    }

    private fun parseAllLinesFromTransport(snapshot: DataSnapshot): List<BusLine> {
        val result = mutableListOf<BusLine>()
        if (!snapshot.exists()) return result

        if (snapshot.hasChild("lines")) {
            for (child in snapshot.child("lines").children) {
                result.addAll(parseBusLinesFromSnapshot(child))
            }
        }

        for (provChild in snapshot.children) {
            if (provChild.hasChild("lines")) {
                for (lineChild in provChild.child("lines").children) {
                    result.addAll(parseBusLinesFromSnapshot(lineChild))
                }
            }
            for (cityChild in provChild.children) {
                if (cityChild.hasChild("lines")) {
                    for (lineChild in cityChild.child("lines").children) {
                        result.addAll(parseBusLinesFromSnapshot(lineChild))
                    }
                } else if (cityChild.key == "lines") {
                    for (lineChild in cityChild.children) {
                        result.addAll(parseBusLinesFromSnapshot(lineChild))
                    }
                }
            }
        }

        return result.distinctBy { it.id }
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
            var idx = 0
            for (stSnap in stopsSnap.children) {
                val stId = stSnap.child("id").getValue(String::class.java) ?: "s_${id}_${idx}"
                val stLat = stSnap.child("lat").getValue(Double::class.java)
                    ?: stSnap.child("latitude").getValue(Double::class.java) ?: 0.0
                val stLng = stSnap.child("lng").getValue(Double::class.java)
                    ?: stSnap.child("longitude").getValue(Double::class.java) ?: 0.0
                val stOrderIndex = (stSnap.child("order").getValue(Long::class.java)
                    ?: stSnap.child("orderIndex").getValue(Long::class.java)
                    ?: idx.toLong()).toInt()
                val stDirection = stSnap.child("direction").getValue(String::class.java) ?: defaultDir
                val stName = stSnap.child("name").getValue(String::class.java) ?: "ایستگاه $stOrderIndex"
                val stLineId = stSnap.child("lineId").getValue(String::class.java) ?: id
                if (stLat != 0.0 || stLng != 0.0) {
                    rawStations.add(com.example.model.Station(stId, stLat, stLng, stLineId, stOrderIndex, stDirection, stName))
                }
                idx++
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
                stations = snappedStations.sortedBy { it.orderIndex },
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

    // Read-only service: simulation is handled locally in repository/viewmodel if fallback condition is met
    fun startLiveSimulation() {}

    fun stopLiveSimulation() {}

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

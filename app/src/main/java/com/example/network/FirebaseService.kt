package com.example.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.model.BusLocation
import com.example.model.BusRoute
import com.example.model.ElahiehPreseededData
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

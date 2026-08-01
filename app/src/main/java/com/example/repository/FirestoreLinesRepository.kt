package com.example.repository

import android.util.Log
import com.example.data.ZanjanBusData
import com.example.model.BusLine
import com.example.model.PathPoint
import com.example.model.Station
import com.example.util.RouteEngine
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.util.GeoPoint
import kotlin.coroutines.resume

class FirestoreLinesRepository {

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val rtdb: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance("https://bus-driver-cb38a-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }

    fun observeLines(provinceId: String = "zanjan", cityId: String = "zanjan"): Flow<List<BusLine>> = callbackFlow {
        val remoteLinesMap = mutableMapOf<String, BusLine>()

        fun emitCombinedLines() {
            if (remoteLinesMap.isNotEmpty()) {
                trySend(remoteLinesMap.values.toList())
            } else {
                trySend(ZanjanBusData.allLines)
            }
        }

        // Send initial baseline immediately
        emitCombinedLines()

        // 1. Listen to Cloud Firestore collection: regions/{provinceId}/cities/{cityId}/lines
        var firestoreReg: ListenerRegistration? = null
        try {
            val firestoreRef = firestore.collection("regions")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("lines")

            firestoreReg = firestoreRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirestoreLinesRepo", "Firestore listener failed: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    snapshot.documents.forEach { doc ->
                        parseBusLineFromFirestoreDoc(doc)?.let { line ->
                            remoteLinesMap[line.id] = line
                        }
                    }
                    emitCombinedLines()
                }
            }
        } catch (e: Exception) {
            Log.e("FirestoreLinesRepo", "Error attaching Firestore listener: ${e.message}")
        }

        // 2. Listen to Cloud Firestore root collection: lines
        var firestoreRootReg: ListenerRegistration? = null
        try {
            firestoreRootReg = firestore.collection("lines").addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && !snapshot.isEmpty) {
                    snapshot.documents.forEach { doc ->
                        parseBusLineFromFirestoreDoc(doc)?.let { line ->
                            remoteLinesMap[line.id] = line
                        }
                    }
                    emitCombinedLines()
                }
            }
        } catch (e: Exception) {
            Log.e("FirestoreLinesRepo", "Error attaching Firestore root listener: ${e.message}")
        }

        // 3. Listen to Realtime Database as well
        var rtdbListener: ValueEventListener? = null
        val rtdbRef = rtdb.getReference("regions").child(provinceId).child("cities").child(cityId).child("lines")
        try {
            rtdbListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val parsed = parseLinesFromSnapshot(snapshot)
                    if (parsed.isNotEmpty()) {
                        parsed.forEach { remoteLinesMap[it.id] = it }
                        emitCombinedLines()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("FirestoreLinesRepo", "RTDB listener cancelled: ${error.message}")
                }
            }
            rtdbRef.addValueEventListener(rtdbListener)
        } catch (e: Exception) {
            Log.e("FirestoreLinesRepo", "Error attaching RTDB listener: ${e.message}")
        }

        awaitClose {
            firestoreReg?.remove()
            firestoreRootReg?.remove()
            rtdbListener?.let { rtdbRef.removeEventListener(it) }
        }
    }

    suspend fun getLines(provinceId: String = "zanjan", cityId: String = "zanjan"): List<BusLine> = suspendCancellableCoroutine { continuation ->
        val remoteLinesMap = mutableMapOf<String, BusLine>()

        firestore.collection("regions")
            .document(provinceId)
            .collection("cities")
            .document(cityId)
            .collection("lines")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && !snapshot.isEmpty) {
                    snapshot.documents.forEach { doc ->
                        parseBusLineFromFirestoreDoc(doc)?.let { line ->
                            remoteLinesMap[line.id] = line
                        }
                    }
                }
                val linesToReturn = if (remoteLinesMap.isNotEmpty()) remoteLinesMap.values.toList() else ZanjanBusData.allLines
                if (continuation.isActive) continuation.resume(linesToReturn)
            }
            .addOnFailureListener { e ->
                Log.w("FirestoreLinesRepo", "Failed to fetch from Firestore: ${e.message}")
                val fallback = ZanjanBusData.allLines
                if (continuation.isActive) continuation.resume(fallback)
            }
    }

    private fun parseBusLineFromFirestoreDoc(doc: DocumentSnapshot): BusLine? {
        return try {
            val id = doc.getString("id") ?: doc.id
            val name = doc.getString("name") ?: "خط اتوبوس"
            val number = doc.getString("number") ?: "۱۰۱"
            val city = doc.getString("city") ?: "زنجان"
            val province = doc.getString("province") ?: "زنجان"
            val colorHex = doc.get("colorHex")?.toString() ?: "#2563EB"
            val startTerminalName = doc.getString("startTerminalName") ?: ""
            val endTerminalName = doc.getString("endTerminalName") ?: ""

            // Parse path / polyline
            val pathPoints = mutableListOf<PathPoint>()
            val pathList = doc.get("path") as? List<*> ?: doc.get("polyline") as? List<*> ?: doc.get("waypoints") as? List<*>
            if (pathList != null) {
                pathList.forEachIndexed { index, item ->
                    if (item is Map<*, *>) {
                        val lat = (item["lat"] as? Number)?.toDouble() ?: (item["latitude"] as? Number)?.toDouble() ?: 0.0
                        val lng = (item["lng"] as? Number)?.toDouble() ?: (item["longitude"] as? Number)?.toDouble() ?: 0.0
                        val order = (item["order"] as? Number)?.toInt() ?: index
                        if (lat != 0.0 || lng != 0.0) {
                            pathPoints.add(PathPoint(lat, lng, order))
                        }
                    }
                }
            }

            val sortedPathPoints = pathPoints.sortedBy { it.order }
            val polyline = sortedPathPoints.map { it.toGeoPoint() }

            // Parse stations / stops
            val stationsList = mutableListOf<Station>()
            val stopsList = doc.get("stops") as? List<*> ?: doc.get("stations") as? List<*>
            if (stopsList != null) {
                stopsList.forEachIndexed { index, item ->
                    if (item is Map<*, *>) {
                        val stId = (item["id"] as? String) ?: "s_${id}_${index}"
                        val stLat = (item["lat"] as? Number)?.toDouble() ?: (item["latitude"] as? Number)?.toDouble() ?: 0.0
                        val stLng = (item["lng"] as? Number)?.toDouble() ?: (item["longitude"] as? Number)?.toDouble() ?: 0.0
                        val stOrder = (item["orderIndex"] as? Number)?.toInt() ?: (item["order"] as? Number)?.toInt() ?: index
                        val stDirection = (item["direction"] as? String) ?: "forward"
                        val stName = (item["name"] as? String) ?: "ایستگاه $stOrder"
                        val stLineId = (item["lineId"] as? String) ?: id

                        if (stLat != 0.0 || stLng != 0.0) {
                            stationsList.add(Station(stId, stLat, stLng, stLineId, stOrder, stDirection, stName))
                        }
                    }
                }
            }

            val snappedStations = if (polyline.isNotEmpty() && stationsList.isNotEmpty()) {
                stationsList.map { station ->
                    val closestIdx = RouteEngine.findClosestPolylineIndex(polyline, station.toGeoPoint())
                    val pt = polyline[closestIdx]
                    station.copy(lat = pt.latitude, lng = pt.longitude)
                }
            } else {
                stationsList
            }

            BusLine(
                id = id,
                name = name,
                number = number,
                city = city,
                province = province,
                colorHex = colorHex,
                startTerminalName = startTerminalName.ifBlank { snappedStations.firstOrNull()?.name ?: "" },
                startTerminalPoint = polyline.firstOrNull() ?: GeoPoint(36.70, 48.46),
                endTerminalName = endTerminalName.ifBlank { snappedStations.lastOrNull()?.name ?: "" },
                endTerminalPoint = polyline.lastOrNull() ?: GeoPoint(36.70, 48.46),
                stations = snappedStations.sortedBy { it.orderIndex },
                polyline = polyline
            )
        } catch (e: Exception) {
            Log.e("FirestoreLinesRepo", "Error parsing Firestore doc: ${e.message}")
            null
        }
    }

    private fun parseLinesFromSnapshot(snapshot: DataSnapshot): List<BusLine> {
        val list = mutableListOf<BusLine>()
        if (!snapshot.exists()) return list

        for (child in snapshot.children) {
            parseBusLineFromSingleSnapshot(child)?.let { list.add(it) }
        }
        return list
    }

    private fun parseBusLineFromSingleSnapshot(snapshot: DataSnapshot): BusLine? {
        return try {
            val metaSnap = if (snapshot.hasChild("metadata")) snapshot.child("metadata") else snapshot
            val id = metaSnap.child("id").getValue(String::class.java)
                ?: snapshot.key ?: return null
            val name = metaSnap.child("name").getValue(String::class.java)
                ?: snapshot.child("name").getValue(String::class.java) ?: "خط اتوبوس"
            val number = metaSnap.child("number").getValue(String::class.java)
                ?: snapshot.child("number").getValue(String::class.java) ?: "۱۰۱"
            val city = metaSnap.child("city").getValue(String::class.java) ?: "زنجان"
            val province = metaSnap.child("province").getValue(String::class.java) ?: "زنجان"
            val colorHex = metaSnap.child("colorHex").getValue(String::class.java)
                ?: snapshot.child("colorHex").getValue(String::class.java) ?: "#2563EB"
            val startTerminalName = metaSnap.child("startTerminalName").getValue(String::class.java) ?: ""
            val endTerminalName = metaSnap.child("endTerminalName").getValue(String::class.java) ?: ""

            // Parse path / polyline
            val pathPoints = mutableListOf<PathPoint>()
            val pathSnap = when {
                snapshot.hasChild("path") -> snapshot.child("path")
                snapshot.hasChild("polyline") -> snapshot.child("polyline")
                else -> null
            }

            if (pathSnap != null) {
                var orderIdx = 0
                for (ptChild in pathSnap.children) {
                    val lat = ptChild.child("lat").getValue(Double::class.java)
                        ?: ptChild.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = ptChild.child("lng").getValue(Double::class.java)
                        ?: ptChild.child("longitude").getValue(Double::class.java) ?: 0.0
                    val order = (ptChild.child("order").getValue(Long::class.java) ?: orderIdx.toLong()).toInt()
                    if (lat != 0.0 || lng != 0.0) {
                        pathPoints.add(PathPoint(lat, lng, order))
                    }
                    orderIdx++
                }
            }

            val sortedPathPoints = pathPoints.sortedBy { it.order }
            val polyline = sortedPathPoints.map { it.toGeoPoint() }

            // Parse stations / stops
            val stationsList = mutableListOf<Station>()
            val stopsSnap = when {
                snapshot.hasChild("stops") -> snapshot.child("stops")
                snapshot.hasChild("stations") -> snapshot.child("stations")
                else -> null
            }

            if (stopsSnap != null) {
                var stIdx = 0
                for (stChild in stopsSnap.children) {
                    val stId = stChild.child("id").getValue(String::class.java) ?: "s_${id}_${stIdx}"
                    val stLat = stChild.child("lat").getValue(Double::class.java)
                        ?: stChild.child("latitude").getValue(Double::class.java) ?: 0.0
                    val stLng = stChild.child("lng").getValue(Double::class.java)
                        ?: stChild.child("longitude").getValue(Double::class.java) ?: 0.0
                    val stOrder = (stChild.child("orderIndex").getValue(Long::class.java)
                        ?: stChild.child("order").getValue(Long::class.java)
                        ?: stIdx.toLong()).toInt()
                    val stDirection = stChild.child("direction").getValue(String::class.java) ?: "forward"
                    val stName = stChild.child("name").getValue(String::class.java) ?: "ایستگاه $stOrder"
                    val stLineId = stChild.child("lineId").getValue(String::class.java) ?: id

                    if (stLat != 0.0 || stLng != 0.0) {
                        stationsList.add(Station(stId, stLat, stLng, stLineId, stOrder, stDirection, stName))
                    }
                    stIdx++
                }
            }

            val snappedStations = if (polyline.isNotEmpty() && stationsList.isNotEmpty()) {
                stationsList.map { station ->
                    val closestIdx = RouteEngine.findClosestPolylineIndex(polyline, station.toGeoPoint())
                    val pt = polyline[closestIdx]
                    station.copy(lat = pt.latitude, lng = pt.longitude)
                }
            } else {
                stationsList
            }

            BusLine(
                id = id,
                name = name,
                number = number,
                city = city,
                province = province,
                colorHex = colorHex,
                startTerminalName = startTerminalName.ifBlank { snappedStations.firstOrNull()?.name ?: "" },
                startTerminalPoint = polyline.firstOrNull() ?: GeoPoint(36.70, 48.46),
                endTerminalName = endTerminalName.ifBlank { snappedStations.lastOrNull()?.name ?: "" },
                endTerminalPoint = polyline.lastOrNull() ?: GeoPoint(36.70, 48.46),
                stations = snappedStations.sortedBy { it.orderIndex },
                polyline = polyline
            )
        } catch (e: Exception) {
            Log.e("FirestoreLinesRepo", "Error parsing line snapshot: ${e.message}")
            null
        }
    }
}


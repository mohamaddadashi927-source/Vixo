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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.util.GeoPoint
import kotlin.coroutines.resume

class FirestoreLinesRepository {

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance("https://bus-driver-cb38a-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }

    fun observeLines(provinceId: String = "zanjan", cityId: String = "zanjan"): Flow<List<BusLine>> = callbackFlow {
        val regionLinesRef = database.getReference("regions").child(provinceId).child("cities").child(cityId).child("lines")
        val rootLinesRef = database.getReference("lines")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var lines = parseLinesFromSnapshot(snapshot)
                if (lines.isEmpty()) {
                    rootLinesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(rootSnapshot: DataSnapshot) {
                            val rootParsed = parseLinesFromSnapshot(rootSnapshot)
                            if (rootParsed.isNotEmpty()) {
                                trySend(rootParsed)
                            } else {
                                seedInitialDataToFirebase(provinceId, cityId)
                                trySend(ZanjanBusData.allLines)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            trySend(ZanjanBusData.allLines)
                        }
                    })
                } else {
                    trySend(lines)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirestoreLinesRepo", "Error listening to lines: ${error.message}")
            }
        }

        regionLinesRef.addValueEventListener(listener)
        awaitClose { regionLinesRef.removeEventListener(listener) }
    }

    suspend fun getLines(provinceId: String = "zanjan", cityId: String = "zanjan"): List<BusLine> = suspendCancellableCoroutine { continuation ->
        val regionLinesRef = database.getReference("regions").child(provinceId).child("cities").child(cityId).child("lines")
        regionLinesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lines = parseLinesFromSnapshot(snapshot)
                if (lines.isEmpty()) {
                    database.getReference("lines").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(rootSnap: DataSnapshot) {
                            val rootLines = parseLinesFromSnapshot(rootSnap)
                            if (rootLines.isNotEmpty()) {
                                if (continuation.isActive) continuation.resume(rootLines)
                            } else {
                                seedInitialDataToFirebase(provinceId, cityId)
                                if (continuation.isActive) continuation.resume(ZanjanBusData.allLines)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (continuation.isActive) continuation.resume(ZanjanBusData.allLines)
                        }
                    })
                } else {
                    if (continuation.isActive) continuation.resume(lines)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (continuation.isActive) continuation.resume(ZanjanBusData.allLines)
            }
        })
    }

    fun seedInitialDataToFirebase(provinceId: String = "zanjan", cityId: String = "zanjan") {
        val regionLinesRef = database.getReference("regions").child(provinceId).child("cities").child(cityId).child("lines")
        val rootLinesRef = database.getReference("lines")

        ZanjanBusData.allLines.forEach { line ->
            val cleanId = line.id.trim()
            val metadata = mapOf(
                "id" to cleanId,
                "name" to line.name,
                "number" to line.number,
                "city" to line.city,
                "province" to line.province,
                "colorHex" to line.colorHex,
                "startTerminalName" to line.startTerminalName,
                "endTerminalName" to line.endTerminalName
            )

            val stops = line.stations.map { st ->
                mapOf(
                    "id" to st.id,
                    "name" to st.name,
                    "lat" to st.lat,
                    "lng" to st.lng,
                    "orderIndex" to st.orderIndex,
                    "direction" to st.direction,
                    "lineId" to cleanId
                )
            }

            val pathPoints = line.polyline.mapIndexed { index, pt ->
                mapOf("lat" to pt.latitude, "lng" to pt.longitude, "order" to index)
            }

            val lineData = mapOf(
                "metadata" to metadata,
                "stops" to stops,
                "path" to pathPoints,
                "id" to cleanId,
                "name" to line.name,
                "number" to line.number,
                "colorHex" to line.colorHex
            )

            try {
                regionLinesRef.child(cleanId).setValue(lineData)
                rootLinesRef.child(cleanId).setValue(lineData)
            } catch (e: Exception) {
                Log.e("FirestoreLinesRepo", "Error seeding line $cleanId: ${e.message}")
            }
        }
    }

    fun parseLinesFromSnapshot(snapshot: DataSnapshot): List<BusLine> {
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

package com.example.repository

import com.example.data.ZanjanBusData
import com.example.model.BusLine
import com.example.model.LiveBus
import com.example.model.TransitPlan
import com.example.network.FirebaseService
import com.example.util.RouteEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class BusRepository(
    private val firebaseService: FirebaseService = FirebaseService()
) {
    private var cachedBusLines: List<BusLine> = ZanjanBusData.allLines

    suspend fun getAllLinesFromFirebase(): List<BusLine> {
        val firebaseLines = firebaseService.getAllLinesFromFirebase()
        if (firebaseLines.isNotEmpty()) {
            cachedBusLines = firebaseLines
        }
        return cachedBusLines
    }

    fun observeBusLines(): Flow<List<BusLine>> {
        return firebaseService.observeBusLines().map { lines ->
            if (lines.isNotEmpty()) {
                cachedBusLines = lines
            }
            cachedBusLines
        }
    }

    fun getBusLines(): List<BusLine> = cachedBusLines

    fun observeLiveBuses(): Flow<List<LiveBus>> = flow {
        var lastFirebaseBuses = emptyList<LiveBus>()
        var lastFirebaseUpdateTimestamp = 0L
        var simStep = 0

        coroutineScope {
            launch {
                firebaseService.observeActiveBuses().collect { rawBuses ->
                    val activeBuses = rawBuses.filter { it.isActive && it.lat != 0.0 && it.lng != 0.0 }
                    if (activeBuses.isNotEmpty()) {
                        lastFirebaseBuses = activeBuses
                        lastFirebaseUpdateTimestamp = System.currentTimeMillis()
                    } else {
                        lastFirebaseBuses = emptyList()
                    }
                }
            }

            while (true) {
                val now = System.currentTimeMillis()
                val isFirebaseFreshAndNotEmpty = lastFirebaseBuses.isNotEmpty() && (now - lastFirebaseUpdateTimestamp <= 10000)

                if (isFirebaseFreshAndNotEmpty) {
                    emit(lastFirebaseBuses)
                } else {
                    simStep++
                    val simulated = generateSimulatedLiveBuses(cachedBusLines, simStep)
                    emit(simulated)
                }
                delay(1000)
            }
        }
    }

    private fun generateSimulatedLiveBuses(lines: List<BusLine>, simStep: Int): List<LiveBus> {
        val activeLines = if (lines.isNotEmpty()) lines else ZanjanBusData.allLines
        return activeLines.mapIndexed { index, line ->
            val poly = line.polyline
            val point = if (poly.isNotEmpty()) {
                poly[(simStep + index * 2) % poly.size]
            } else {
                val first = line.stations.firstOrNull()
                GeoPoint(first?.lat ?: 36.6800, first?.lng ?: 48.4700)
            }
            LiveBus(
                busId = "sim_bus_${line.number}",
                driverId = "sim_driver_${line.number}",
                lineId = line.id,
                lineName = line.name,
                lat = point.latitude,
                lng = point.longitude,
                speed = 35.0,
                heading = ((index * 45.0) + (simStep * 10.0)) % 360.0,
                timestamp = System.currentTimeMillis(),
                isActive = true
            )
        }
    }

    suspend fun computeTransitRoute(
        userOrigin: GeoPoint,
        userDest: GeoPoint,
        liveBuses: List<LiveBus>,
        customLines: List<BusLine>? = null
    ): TransitPlan? {
        val linesToUse = customLines ?: cachedBusLines
        return RouteEngine.calculateTransitPlan(
            userOrigin = userOrigin,
            userDest = userDest,
            liveBuses = liveBuses,
            lines = if (linesToUse.isNotEmpty()) linesToUse else ZanjanBusData.allLines
        )
    }
}

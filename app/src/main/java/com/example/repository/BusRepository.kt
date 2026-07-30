package com.example.repository

import com.example.data.ZanjanBusData
import com.example.model.BusLine
import com.example.model.LiveBus
import com.example.model.TransitPlan
import com.example.network.FirebaseService
import com.example.util.RouteEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    fun observeLiveBuses(): Flow<List<LiveBus>> {
        return firebaseService.observeActiveBuses().map { rawBuses ->
            rawBuses.filter { it.isActive && it.lat != 0.0 && it.lng != 0.0 }
        }
    }

    fun observeActiveBusesForLine(lineId: String): Flow<List<LiveBus>> {
        return firebaseService.observeActiveBusesForLine(lineId).map { rawBuses ->
            rawBuses.filter { it.isActive && it.lat != 0.0 && it.lng != 0.0 }
        }
    }

    fun updateDriverLocationOnShift(
        driverId: String,
        busId: String,
        lineId: String,
        lat: Double,
        lng: Double,
        speed: Double,
        heading: Double,
        isActive: Boolean
    ) {
        firebaseService.updateDriverLocationOnShift(
            driverId = driverId,
            busId = busId,
            lineId = lineId,
            lat = lat,
            lng = lng,
            speed = speed,
            heading = heading,
            isActive = isActive
        )
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

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
        return firebaseService.observeBusLocations().map { busLocations ->
            val currentLines = cachedBusLines
            if (busLocations.isEmpty()) {
                generateSimulatedLiveBuses(currentLines)
            } else {
                busLocations.map { loc ->
                    val line = currentLines.find { it.id == loc.routeId }
                    LiveBus(
                        busId = "bus_${loc.routeId}",
                        lineId = loc.routeId,
                        lineName = line?.name ?: "خط اتوبوس",
                        lat = loc.lat,
                        lng = loc.lng,
                        speedKmh = if (loc.speedKmh > 0) loc.speedKmh else 35,
                        bearing = loc.bearing,
                        lastUpdated = loc.lastUpdated
                    )
                }
            }
        }
    }

    private fun generateSimulatedLiveBuses(lines: List<BusLine>): List<LiveBus> {
        val activeLines = if (lines.isNotEmpty()) lines else ZanjanBusData.allLines
        return activeLines.mapIndexed { index, line ->
            val firstStation = line.stations.firstOrNull()
            LiveBus(
                busId = "live_bus_${line.number}",
                lineId = line.id,
                lineName = line.name,
                lat = (firstStation?.lat ?: 36.6800) + (index * 0.003),
                lng = (firstStation?.lng ?: 48.4700) + (index * 0.003),
                speedKmh = 32 + (index * 4),
                bearing = 45f * (index + 1)
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

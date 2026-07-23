package com.example.util

import com.example.data.ZanjanBusData
import com.example.model.*
import com.example.network.OSRMRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import kotlin.math.*

object RouteEngine {

    // Haversine formula to compute great-circle distance between two points on Earth in km
    fun haversineDistanceKm(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        val radiusKm = 6371.0
        return radiusKm * c
    }

    // 1. FIND NEAREST STATIONS
    fun findNearestStation(
        userPoint: GeoPoint,
        lines: List<BusLine> = ZanjanBusData.allLines
    ): BusStation? {
        var minDistance = Double.MAX_VALUE
        var nearestStation: BusStation? = null

        for (line in lines) {
            for (station in line.stations) {
                val dist = haversineDistanceKm(userPoint, station.toGeoPoint())
                if (dist < minDistance) {
                    minDistance = dist
                    nearestStation = station
                }
            }
        }
        return nearestStation
    }

    // 2. MATCH BUS LINE
    // Checks which line contains both originStation and destinationStation in correct sequential order
    fun matchBusLine(
        originStation: BusStation,
        destStation: BusStation,
        lines: List<BusLine> = ZanjanBusData.allLines
    ): BusLine? {
        val candidateLines = lines.filter { line ->
            val originOnLine = line.stations.find { it.id == originStation.id || (abs(it.lat - originStation.lat) < 0.0001 && abs(it.lng - originStation.lng) < 0.0001) }
            val destOnLine = line.stations.find { it.id == destStation.id || (abs(it.lat - destStation.lat) < 0.0001 && abs(it.lng - destStation.lng) < 0.0001) }

            originOnLine != null && destOnLine != null && originOnLine.orderIndex < destOnLine.orderIndex
        }

        // Return candidate with shortest station gap
        return candidateLines.minByOrNull { line ->
            val o = line.stations.first { it.id == originStation.id || (abs(it.lat - originStation.lat) < 0.0001 && abs(it.lng - originStation.lng) < 0.0001) }
            val d = line.stations.first { it.id == destStation.id || (abs(it.lat - destStation.lat) < 0.0001 && abs(it.lng - destStation.lng) < 0.0001) }
            d.orderIndex - o.orderIndex
        } ?: lines.firstOrNull { it.id == originStation.lineId }
    }

    // 3. GENERATE 3 SEGMENTS (Walk -> Bus -> Walk)
    suspend fun calculateTransitPlan(
        userOrigin: GeoPoint,
        userDest: GeoPoint,
        liveBuses: List<LiveBus> = emptyList(),
        lines: List<BusLine> = ZanjanBusData.allLines
    ): TransitPlan? = withContext(Dispatchers.IO) {
        // Step 1: Find nearest stations to origin and destination
        val originStation = findNearestStation(userOrigin, lines) ?: return@withContext null
        val destStation = findNearestStation(userDest, lines) ?: return@withContext null

        if (originStation.id == destStation.id) {
            // Origin and dest station are the same or too close, pick next best dest station
            val alternativeDestStation = lines.flatMap { it.stations }
                .filter { it.id != originStation.id }
                .minByOrNull { haversineDistanceKm(userDest, it.toGeoPoint()) } ?: destStation

            return@withContext buildPlanForStations(userOrigin, userDest, originStation, alternativeDestStation, lines, liveBuses)
        }

        return@withContext buildPlanForStations(userOrigin, userDest, originStation, destStation, lines, liveBuses)
    }

    private suspend fun buildPlanForStations(
        userOrigin: GeoPoint,
        userDest: GeoPoint,
        originStation: BusStation,
        destStation: BusStation,
        lines: List<BusLine>,
        liveBuses: List<LiveBus>
    ): TransitPlan {
        // Step 2: Match Bus Line
        val busLine = matchBusLine(originStation, destStation, lines) ?: ZanjanBusData.line3

        // Step 3: Segment 1 - Walk: userOrigin -> originStation (using OSRM foot profile)
        val walk1Segment = fetchWalkingSegment(
            from = userOrigin,
            to = originStation.toGeoPoint(),
            title = "پیاده‌روی تا ایستگاه ${originStation.name}",
            type = TransitSegmentType.WALK_TO_STATION
        )

        // Step 3: Segment 2 - Bus Ride: originStation -> destStation
        val busRideSegment = buildBusRideSegment(busLine, originStation, destStation)

        // Step 3: Segment 3 - Walk: destStation -> userDest
        val walk2Segment = fetchWalkingSegment(
            from = destStation.toGeoPoint(),
            to = userDest,
            title = "پیاده‌روی تا مقصد نهایی",
            type = TransitSegmentType.WALK_TO_DEST
        )

        // Step 4: Live Bus Matching & ETA Calculation
        val (matchedBus, busEtaMin) = matchLiveBusAndEta(busLine, originStation, liveBuses, walk1Segment.durationMin)

        val totalDist = walk1Segment.distanceKm + busRideSegment.distanceKm + walk2Segment.distanceKm
        val totalDur = max(walk1Segment.durationMin, busEtaMin.toDouble()) + busRideSegment.durationMin + walk2Segment.durationMin

        return TransitPlan(
            busLine = busLine,
            originStation = originStation,
            destStation = destStation,
            walkToStation = walk1Segment,
            busRide = busRideSegment,
            walkToDest = walk2Segment,
            totalDistanceKm = totalDist,
            totalDurationMin = totalDur,
            matchedBus = matchedBus,
            busEtaMin = busEtaMin
        )
    }

    private suspend fun fetchWalkingSegment(
        from: GeoPoint,
        to: GeoPoint,
        title: String,
        type: TransitSegmentType
    ): TransitSegment {
        return try {
            val coordsParam = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
            val response = OSRMRetrofitClient.api.getWalkingRoute(coordsParam)
            val route = response.routes?.firstOrNull()

            if (route != null && route.geometry?.coordinates != null) {
                val polyPoints = route.geometry.coordinates.map { GeoPoint(it[1], it[0]) }
                val distKm = (route.distance ?: 0.0) / 1000.0
                val durMin = (route.duration ?: 0.0) / 60.0

                TransitSegment(
                    type = type,
                    title = title,
                    description = String.format("%.0f متر (حدود %.0f دقیقه پیاده‌روی)", distKm * 1000, max(1.0, durMin)),
                    distanceKm = distKm,
                    durationMin = max(1.0, durMin),
                    points = polyPoints
                )
            } else {
                fallbackWalkingSegment(from, to, title, type)
            }
        } catch (e: Exception) {
            fallbackWalkingSegment(from, to, title, type)
        }
    }

    private fun fallbackWalkingSegment(
        from: GeoPoint,
        to: GeoPoint,
        title: String,
        type: TransitSegmentType
    ): TransitSegment {
        val distKm = haversineDistanceKm(from, to)
        // Average walking speed ~ 4.5 km/h -> 1 km takes ~13.3 minutes
        val durMin = max(1.0, distKm * 13.3)
        return TransitSegment(
            type = type,
            title = title,
            description = String.format("%.0f متر (حدود %.0f دقیقه پیاده‌روی)", distKm * 1000, durMin),
            distanceKm = distKm,
            durationMin = durMin,
            points = listOf(from, to)
        )
    }

    private fun buildBusRideSegment(
        busLine: BusLine,
        originStation: BusStation,
        destStation: BusStation
    ): TransitSegment {
        // Extract sub-polyline or station points for the bus ride
        val linePoly = busLine.polyline
        val points = if (linePoly.size >= 2) {
            // Find polyline points bounded by origin and dest station
            val startIndex = findClosestPolylineIndex(linePoly, originStation.toGeoPoint())
            val endIndex = findClosestPolylineIndex(linePoly, destStation.toGeoPoint())

            if (startIndex < endIndex) {
                linePoly.subList(startIndex, endIndex + 1)
            } else if (startIndex > endIndex) {
                linePoly.subList(endIndex, startIndex + 1).reversed()
            } else {
                listOf(originStation.toGeoPoint(), destStation.toGeoPoint())
            }
        } else {
            val startIdx = originStation.orderIndex
            val endIdx = destStation.orderIndex
            val stationsSub = if (startIdx <= endIdx) {
                busLine.stations.filter { it.orderIndex in startIdx..endIdx }
            } else {
                busLine.stations.filter { it.orderIndex in endIdx..startIdx }.reversed()
            }
            stationsSub.map { it.toGeoPoint() }
        }

        var totalDistKm = 0.0
        for (i in 0 until points.size - 1) {
            totalDistKm += haversineDistanceKm(points[i], points[i + 1])
        }
        if (totalDistKm == 0.0) {
            totalDistKm = haversineDistanceKm(originStation.toGeoPoint(), destStation.toGeoPoint())
        }

        // Average city bus speed ~28 km/h -> 1 km takes ~2.1 minutes + 1 min dwell time per station
        val stationCount = abs(destStation.orderIndex - originStation.orderIndex)
        val durMin = max(2.0, (totalDistKm / 28.0 * 60.0) + (stationCount * 0.5))

        return TransitSegment(
            type = TransitSegmentType.BUS_RIDE,
            title = "سواری با ${busLine.name}",
            description = String.format("%d ایستگاه (%.1f کیلومتر - حدود %.0f دقیقه)", max(1, stationCount), totalDistKm, durMin),
            distanceKm = totalDistKm,
            durationMin = durMin,
            points = points
        )
    }

    private fun findClosestPolylineIndex(poly: List<GeoPoint>, point: GeoPoint): Int {
        var minIdx = 0
        var minDist = Double.MAX_VALUE
        for (i in poly.indices) {
            val d = haversineDistanceKm(poly[i], point)
            if (d < minDist) {
                minDist = d
                minIdx = i
            }
        }
        return minIdx
    }

    // 4. LIVE BUS MATCHING
    private fun matchLiveBusAndEta(
        busLine: BusLine,
        originStation: BusStation,
        liveBuses: List<LiveBus>,
        walkTimeToStationMin: Double
    ): Pair<LiveBus?, Int> {
        val lineBuses = liveBuses.filter { it.lineId == busLine.id }
        val originGeo = originStation.toGeoPoint()

        val bestBus = lineBuses.minByOrNull { bus ->
            haversineDistanceKm(bus.toGeoPoint(), originGeo)
        } ?: LiveBus(
            busId = "bus_sim_${busLine.number}",
            lineId = busLine.id,
            lineName = busLine.name,
            lat = originStation.lat - 0.008,
            lng = originStation.lng - 0.008,
            speedKmh = 35,
            bearing = 45f
        )

        val distToStationKm = haversineDistanceKm(bestBus.toGeoPoint(), originGeo)
        val busSpeedKmh = if (bestBus.speedKmh > 10) bestBus.speedKmh.toDouble() else 30.0
        val rawBusEtaMin = (distToStationKm / busSpeedKmh * 60.0).roundToInt().coerceAtLeast(2)

        return Pair(bestBus, rawBusEtaMin)
    }
}

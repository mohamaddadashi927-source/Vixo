package com.example.util

import com.example.model.*
import com.example.network.OSRMRetrofitClient
import com.example.repository.FirestoreLinesRepository
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

    // 1. FIND CANDIDATE STATIONS WITH RADIUS FILTER
    fun findCandidateStationsInRadius(
        userPoint: GeoPoint,
        radiusKm: Double,
        lines: List<BusLine> = emptyList()
    ): List<BusStation> {
        return lines.flatMap { line ->
            line.stations.map { station ->
                if (station.lineId.isBlank()) station.copy(lineId = line.id) else station
            }
        }
            .filter { haversineDistanceKm(userPoint, it.toGeoPoint()) <= radiusKm }
            .sortedBy { haversineDistanceKm(userPoint, it.toGeoPoint()) }
    }

    fun findCandidateStations(
        userPoint: GeoPoint,
        lines: List<BusLine> = emptyList(),
        limit: Int = 20
    ): List<BusStation> {
        return lines.flatMap { line ->
            line.stations.map { station ->
                if (station.lineId.isBlank()) station.copy(lineId = line.id) else station
            }
        }
            .sortedBy { haversineDistanceKm(userPoint, it.toGeoPoint()) }
            .take(limit)
    }

    fun findNearestStation(
        userPoint: GeoPoint,
        lines: List<BusLine> = emptyList()
    ): BusStation? {
        return findCandidateStations(userPoint, lines, limit = 1).firstOrNull()
    }

    // Candidate pair evaluator with scoring prioritizing minimal walking distance:
    // Primary weight: walking distance (50.0 per km)
    // Secondary weight: total travel time (1.0 per min) and waiting time (0.1 per min)
    private data class RouteCandidate(
        val originStation: BusStation,
        val destStation: BusStation,
        val busLine: BusLine,
        val score: Double,
        val walk1DistKm: Double,
        val walk2DistKm: Double,
        val busRideDistKm: Double,
        val etaMin: Int
    )

    fun findBestStationPair(
        userOrigin: GeoPoint,
        userDest: GeoPoint,
        lines: List<BusLine> = emptyList(),
        liveBuses: List<LiveBus> = emptyList()
    ): Pair<BusStation, BusStation>? {
        val candidates = evaluateRouteCandidates(userOrigin, userDest, lines, liveBuses)
        val best = candidates.minByOrNull { it.score }
        return if (best != null) Pair(best.originStation, best.destStation) else null
    }

    private fun evaluateRouteCandidates(
        userOrigin: GeoPoint,
        userDest: GeoPoint,
        lines: List<BusLine>,
        liveBuses: List<LiveBus>
    ): List<RouteCandidate> {
        val candidatePairs = mutableListOf<Pair<BusStation, BusStation>>()

        // Step 1: Initial search radius 800m (0.8km)
        var originStops = findCandidateStationsInRadius(userOrigin, 0.8, lines)
        var destStops = findCandidateStationsInRadius(userDest, 0.8, lines)

        collectValidPairs(originStops, destStops, candidatePairs)

        // Step 2: Radius expansion to 2000m (2.0km) if empty
        if (candidatePairs.isEmpty()) {
            originStops = findCandidateStationsInRadius(userOrigin, 2.0, lines)
            destStops = findCandidateStationsInRadius(userDest, 2.0, lines)
            collectValidPairs(originStops, destStops, candidatePairs)
        }

        // Step 3: Top 25 candidate stations across all lines if still empty
        if (candidatePairs.isEmpty()) {
            originStops = findCandidateStations(userOrigin, lines, limit = 25)
            destStops = findCandidateStations(userDest, lines, limit = 25)
            collectValidPairs(originStops, destStops, candidatePairs)
        }

        // Score all candidate pairs:
        // Walking distance is the PRIMARY criterion: (walkingDistance * 50.0) + (totalTime * 1.0) + (waitingTime * 0.1)
        val evaluated = candidatePairs.mapNotNull { (sOrigin, sDest) ->
            val busLine = lines.firstOrNull { it.id == sOrigin.lineId } ?: return@mapNotNull null

            val walk1Dist = haversineDistanceKm(userOrigin, sOrigin.toGeoPoint())
            val walk1Time = walk1Dist * 13.3 // min
            val walk2Dist = haversineDistanceKm(userDest, sDest.toGeoPoint())
            val walk2Time = walk2Dist * 13.3 // min
            val walkingDistance = walk1Dist + walk2Dist

            val stationCount = abs(sDest.orderIndex - sOrigin.orderIndex)
            val busRideDist = haversineDistanceKm(sOrigin.toGeoPoint(), sDest.toGeoPoint())
            val busTime = max(2.0, (busRideDist / 28.0 * 60.0) + (stationCount * 0.5))

            // Match live bus for ETA
            val (_, etaInfo) = matchLiveBusAndEta(busLine, sOrigin, liveBuses, walk1Time)
            val busEta = etaInfo.etaMinutes
            val waitingTime = if (busEta > 0) busEta.toDouble() else 5.0

            val totalTime = max(walk1Time, waitingTime) + busTime + walk2Time
            val score = (walkingDistance * 50.0) + (totalTime * 1.0) + (waitingTime * 0.1)

            RouteCandidate(
                originStation = sOrigin,
                destStation = sDest,
                busLine = busLine,
                score = score,
                walk1DistKm = walk1Dist,
                walk2DistKm = walk2Dist,
                busRideDistKm = busRideDist,
                etaMin = busEta
            )
        }

        return evaluated.sortedBy { it.score }
    }

    private fun collectValidPairs(
        originStops: List<BusStation>,
        destStops: List<BusStation>,
        outList: MutableList<Pair<BusStation, BusStation>>
    ) {
        val addedKeys = mutableSetOf<String>()
        for (sOrigin in originStops) {
            for (sDest in destStops) {
                // STRICT DIRECTION & ORDER INDEX CONTROL: Must be same line, same direction, and origin order < dest order
                if (sOrigin.lineId == sDest.lineId && sOrigin.direction == sDest.direction) {
                    if (sOrigin.orderIndex < sDest.orderIndex) {
                        val pairKey = "${sOrigin.id}_${sDest.id}_${sOrigin.lineId}"
                        if (addedKeys.add(pairKey)) {
                            outList.add(Pair(sOrigin, sDest))
                        }
                    }
                }
            }
        }
    }

    // 2. MATCH BUS LINE
    fun matchBusLine(
        originStation: BusStation,
        destStation: BusStation,
        lines: List<BusLine> = emptyList()
    ): BusLine? {
        val candidateLines = lines.filter { line ->
            val originOnLine = line.stations.find { it.id == originStation.id || (abs(it.lat - originStation.lat) < 0.0001 && abs(it.lng - originStation.lng) < 0.0001) }
            val destOnLine = line.stations.find { it.id == destStation.id || (abs(it.lat - destStation.lat) < 0.0001 && abs(it.lng - destStation.lng) < 0.0001) }

            originOnLine != null && destOnLine != null &&
            originOnLine.direction == destOnLine.direction &&
            originOnLine.orderIndex < destOnLine.orderIndex
        }

        return candidateLines.minByOrNull { line ->
            val o = line.stations.first { it.id == originStation.id || (abs(it.lat - originStation.lat) < 0.0001 && abs(it.lng - originStation.lng) < 0.0001) }
            val d = line.stations.first { it.id == destStation.id || (abs(it.lat - destStation.lat) < 0.0001 && abs(it.lng - destStation.lng) < 0.0001) }
            d.orderIndex - o.orderIndex
        } ?: lines.firstOrNull { it.id == originStation.lineId }
    }

    // 3. GENERATE TRANSIT PLAN (ALWAYS RETURNS NON-NULL PLAN)
    suspend fun calculateTransitPlan(
        userOrigin: GeoPoint,
        userDest: GeoPoint,
        liveBuses: List<LiveBus> = emptyList(),
        lines: List<BusLine> = emptyList()
    ): TransitPlan = withContext(Dispatchers.IO) {
        val safeLines = if (lines.isNotEmpty()) lines else FirestoreLinesRepository().getLines()

        // Evaluated scored candidates
        val candidates = evaluateRouteCandidates(userOrigin, userDest, safeLines, liveBuses)
        val bestCandidate = candidates.minByOrNull { it.score }

        if (bestCandidate != null) {
            return@withContext buildPlanForStations(
                userOrigin,
                userDest,
                bestCandidate.originStation,
                bestCandidate.destStation,
                safeLines,
                liveBuses
            )
        }

        // Fallback Step 3: Find nearest stations on any line
        val originStation = findNearestStation(userOrigin, safeLines)
        val destStation = findNearestStation(userDest, safeLines)

        if (originStation != null && destStation != null) {
            val adjustedDest = if (originStation.id == destStation.id) {
                safeLines.flatMap { it.stations }
                    .filter { it.id != originStation.id }
                    .minByOrNull { haversineDistanceKm(userDest, it.toGeoPoint()) } ?: destStation
            } else destStation

            return@withContext buildPlanForStations(userOrigin, userDest, originStation, adjustedDest, safeLines, liveBuses)
        }

        // Final Fallback Step 4: Direct walking path from origin to destination (never return null)
        val fallbackBusLine = safeLines.firstOrNull() ?: BusLine(
            id = "fallback",
            name = "خط اتوبوس",
            number = "۱۰۱",
            polyline = listOf(userOrigin, userDest)
        )
        return@withContext buildDirectFallbackPlan(userOrigin, userDest, fallbackBusLine)
    }

    private suspend fun buildPlanForStations(
        userOrigin: GeoPoint,
        userDest: GeoPoint,
        originStation: BusStation,
        destStation: BusStation,
        lines: List<BusLine>,
        liveBuses: List<LiveBus>
    ): TransitPlan {
        val busLine = matchBusLine(originStation, destStation, lines)
            ?: lines.firstOrNull { it.id == originStation.lineId }
            ?: lines.firstOrNull()
            ?: BusLine(id = originStation.lineId.ifEmpty { "line_dynamic" }, name = "خط اتوبوس", number = "۱۰۱")

        // Snap stations to nearest point on stored line polyline
        val linePoly = busLine.polyline
        val (snappedOrigin, snappedDest) = if (linePoly.isNotEmpty()) {
            val startIdx = findClosestPolylineIndex(linePoly, originStation.toGeoPoint())
            val endIdx = findClosestPolylineIndex(linePoly, destStation.toGeoPoint())
            val snapOPoint = linePoly[startIdx]
            val snapDPoint = linePoly[endIdx]
            val snapO = originStation.copy(lat = snapOPoint.latitude, lng = snapOPoint.longitude)
            val snapD = destStation.copy(lat = snapDPoint.latitude, lng = snapDPoint.longitude)
            Pair(snapO, snapD)
        } else {
            Pair(originStation, destStation)
        }

        val walk1Segment = fetchWalkingSegment(
            from = userOrigin,
            to = snappedOrigin.toGeoPoint(),
            title = "مسیر پیاده تا ایستگاه مبدأ (${snappedOrigin.name})",
            type = TransitSegmentType.WALK_TO_STATION
        )

        val busRideSegment = buildBusRideSegment(busLine, snappedOrigin, snappedDest)

        val walk2Segment = fetchWalkingSegment(
            from = snappedDest.toGeoPoint(),
            to = userDest,
            title = "مسیر پیاده از ایستگاه مقصد تا مقصد نهایی",
            type = TransitSegmentType.WALK_TO_DEST
        )

        val (matchedBus, etaInfo) = matchLiveBusAndEta(busLine, snappedOrigin, liveBuses, walk1Segment.durationMin)

        val totalDist = walk1Segment.distanceKm + busRideSegment.distanceKm + walk2Segment.distanceKm
        val waitingTimeMin = if (etaInfo.etaMinutes > 0) etaInfo.etaMinutes.toDouble() else 0.0
        val totalDur = max(walk1Segment.durationMin, waitingTimeMin) + busRideSegment.durationMin + walk2Segment.durationMin

        return TransitPlan(
            busLine = busLine,
            originStation = snappedOrigin,
            destStation = snappedDest,
            walkToStation = walk1Segment,
            busRide = busRideSegment,
            walkToDest = walk2Segment,
            totalDistanceKm = totalDist,
            totalDurationMin = totalDur,
            matchedBus = matchedBus,
            busEtaMin = etaInfo.etaMinutes,
            busEtaText = etaInfo.displayText
        )
    }

    private suspend fun buildDirectFallbackPlan(
        userOrigin: GeoPoint,
        userDest: GeoPoint,
        fallbackLine: BusLine
    ): TransitPlan {
        val directSegment = fetchWalkingSegment(
            from = userOrigin,
            to = userDest,
            title = "مسیر مستقیم پیاده‌روی تا مقصد",
            type = TransitSegmentType.WALK_TO_DEST
        )

        val dummyOriginStation = fallbackLine.stations.firstOrNull() ?: BusStation(
            id = "s_fallback_o",
            lat = userOrigin.latitude,
            lng = userOrigin.longitude,
            lineId = fallbackLine.id,
            orderIndex = 0,
            direction = "forward",
            name = "ایستگاه مبدأ"
        )
        val dummyDestStation = fallbackLine.stations.lastOrNull() ?: BusStation(
            id = "s_fallback_d",
            lat = userDest.latitude,
            lng = userDest.longitude,
            lineId = fallbackLine.id,
            orderIndex = 1,
            direction = "forward",
            name = "ایستگاه مقصد"
        )

        val emptySegment = TransitSegment(
            type = TransitSegmentType.BUS_RIDE,
            title = "مسیر در دسترس نیست",
            description = "مسیر در دسترس نیست",
            distanceKm = 0.0,
            durationMin = 0.0,
            points = emptyList()
        )

        val zeroWalkSegment = TransitSegment(
            type = TransitSegmentType.WALK_TO_STATION,
            title = "مبدأ",
            description = "مسیر در دسترس نیست",
            distanceKm = 0.0,
            durationMin = 0.0,
            points = emptyList()
        )

        return TransitPlan(
            busLine = fallbackLine,
            originStation = dummyOriginStation,
            destStation = dummyDestStation,
            walkToStation = zeroWalkSegment,
            busRide = emptySegment,
            walkToDest = directSegment,
            totalDistanceKm = directSegment.distanceKm,
            totalDurationMin = directSegment.durationMin,
            matchedBus = null,
            busEtaMin = -1
        )
    }

    fun formatDistance(distKm: Double): String {
        val meters = (distKm * 1000).roundToInt()
        return if (meters < 1000) {
            "$meters متر"
        } else {
            String.format("%.1f کیلومتر", distKm)
        }
    }

    fun formatDuration(durMin: Double): String {
        val sec = (durMin * 60).roundToInt()
        return if (sec < 60) {
            "$sec ثانیه"
        } else {
            val mins = max(1, durMin.roundToInt())
            "$mins دقیقه"
        }
    }

    fun detectLoopInPolyline(points: List<GeoPoint>): Boolean {
        if (points.size < 5) return false

        // Check for self-intersecting loops or backtracking
        for (i in 0 until points.size - 4) {
            val p1 = points[i]
            for (j in i + 4 until points.size) {
                val p2 = points[j]
                if (haversineDistanceKm(p1, p2) < 0.012) { // < 12 meters
                    return true
                }
            }
        }

        val start = points.first()
        val end = points.last()
        val directDist = haversineDistanceKm(start, end)
        if (directDist > 0.05) { // > 50 meters
            var totalPolyDist = 0.0
            for (k in 0 until points.size - 1) {
                totalPolyDist += haversineDistanceKm(points[k], points[k + 1])
            }
            if (totalPolyDist > 2.0 * directDist) {
                return true
            }
        }

        return false
    }

    private suspend fun fetchWalkingSegment(
        from: GeoPoint,
        to: GeoPoint,
        title: String,
        type: TransitSegmentType
    ): TransitSegment {
        val directDistKm = haversineDistanceKm(from, to)
        val coordsParam = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"

        // Try OSRM with 1 retry
        for (attempt in 1..2) {
            try {
                if (attempt > 1) {
                    kotlinx.coroutines.delay(300)
                }
                val response = OSRMRetrofitClient.api.getWalkingRoute(coordsParam)
                val route = response.routes?.firstOrNull()

                if (route != null && route.geometry?.coordinates != null && route.geometry.coordinates.isNotEmpty()) {
                    val polyPoints = route.geometry.coordinates.map { GeoPoint(it[1], it[0]) }
                    val distMeters = route.distance ?: (directDistKm * 1000.0)
                    val distKm = distMeters / 1000.0
                    val durSec = route.duration ?: (distKm * 13.3 * 60)
                    val durMin = durSec / 60.0

                    val isLoop = detectLoopInPolyline(polyPoints)
                    val isTooLong = distKm > 2.5 * directDistKm

                    if (!isLoop && !isTooLong) {
                        return TransitSegment(
                            type = type,
                            title = title,
                            description = "${formatDistance(distKm)} و ${formatDuration(durMin)}",
                            distanceKm = distKm,
                            durationMin = max(0.5, durMin),
                            points = polyPoints
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("RouteEngine", "OSRM fetch attempt $attempt failed: ${e.message}")
            }
        }

        return smartWalkingFallback(from, to, title, type, directDistKm)
    }

    private fun smartWalkingFallback(
        from: GeoPoint,
        to: GeoPoint,
        title: String,
        type: TransitSegmentType,
        directDistKm: Double
    ): TransitSegment {
        val durMin = max(0.5, (directDistKm * 1000.0 / 80.0) / 60.0) // ~80m/min walking speed (4.8 km/h)
        val isApproximate = directDistKm > 0.3
        val descText = if (isApproximate) {
            "مسیر تقریبی پیاده‌روی (${formatDistance(directDistKm)} و ${formatDuration(durMin)})"
        } else {
            "${formatDistance(directDistKm)} و ${formatDuration(durMin)}"
        }

        return TransitSegment(
            type = type,
            title = title,
            description = descText,
            distanceKm = directDistKm,
            durationMin = durMin,
            points = listOf(from, to) // ALWAYS return at least direct line between points
        )
    }

    private fun buildBusRideSegment(
        busLine: BusLine,
        originStation: BusStation,
        destStation: BusStation
    ): TransitSegment {
        val linePoly = busLine.polyline

        if (linePoly.isEmpty()) {
            android.util.Log.e("RouteEngine", "Path is missing for bus line: ${busLine.id}")
        }

        var startIndex = 0
        var endIndex = 0

        val points = if (linePoly.size >= 2) {
            startIndex = findClosestPolylineIndex(linePoly, originStation.toGeoPoint())
            endIndex = findClosestPolylineIndex(linePoly, destStation.toGeoPoint())

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
            startIndex = startIdx
            endIndex = endIdx
            val stationsSub = if (startIdx <= endIdx) {
                busLine.stations.filter { it.orderIndex in startIdx..endIdx }
            } else {
                busLine.stations.filter { it.orderIndex in endIdx..startIdx }.reversed()
            }
            stationsSub.map { it.toGeoPoint() }
        }

        android.util.Log.d(
            "RouteEngine",
            "selected lineId: ${busLine.id}, direction: ${originStation.direction}, selected stops: [origin: ${originStation.name} -> dest: ${destStation.name}], startIndex: $startIndex, endIndex: $endIndex, path length: ${points.size}"
        )

        // Cumulative distance along the polyline segment
        var totalDistKm = 0.0
        for (i in 0 until points.size - 1) {
            totalDistKm += haversineDistanceKm(points[i], points[i + 1])
        }
        if (totalDistKm == 0.0) {
            totalDistKm = haversineDistanceKm(originStation.toGeoPoint(), destStation.toGeoPoint())
        }

        val stationCount = abs(destStation.orderIndex - originStation.orderIndex)
        val durMin = max(2.0, (totalDistKm / 28.0 * 60.0) + (stationCount * 0.5))

        return TransitSegment(
            type = TransitSegmentType.BUS_RIDE,
            title = "مسیر حرکت اتوبوس (${busLine.name})",
            description = "${formatDistance(totalDistKm)} و ${formatDuration(durMin)} (${max(1, stationCount)} ایستگاه)",
            distanceKm = totalDistKm,
            durationMin = durMin,
            points = points
        )
    }

    fun findClosestPolylineIndex(poly: List<GeoPoint>, point: GeoPoint): Int {
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

    // 4. LIVE BUS MATCHING & ETA CALCULATION
    fun calculateBusEta(
        bus: LiveBus,
        station: BusStation
    ): BusEtaInfo {
        val distKm = haversineDistanceKm(bus.toGeoPoint(), station.toGeoPoint())
        val speedKmh = bus.speed

        if (speedKmh <= 1.0) {
            return BusEtaInfo(
                distanceKm = distKm,
                speedKmh = speedKmh,
                etaMinutes = 0,
                isStopped = true,
                displayText = "در حال توقف"
            )
        }

        // Formula: ETA = distance / speed
        val etaMin = (distKm / speedKmh * 60.0).roundToInt().coerceAtLeast(1)
        val displayText = "اتوبوس تا این ایستگاه ~ $etaMin دقیقه دیگر می‌رسد"

        return BusEtaInfo(
            distanceKm = distKm,
            speedKmh = speedKmh,
            etaMinutes = etaMin,
            isStopped = false,
            displayText = displayText
        )
    }

    fun matchLiveBusAndEta(
        busLine: BusLine,
        originStation: BusStation,
        liveBuses: List<LiveBus>,
        walkTimeToStationMin: Double
    ): Pair<LiveBus?, BusEtaInfo> {
        val lineBuses = liveBuses.filter { bus ->
            val b = bus.lineId.trim()
            val l = busLine.id.trim()
            b == l || b.removeSuffix("_forward").removeSuffix("_backward") == l.removeSuffix("_forward").removeSuffix("_backward")
        }
        if (lineBuses.isEmpty()) {
            return Pair(null, BusEtaInfo(displayText = "زمان رسیدن نامشخص"))
        }

        val originGeo = originStation.toGeoPoint()

        // Filter active buses within 15 km
        val candidateBuses = lineBuses.filter { bus ->
            val dist = haversineDistanceKm(bus.toGeoPoint(), originGeo)
            dist < 15.0
        }

        val bestBus = candidateBuses.minByOrNull { bus ->
            haversineDistanceKm(bus.toGeoPoint(), originGeo)
        } ?: lineBuses.minByOrNull { bus ->
            haversineDistanceKm(bus.toGeoPoint(), originGeo)
        }

        if (bestBus == null) {
            return Pair(null, BusEtaInfo(displayText = "زمان رسیدن نامشخص"))
        }

        val etaInfo = calculateBusEta(bestBus, originStation)
        return Pair(bestBus, etaInfo)
    }
}


package com.example.model

import org.osmdroid.util.GeoPoint

data class Station(
    val id: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val lineId: String = "",
    val orderIndex: Int = 0,
    val direction: String = "forward", // "forward" or "backward"
    val name: String = ""
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lng)
}

// Backward compatibility alias
typealias BusStation = Station

data class BusLine(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val colorHex: String = "#2563EB",
    val startTerminalName: String = "",
    val startTerminalPoint: GeoPoint = GeoPoint(0.0, 0.0),
    val endTerminalName: String = "",
    val endTerminalPoint: GeoPoint = GeoPoint(0.0, 0.0),
    val stations: List<Station> = emptyList(),
    val polyline: List<GeoPoint> = emptyList()
)

enum class TransitSegmentType {
    WALK_TO_STATION,
    BUS_RIDE,
    WALK_TO_DEST
}

data class TransitSegment(
    val type: TransitSegmentType,
    val title: String,
    val description: String,
    val distanceKm: Double,
    val durationMin: Double,
    val points: List<GeoPoint>
)

data class LiveBus(
    val busId: String,
    val lineId: String,
    val lineName: String = "",
    val lat: Double,
    val lng: Double,
    val speedKmh: Int = 30,
    val bearing: Float = 0f,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toGeoPoint(): GeoPoint = GeoPoint(lat, lng)
}

data class TransitPlan(
    val busLine: BusLine,
    val originStation: BusStation,
    val destStation: BusStation,
    val walkToStation: TransitSegment,
    val busRide: TransitSegment,
    val walkToDest: TransitSegment,
    val totalDistanceKm: Double,
    val totalDurationMin: Double,
    val matchedBus: LiveBus?,
    val busEtaMin: Int
)

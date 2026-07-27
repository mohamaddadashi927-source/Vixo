package com.example.model

import com.squareup.moshi.JsonClass
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

@JsonClass(generateAdapter = true)
data class BusStop(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class BusRoute(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val stops: List<BusStop> = emptyList(),
    val coordinates: List<List<Double>> = emptyList() // List of [lng, lat]
)

@JsonClass(generateAdapter = true)
data class BusLocation(
    val routeId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speedKmh: Int = 0,
    val bearing: Float = 0f,
    val lastUpdated: Long = 0L
)

// Proposing a complete structure to parse KML/KMZ files and prepare database seeding.
// This utility handles unzipping KMZ and parsing the nested KML file.
object KMZParser {
    
    fun parseKmz(inputStream: InputStream): ParsedRouteData? {
        try {
            val zipInputStream = ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".kml", ignoreCase = true)) {
                    return parseKml(zipInputStream)
                }
                entry = zipInputStream.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseKml(inputStream: InputStream): ParsedRouteData {
        val coordinates = mutableListOf<List<Double>>()
        val stops = mutableListOf<BusStop>()
        var routeName = "خط اتوبوس الهیه"
        
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(inputStream)
            document.documentElement.normalize()

            // 1. Parse Route Name
            val nameNodes = document.getElementsByTagName("name")
            if (nameNodes.length > 0) {
                routeName = nameNodes.item(0).textContent
            }

            // 2. Parse Route LineString coordinates
            val coordinateNodes = document.getElementsByTagName("coordinates")
            for (i in 0 until coordinateNodes.length) {
                val rawCoords = coordinateNodes.item(i).textContent.trim()
                // Coordinates in KML are usually space-separated triplets: "lng,lat,alt lng,lat,alt ..."
                val points = rawCoords.split("\\s+".toRegex())
                for (point in points) {
                    val parts = point.split(",")
                    if (parts.size >= 2) {
                        val lng = parts[0].toDoubleOrNull()
                        val lat = parts[1].toDoubleOrNull()
                        if (lat != null && lng != null) {
                            coordinates.add(listOf(lng, lat))
                        }
                    }
                }
            }

            // 3. Parse Placemarks representing Stations
            val placemarkNodes = document.getElementsByTagName("Placemark")
            for (i in 0 until placemarkNodes.length) {
                val placemark = placemarkNodes.item(i)
                var stopName = ""
                var lat = 0.0
                var lng = 0.0
                var isStation = false

                val childNodes = placemark.childNodes
                for (j in 0 until childNodes.length) {
                    val child = childNodes.item(j)
                    if (child.nodeName == "name") {
                        stopName = child.textContent
                    }
                    if (child.nodeName == "Point") {
                        isStation = true
                        val pointCoords = child.textContent.trim().split(",")
                        if (pointCoords.size >= 2) {
                            lng = pointCoords[0].toDoubleOrNull() ?: 0.0
                            lat = pointCoords[1].toDoubleOrNull() ?: 0.0
                        }
                    }
                }
                if (isStation && stopName.isNotEmpty()) {
                    stops.add(BusStop(id = "stop_${stops.size + 1}", name = stopName, lat = lat, lng = lng))
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ParsedRouteData(routeName, coordinates, stops)
    }
}

data class ParsedRouteData(
    val name: String,
    val coordinates: List<List<Double>>,
    val stops: List<BusStop>
)

// Fallback preseeded data using real Zanjan routes
object ElahiehPreseededData {
    val routes: List<BusRoute> get() = com.example.data.ZanjanBusData.allLines.map { line ->
        BusRoute(
            id = line.id,
            name = line.name,
            number = line.number,
            stops = line.stations.map { st ->
                BusStop(st.id, st.name, st.lat, st.lng)
            },
            coordinates = line.polyline.map { pt -> listOf(pt.longitude, pt.latitude) }
        )
    }
}

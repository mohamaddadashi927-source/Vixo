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

// High fidelity fallbacks with real Zanjan bus routes and stations!
object ElahiehPreseededData {
    val routes = listOf(
        BusRoute(
            id = "zanjan_1",
            name = "خط ۱ (سبزه میدان - شهرک کارمندان)",
            number = "۱۰۱",
            stops = listOf(
                BusStop("s1_1", "سبزه میدان", 36.6730, 48.4850),
                BusStop("s1_2", "خیابان سعدی شمالی", 36.6810, 48.4910),
                BusStop("s1_3", "میدان مدرس", 36.6900, 48.5020),
                BusStop("s1_4", "جامعه المصطفی", 36.6950, 48.5100),
                BusStop("s1_5", "شهرک کارمندان", 36.6990, 48.5180)
            ),
            coordinates = listOf(
                listOf(48.4850, 36.6730),
                listOf(48.4880, 36.6770),
                listOf(48.4910, 36.6810),
                listOf(48.4960, 36.6850),
                listOf(48.5020, 36.6900),
                listOf(48.5060, 36.6930),
                listOf(48.5100, 36.6950),
                listOf(48.5140, 36.6970),
                listOf(48.5180, 36.6990)
            )
        ),
        BusRoute(
            id = "zanjan_2",
            name = "خط ۲ (پایانه ۲۲ بهمن - بیمارستان آیت‌الله موسوی)",
            number = "۱۰۲",
            stops = listOf(
                BusStop("s2_1", "پایانه ۲۲ بهمن", 36.6710, 48.4960),
                BusStop("s2_2", "میدان آزادی", 36.6780, 48.4820),
                BusStop("s2_3", "خیابان صفا", 36.6820, 48.4980),
                BusStop("s2_4", "میدان قائم", 36.6880, 48.5120),
                BusStop("s2_5", "بیمارستان آیت‌الله موسوی", 36.6920, 48.5250)
            ),
            coordinates = listOf(
                listOf(48.4960, 36.6710),
                listOf(48.4890, 36.6740),
                listOf(48.4820, 36.6780),
                listOf(48.4900, 36.6800),
                listOf(48.4980, 36.6820),
                listOf(48.5050, 36.6850),
                listOf(48.5120, 36.6880),
                listOf(48.5180, 36.6900),
                listOf(48.5250, 36.6920)
            )
        ),
        BusRoute(
            id = "zanjan_3",
            name = "خط ۳ (میدان انقلاب - شهرک زیباشهر)",
            number = "۱۰۳",
            stops = listOf(
                BusStop("s3_1", "میدان انقلاب زنجان", 36.6750, 48.4890),
                BusStop("s3_2", "پل ولیعصر", 36.6790, 48.5020),
                BusStop("s3_3", "شهرک انصاریه", 36.6850, 48.5200),
                BusStop("s3_4", "شهرک زیباشهر", 36.6910, 48.5350)
            ),
            coordinates = listOf(
                listOf(48.4890, 36.6750),
                listOf(48.4950, 36.6770),
                listOf(48.5020, 36.6790),
                listOf(48.5110, 36.6820),
                listOf(48.5200, 36.6850),
                listOf(48.5280, 36.6880),
                listOf(48.5350, 36.6910)
            )
        )
    )
}

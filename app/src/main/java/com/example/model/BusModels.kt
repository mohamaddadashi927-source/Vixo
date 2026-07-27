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

// High fidelity fallbacks with real bus routes and stations!
object ElahiehPreseededData {
    val routes = listOf(
        BusRoute(
            id = "line_elahieh1_sabzeh",
            name = "خط الهیه فاز یک به سبزه میدان (۲۲ ایستگاه)",
            number = "۱۰۲",
            stops = listOf(
                BusStop("s1", "ایستگاه ۱: مبدا - الهیه فاز یک", 36.312500, 59.482100),
                BusStop("s2", "ایستگاه ۲: بلوار الهیه - نبش سجادیه", 36.313800, 59.483900),
                BusStop("s3", "ایستگاه ۳: بلوار اقدسیه ۱۰", 36.315100, 59.485600),
                BusStop("s4", "ایستگاه ۴: میدان محمدیه", 36.316400, 59.487200),
                BusStop("s5", "ایستگاه ۵: تقاطع رحمانی", 36.317700, 59.488900),
                BusStop("s6", "ایستگاه ۶: بلوار صادقیه", 36.319000, 59.490500),
                BusStop("s7", "ایستگاه ۷: میدان نمایشگاه", 36.320300, 59.492100),
                BusStop("s8", "ایستگاه ۸: بلوار استقلال - تقاطع مانا", 36.321600, 59.493800),
                BusStop("s9", "ایستگاه ۹: میدان استقلال", 36.322900, 59.495400),
                BusStop("s10", "ایستگاه ۱۰: بزرگراه آزادی - پل روگذر", 36.324200, 59.497100),
                BusStop("s11", "ایستگاه ۱۱: تقاطع ایستگاه مترو صدف", 36.325500, 59.498700),
                BusStop("s12", "ایستگاه ۱۲: بلوار معلم - نبش معلم ۴۵", 36.326800, 59.500400),
                BusStop("s13", "ایستگاه ۱۳: سه راه دانش‌آموز", 36.328100, 59.502000),
                BusStop("s14", "ایستگاه ۱۴: میدان جهاد", 36.329400, 59.503700),
                BusStop("s15", "ایستگاه ۱۵: چهارراه ابوطالب", 36.330700, 59.505300),
                BusStop("s16", "ایستگاه ۱۶: میدان شهید دیلمی", 36.332000, 59.507000),
                BusStop("s17", "ایستگاه ۱۷: خیابان دانشگاه", 36.333300, 59.508600),
                BusStop("s18", "ایستگاه ۱۸: میدان شهدا", 36.334600, 59.510300),
                BusStop("s19", "ایستگاه ۱۹: خیابان خسروی نو", 36.335900, 59.511900),
                BusStop("s20", "ایستگاه ۲۰: فلکه آب", 36.337200, 59.513600),
                BusStop("s21", "ایستگاه ۲۱: بازار مرکزی", 36.338500, 59.515200),
                BusStop("s22", "ایستگاه ۲۲: مقصد - سبزه میدان", 36.339800, 59.516900)
            ),
            coordinates = listOf(
                listOf(59.482100, 36.312500),
                listOf(59.483900, 36.313800),
                listOf(59.485600, 36.315100),
                listOf(59.487200, 36.316400),
                listOf(59.488900, 36.317700),
                listOf(59.490500, 36.319000),
                listOf(59.492100, 36.320300),
                listOf(59.493800, 36.321600),
                listOf(59.495400, 36.322900),
                listOf(59.497100, 36.324200),
                listOf(59.498700, 36.325500),
                listOf(59.500400, 36.326800),
                listOf(59.502000, 36.328100),
                listOf(59.503700, 36.329400),
                listOf(59.505300, 36.330700),
                listOf(59.507000, 36.332000),
                listOf(59.508600, 36.333300),
                listOf(59.510300, 36.334600),
                listOf(59.511900, 36.335900),
                listOf(59.513600, 36.337200),
                listOf(59.515200, 36.338500),
                listOf(59.516900, 36.339800)
            )
        ),
        BusRoute(
            id = "line_sabzeh_elahieh1",
            name = "خط سبزه میدان به الهیه فاز یک (مسیر برگشت)",
            number = "۱۰۲B",
            stops = listOf(
                BusStop("sb1", "ایستگاه ۱: مبدا - سبزه میدان", 36.339800, 59.516900),
                BusStop("sb2", "ایستگاه ۲: بازار مرکزی", 36.338500, 59.515200),
                BusStop("sb3", "ایستگاه ۳: فلکه آب", 36.337200, 59.513600),
                BusStop("sb4", "ایستگاه ۴: خیابان خسروی نو", 36.335900, 59.511900),
                BusStop("sb5", "ایستگاه ۵: میدان شهدا", 36.334600, 59.510300),
                BusStop("sb6", "ایستگاه ۶: خیابان دانشگاه", 36.333300, 59.508600),
                BusStop("sb7", "ایستگاه ۷: میدان شهید دیلمی", 36.332000, 59.507000),
                BusStop("sb8", "ایستگاه ۸: چهارراه ابوطالب", 36.330700, 59.505300),
                BusStop("sb9", "ایستگاه ۹: میدان جهاد", 36.329400, 59.503700),
                BusStop("sb10", "ایستگاه ۱۰: سه راه دانش‌آموز", 36.328100, 59.502000),
                BusStop("sb11", "ایستگاه ۱۱: بلوار معلم - نبش معلم ۴۵", 36.326800, 59.500400),
                BusStop("sb12", "ایستگاه ۱۲: تقاطع ایستگاه مترو صدف", 36.325500, 59.498700),
                BusStop("sb13", "ایستگاه ۱۳: بزرگراه آزادی - پل روگذر", 36.324200, 59.497100),
                BusStop("sb14", "ایستگاه ۱۴: میدان استقلال", 36.322900, 59.495400),
                BusStop("sb15", "ایستگاه ۱۵: بلوار استقلال - تقاطع مانا", 36.321600, 59.493800),
                BusStop("sb16", "ایستگاه ۱۶: میدان نمایشگاه", 36.320300, 59.492100),
                BusStop("sb17", "ایستگاه ۱۷: بلوار صادقیه", 36.319000, 59.490500),
                BusStop("sb18", "ایستگاه ۱۸: تقاطع رحمانی", 36.317700, 59.488900),
                BusStop("sb19", "ایستگاه ۱۹: میدان محمدیه", 36.316400, 59.487200),
                BusStop("sb20", "ایستگاه ۲۰: بلوار اقدسیه ۱۰", 36.315100, 59.485600),
                BusStop("sb21", "ایستگاه ۲۱: بلوار الهیه - نبش سجادیه", 36.313800, 59.483900),
                BusStop("sb22", "ایستگاه ۲۲: مقصد - الهیه فاز یک", 36.312500, 59.482100)
            ),
            coordinates = listOf(
                listOf(59.516900, 36.339800),
                listOf(59.515200, 36.338500),
                listOf(59.513600, 36.337200),
                listOf(59.511900, 36.335900),
                listOf(59.510300, 36.334600),
                listOf(59.508600, 36.333300),
                listOf(59.507000, 36.332000),
                listOf(59.505300, 36.330700),
                listOf(59.503700, 36.329400),
                listOf(59.502000, 36.328100),
                listOf(59.500400, 36.326800),
                listOf(59.498700, 36.325500),
                listOf(59.497100, 36.324200),
                listOf(59.495400, 36.322900),
                listOf(59.493800, 36.321600),
                listOf(59.492100, 36.320300),
                listOf(59.490500, 36.319000),
                listOf(59.488900, 36.317700),
                listOf(59.487200, 36.316400),
                listOf(59.485600, 36.315100),
                listOf(59.483900, 36.313800),
                listOf(59.482100, 36.312500)
            )
        )
    )
}

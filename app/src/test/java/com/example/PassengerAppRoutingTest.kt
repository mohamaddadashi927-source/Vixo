package com.example

import com.example.model.BusLine
import com.example.model.BusStation
import com.example.model.TransitSegmentType
import com.example.util.RouteEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PassengerAppRoutingTest {

    private val sampleLine by lazy {
        BusLine(
            id = "test_line_1",
            name = "خط آزمایش",
            number = "۱۰۱",
            city = "zanjan_city",
            province = "zanjan",
            colorHex = "#2563EB",
            startTerminalName = "ترمیال مبدأ",
            startTerminalPoint = GeoPoint(36.67, 48.48),
            endTerminalName = "ترمیال مقصد",
            endTerminalPoint = GeoPoint(36.70, 48.52),
            stations = listOf(
                BusStation("st_1", 36.670, 48.480, "test_line_1", 0, "forward", "ایستگاه ۱"), // 100m from origin
                BusStation("st_2", 36.675, 48.485, "test_line_1", 1, "forward", "ایستگاه ۲"), // 500m from origin
                BusStation("st_3", 36.690, 48.500, "test_line_1", 2, "forward", "ایستگاه ۳"), // near dest
                BusStation("st_4", 36.700, 48.520, "test_line_1", 3, "forward", "ایستگاه ۴")  // 100m from dest
            ),
            polyline = listOf(
                GeoPoint(36.670, 48.480),
                GeoPoint(36.675, 48.485),
                GeoPoint(36.690, 48.500),
                GeoPoint(36.700, 48.520)
            )
        )
    }

    @Test
    fun testNearestStationSelection() {
        val userOrigin = GeoPoint(36.6705, 48.4805) // Very close to st_1 (st_1 is ~70m, st_2 is ~600m)
        val userDest = GeoPoint(36.7002, 48.5202)   // Very close to st_4

        val bestPair = RouteEngine.findBestStationPair(
            userOrigin = userOrigin,
            userDest = userDest,
            lines = listOf(sampleLine)
        )

        assertNotNull("Should find a valid station pair", bestPair)
        assertEquals("Should select nearest station st_1 as origin", "st_1", bestPair!!.first.id)
        assertEquals("Should select nearest station st_4 as destination", "st_4", bestPair.second.id)
    }

    @Test
    fun testDirectionalityControl() {
        val reverseLine = BusLine(
            id = "test_line_rev",
            name = "خط یکطرفه",
            number = "۱۰۲",
            stations = listOf(
                BusStation("st_a", 36.670, 48.480, "test_line_rev", 0, "forward", "ایستگاه A"),
                BusStation("st_b", 36.700, 48.520, "test_line_rev", 1, "forward", "ایستگاه B")
            )
        )

        // Reverse trip: userOrigin near st_b (order 1), userDest near st_a (order 0)
        val userOrigin = GeoPoint(36.7001, 48.5201)
        val userDest = GeoPoint(36.6701, 48.4801)

        val bestPair = RouteEngine.findBestStationPair(
            userOrigin = userOrigin,
            userDest = userDest,
            lines = listOf(reverseLine)
        )

        // Station st_b (orderIndex 1) must NOT be selected as origin for a trip to st_a (orderIndex 0)
        if (bestPair != null) {
            assertTrue("st_b (order 1) must not be origin station when traveling to st_a (order 0)", bestPair.first.id != "st_b")
        }
    }

    @Test
    fun testWalkingRouteFallbackNeverEmpty() {
        runBlocking {
            val origin = GeoPoint(36.67, 48.48)
            val dest = GeoPoint(36.75, 48.55) // > 10 km away

            val plan = RouteEngine.calculateTransitPlan(
                userOrigin = origin,
                userDest = dest,
                lines = listOf(sampleLine)
            )

            assertNotNull("Transit plan should be generated", plan)
            plan?.let {
                assertTrue("Walk to station points must never be empty", it.walkToStation.points.isNotEmpty())
                assertTrue("Walk to dest points must never be empty", it.walkToDest.points.isNotEmpty())
                assertTrue("Walk to dest distance must be positive", it.walkToDest.distanceKm > 0.0)
            }
        }
    }
}

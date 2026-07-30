package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeometryJson(
    @Json(name = "coordinates") val coordinates: List<List<Double>>?,
    @Json(name = "type") val type: String?
)

@JsonClass(generateAdapter = true)
data class OSRMRoute(
    @Json(name = "geometry") val geometry: GeometryJson?,
    @Json(name = "distance") val distance: Double?, // in meters
    @Json(name = "duration") val duration: Double? // in seconds
)

@JsonClass(generateAdapter = true)
data class OSRMResponse(
    @Json(name = "routes") val routes: List<OSRMRoute>?
)

interface OSRMApi {
    @GET("route/v1/foot/{coordinates}")
    suspend fun getWalkingRoute(
        @Path("coordinates") coords: String, // format: "lng,lat;lng,lat"
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = true
    ): OSRMResponse

    @GET("route/v1/foot/{coordinates}")
    suspend fun getDrivingRoute(
        @Path("coordinates") coords: String, // format: "lng,lat;lng,lat"
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("steps") steps: Boolean = true
    ): OSRMResponse
}

object OSRMRetrofitClient {
    private const val BASE_URL = "https://router.project-osrm.org/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: OSRMApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(OSRMApi::class.java)
    }
}

package com.example.network

import com.vixo.passenger.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "tools") val tools: List<Map<String, Map<String, String>>>? = null,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: GeminiApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApi::class.java)
    }
}

class GeminiService {
    suspend fun getTravelAdvice(prompt: String, chatHistory: List<Content> = emptyList()): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "خطای سیستم: کلید API جمینی تنظیم نشده است. لطفاً آن را در بخش Secrets اضافه کنید."
        }

        // Add Google Maps grounding tools configuration
        val mapsTool = mapOf("google_maps" to emptyMap<String, String>())
        val toolsList = listOf(mapsTool)

        val systemInstruction = Content(
            parts = listOf(
                Part(
                    text = "شما یک دستیار هوشمند و دلسوز برای سفر با اتوبوس در مشهد (به ویژه منطقه الهیه) هستید. " +
                           "اطلاعات بسیار دقیقی در مورد ایستگاه ها، مراکز تفریحی، تجاری و مسکونی الهیه مشهد بدهید. " +
                           "از ابزار گوگل مپ برای بازیابی اطلاعات مکانی دقیق استفاده کنید. " +
                           "پاسخ ها را بسیار خلاصه، مفید و به زبان شیرین فارسی بنویسید."
                )
            )
        )

        val contents = chatHistory + listOf(Content(parts = listOf(Part(text = prompt))))
        val request = GeminiRequest(
            contents = contents,
            tools = toolsList,
            systemInstruction = systemInstruction
        )

        return try {
            val response = GeminiRetrofitClient.api.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "پاسخی دریافت نشد. دوباره تلاش کنید."
        } catch (e: Exception) {
            "خطا در برقراری ارتباط با دستیار هوشمند: ${e.localizedMessage}"
        }
    }
}

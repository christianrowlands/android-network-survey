package com.craxiom.networksurvey.data.api

import com.craxiom.networksurvey.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


// The API definition for the NS Tower Service
interface Api {
    @GET("cells/area")
    suspend fun getTowers(
        @Query("bbox") bbox: String,
        @Query("radio") radio: String,
        @Query("source") source: String
    ): Response<TowerResponse>

    @GET("cells/area")
    suspend fun getTowers(
        @Query("bbox") bbox: String,
        @Query("radio") radio: String,
        @Query("mcc") mcc: Int,
        @Query("mnc") mnc: Int,
        @Query("source") source: String
    ): Response<TowerResponse>

    @GET("/maptiler/key")
    suspend fun getApiKey(): Response<MapTilerKeyResponse>

    @GET("cells/single")
    suspend fun checkSingleTower(
        @Query("mcc") mcc: Int,
        @Query("mnc") mnc: Int,
        @Query("area") area: Int,
        @Query("cid") cid: Long,
        @Query("radio") radio: String
    ): Response<Tower>

    @GET("cells/search")
    suspend fun searchTowers(
        @Query("mcc") mcc: Int,
        @Query("mnc") mnc: Int,
        @Query("area") area: Int,
        @Query("cid") cid: Long
    ): Response<TowerResponse>
}

val okHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("x-api-key", BuildConfig.NS_API_KEY)
            .build()
        chain.proceed(newRequest)
    }
    .build()

val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl("https://network-survey-gateway-2z7o328z.uc.gateway.dev/")
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

/**
 * The data class that represents a tower from the NS backend. Needs to stay in sync with the API.
 */
data class Tower(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("mcc") val mcc: Int,
    @SerializedName("mnc") val mnc: Int,
    @SerializedName("area") val area: Int,
    @SerializedName("cid") val cid: Long,
    @SerializedName("unit") val unit: Int, // Unit is the PCI for LTE or PCS for UMTS
    @SerializedName("average_signal") val averageSignal: Int,
    @SerializedName("range") val range: Int,
    @SerializedName("samples") val samples: Int,
    @SerializedName("changeable") val changeable: Int,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("radio") val radio: String,
    @SerializedName("source") val source: String
)

/**
 * The data class that represents the response from the NS backend when fetching towers.
 */
data class TowerResponse(
    val count: Int,
    val cells: List<Tower>
)

/**
 * The data class that represents the MapTiler API key response.
 */
data class MapTilerKeyResponse(
    @SerializedName("apiKey") val apiKey: String
)
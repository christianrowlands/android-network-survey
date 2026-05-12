package com.craxiom.networksurvey.data.api

import androidx.compose.runtime.Immutable
import com.craxiom.networksurvey.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query


// The API definition for the NS Tower Service
interface Api {
    @GET("v2/cells/area")
    suspend fun getTowers(
        @Query("bbox") bbox: String,
        @Query("radio") radio: String,
        @Query("source") source: String
    ): Response<TowerResponse>

    @GET("v2/cells/area")
    suspend fun getTowers(
        @Query("bbox") bbox: String,
        @Query("radio") radio: String,
        @Query("mcc") mcc: String,
        @Query("mnc") mnc: String,
        @Query("source") source: String
    ): Response<TowerResponse>

    @GET("/maptiler/key")
    suspend fun getApiKey(): Response<MapTilerKeyResponse>

    @GET("v2/cells/single")
    suspend fun checkSingleTower(
        @Query("mcc") mcc: String,
        @Query("mnc") mnc: String,
        @Query("area") area: Int,
        @Query("cid") cid: Long,
        @Query("radio") radio: String
    ): Response<Tower>

    @GET("v2/cells/search")
    suspend fun searchTowers(
        @Query("mcc") mcc: String,
        @Query("mnc") mnc: String,
        @Query("area") area: Int,
        @Query("cid") cid: Long
    ): Response<TowerResponse>

    @GET("v2/oui")
    suspend fun getOui(
        @Query("mac") mac: String
    ): Response<OuiLookupResponse>

    @POST("v2/oui/batch")
    suspend fun getOuiBatch(
        @Body request: OuiBatchRequest
    ): Response<OuiBatchResponse>

    @GET("v2/oui/dataset")
    suspend fun getOuiDataset(): Response<OuiDatasetResponse>

    @GET("v2/plmn")
    suspend fun getPlmn(
        @Query("mcc") mcc: String,
        @Query("mnc") mnc: String
    ): Response<PlmnSingleResponse>

    @GET("v2/plmn/search")
    suspend fun searchPlmn(
        @Query("q") q: String? = null,
        @Query("mcc") mcc: String? = null,
        @Query("mnc") mnc: String? = null,
        @Query("country") country: String? = null,
        @Query("iso") iso: String? = null,
        @Query("operator") operator: String? = null,
        @Query("region") region: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<PlmnSearchResponse>
}

private const val NS_TOWER_BASE_URL = "https://network-survey-gateway-2z7o328z.uc.gateway.dev/"
private val NS_TOWER_HOST = NS_TOWER_BASE_URL.toHttpUrl().host

val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    // Attach the API key only for requests to the NS tower gateway. OkHttp follows redirects
    // by default and does NOT strip arbitrary headers on cross-host redirects, sending
    // `x-api-key` to a redirect destination would leak the key. Gating the header on host
    // match is the defensive posture.
    .addInterceptor { chain ->
        val request = chain.request()
        val newRequest = if (request.url.host.equals(NS_TOWER_HOST, ignoreCase = true)) {
            request.newBuilder()
                .header("x-api-key", BuildConfig.NS_API_KEY)
                .build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }
    .build()

val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl(NS_TOWER_BASE_URL)
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

/**
 * The data class that represents a tower from the NS backend. Needs to stay in sync with the API.
 */
data class Tower(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("mcc") val mcc: String,
    @SerializedName("mnc") val mnc: String,
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
    @SerializedName("source") val source: String,
    @SerializedName("comments") val comments: String? = null
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

/**
 * Single OUI lookup result item from the tower service.
 *
 * Returned both as the body of [Api.getOui] (inside [OuiLookupResponse.result]) and as each
 * element of [OuiBatchResponse.results]. The server always responds 200. Classification flows
 * through the [isUnknown] / [reason] fields.
 *
 * String and boxed-Int fields are declared nullable because Gson bypasses Kotlin's non-null
 * checks at deserialization; a server that unexpectedly omits a field or sends `null` would
 * otherwise produce a hidden NPE on first field access. Callers must tolerate null strings.
 */
data class OuiLookupResult(
    @SerializedName("mac") val mac: String? = null,
    @SerializedName("vendor") val vendor: String? = null,
    @SerializedName("matched_prefix_len") val matchedPrefixLen: Int = 0,
    @SerializedName("is_locally_administered") val isLocallyAdministered: Boolean = false,
    @SerializedName("is_multicast") val isMulticast: Boolean = false,
    @SerializedName("is_unknown") val isUnknown: Boolean = false,
    @SerializedName("reason") val reason: String? = null
)

/**
 * Response body for the single-MAC OUI endpoint (`GET /v2/oui`).
 */
data class OuiLookupResponse(
    @SerializedName("dataset_version") val datasetVersion: String? = null,
    @SerializedName("result") val result: OuiLookupResult? = null
)

/**
 * Request body for the batch OUI endpoint (`POST /v2/oui/batch`).
 * Server caps the list at 1000 entries and the full request at 256 KB.
 */
data class OuiBatchRequest(
    @SerializedName("macs") val macs: List<String>
)

/**
 * Response body for the batch OUI endpoint (`POST /v2/oui/batch`).
 * Results preserve the input order.
 */
data class OuiBatchResponse(
    @SerializedName("dataset_version") val datasetVersion: String? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("results") val results: List<OuiLookupResult> = emptyList()
)

/**
 * Response body for the dataset status endpoint (`GET /v2/oui/dataset`).
 * The [loaded] flag is the signal, when false, the server failed to load the IEEE registries.
 */
data class OuiDatasetResponse(
    @SerializedName("dataset_version") val datasetVersion: String? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("ma_l_count") val maLCount: Int = 0,
    @SerializedName("ma_m_count") val maMCount: Int = 0,
    @SerializedName("ma_s_count") val maSCount: Int = 0,
    @SerializedName("loaded") val loaded: Boolean = false
)

/**
 * One operator entry from the embedded MCC-MNC dataset, returned by the PLMN endpoints.
 * Mirrors `model.PlmnRecord` in the tower-service backend. Multiple records can share an
 * MCC+MNC pair (shared networks, MNO/MVNO splits, brand variants); see [PlmnLookupResult].
 *
 * String fields are nullable because Gson bypasses Kotlin's non-null checks at deserialization,
 * and a server that unexpectedly omits a `omitempty` field would otherwise produce a hidden NPE.
 */
@Immutable
data class PlmnRecord(
    @SerializedName("mcc") val mcc: String,
    @SerializedName("mnc") val mnc: String,
    @SerializedName("plmn") val plmn: String,
    @SerializedName("region") val region: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("iso") val iso: String? = null,
    @SerializedName("operator") val operator: String? = null,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("tadig") val tadig: String? = null
)

/**
 * The per-PLMN classification returned by `GET /v2/plmn`. A "no match" comes back as HTTP 200
 * with [isUnknown] = true and an empty [records] list (NOT a 404).
 */
data class PlmnLookupResult(
    @SerializedName("mcc") val mcc: String? = null,
    @SerializedName("mnc") val mnc: String? = null,
    @SerializedName("records") val records: List<PlmnRecord> = emptyList(),
    @SerializedName("is_unknown") val isUnknown: Boolean = false,
    @SerializedName("reason") val reason: String? = null
)

/**
 * Response body for the single-PLMN resolve endpoint (`GET /v2/plmn`).
 */
data class PlmnSingleResponse(
    @SerializedName("dataset_version") val datasetVersion: String? = null,
    @SerializedName("result") val result: PlmnLookupResult = PlmnLookupResult()
)

/**
 * Response body for the search endpoint (`GET /v2/plmn/search`). [truncated] is true when the
 * dataset had more matches than were returned (server-side cap).
 */
data class PlmnSearchResponse(
    @SerializedName("dataset_version") val datasetVersion: String? = null,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("truncated") val truncated: Boolean = false,
    @SerializedName("results") val results: List<PlmnRecord> = emptyList()
)
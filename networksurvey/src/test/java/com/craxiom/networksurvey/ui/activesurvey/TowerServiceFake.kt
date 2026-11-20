package com.craxiom.networksurvey.ui.activesurvey

import com.craxiom.networksurvey.data.api.Api
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * A local stand-in for the tower service's `cells/single` endpoint. Every request answers with
 * [code]; a 200 carries a tower at [TOWER_LATITUDE], [TOWER_LONGITUDE], and [DISCONNECT] drops the
 * connection instead of answering. [onRequest] runs before each answer, for a test that changes
 * something while a check waits on the service.
 */
class TowerServiceFake : AutoCloseable {

    private val server = MockWebServer()

    @Volatile
    var code: Int = 404

    /** Runs on the server's thread when a request arrives, before it is answered. */
    @Volatile
    var onRequest: () -> Unit = {}

    /** How many requests the service has received. */
    val requestCount: Int get() = server.requestCount

    /** An [Api] that talks to this fake. */
    val api: Api

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                onRequest()
                return when (code) {
                    DISCONNECT -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                    200 -> MockResponse().setResponseCode(200).setBody(TOWER_JSON)
                    else -> MockResponse().setResponseCode(code)
                }
            }
        }
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }

    override fun close() = server.shutdown()

    companion object {
        /** A [code] that drops the connection, the way a phone without network fails. */
        const val DISCONNECT = -1

        const val TOWER_LATITUDE = 38.9
        const val TOWER_LONGITUDE = -77.04

        private const val TOWER_JSON = """{"lat":$TOWER_LATITUDE,"lon":$TOWER_LONGITUDE,"mcc":"310","mnc":"260",
            "area":12345,"cid":25600103,"unit":1,"average_signal":-80,"range":500,"samples":3,"changeable":1,
            "created_at":0,"updated_at":0,"radio":"LTE","source":"ocid"}"""
    }
}

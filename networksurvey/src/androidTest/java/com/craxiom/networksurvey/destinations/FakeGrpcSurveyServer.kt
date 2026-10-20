package com.craxiom.networksurvey.destinations

import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.CdmaRecord
import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.GnssRecord
import com.craxiom.messaging.GsmRecord
import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.PhoneState
import com.craxiom.messaging.UmtsRecord
import com.craxiom.messaging.WifiBeaconRecord
import com.craxiom.messaging.grpc.BluetoothSurveyResponse
import com.craxiom.messaging.grpc.CdmaSurveyResponse
import com.craxiom.messaging.grpc.ConnectionHandshakeGrpc
import com.craxiom.messaging.grpc.ConnectionReply
import com.craxiom.messaging.grpc.ConnectionRequest
import com.craxiom.messaging.grpc.DeviceStatusGrpc
import com.craxiom.messaging.grpc.GnssSurveyResponse
import com.craxiom.messaging.grpc.GsmSurveyResponse
import com.craxiom.messaging.grpc.LteSurveyResponse
import com.craxiom.messaging.grpc.NrSurveyResponse
import com.craxiom.messaging.grpc.PhoneStateResponse
import com.craxiom.messaging.grpc.StatusUpdateReply
import com.craxiom.messaging.grpc.UmtsSurveyResponse
import com.craxiom.messaging.grpc.WifiBeaconSurveyResponse
import com.craxiom.messaging.grpc.WirelessSurveyGrpc
import io.grpc.InsecureServerCredentials
import io.grpc.Server
import io.grpc.okhttp.OkHttpServerBuilder
import io.grpc.stub.StreamObserver
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * An in-process Network Survey gRPC server for the instrumented destination tests. It implements the
 * connection handshake, the device status stream and every survey stream the app opens, and keeps
 * each record it receives in [received].
 */
class FakeGrpcSurveyServer(val port: Int = DEFAULT_PORT) {

    /** One received record: the stream it arrived on and its text form. */
    data class Received(val stream: Stream, val text: String)

    enum class Stream { GSM, CDMA, UMTS, LTE, NR, PHONE_STATE, WIFI_BEACON, BLUETOOTH, GNSS, DEVICE_STATUS }

    val received: MutableList<Received> = CopyOnWriteArrayList()

    /** How many connection handshakes the app has made with this server. */
    val handshakes = AtomicInteger(0)

    private var server: Server? = null

    fun start() {
        server = OkHttpServerBuilder.forPort(port, InsecureServerCredentials.create())
            .addService(handshakeService)
            .addService(wirelessSurveyService)
            .addService(deviceStatusService)
            .build()
            .start()
    }

    fun stop() {
        server?.shutdownNow()?.awaitTermination(5, TimeUnit.SECONDS)
        server = null
    }

    fun receivedOn(stream: Stream): List<Received> = received.filter { it.stream == stream }

    private val handshakeService = object : ConnectionHandshakeGrpc.ConnectionHandshakeImplBase() {
        override fun startConnection(request: ConnectionRequest, responseObserver: StreamObserver<ConnectionReply>) {
            handshakes.incrementAndGet()
            responseObserver.onNext(ConnectionReply.newBuilder().setConnectionAccept(true).build())
            responseObserver.onCompleted()
        }
    }

    private val deviceStatusService = object : DeviceStatusGrpc.DeviceStatusImplBase() {
        override fun statusUpdate(responseObserver: StreamObserver<StatusUpdateReply>): StreamObserver<DeviceStatus> =
            collect(Stream.DEVICE_STATUS, responseObserver, StatusUpdateReply.getDefaultInstance())
    }

    private val wirelessSurveyService = object : WirelessSurveyGrpc.WirelessSurveyImplBase() {
        override fun streamGsmSurvey(r: StreamObserver<GsmSurveyResponse>): StreamObserver<GsmRecord> =
            collect(Stream.GSM, r, GsmSurveyResponse.getDefaultInstance())

        override fun streamCdmaSurvey(r: StreamObserver<CdmaSurveyResponse>): StreamObserver<CdmaRecord> =
            collect(Stream.CDMA, r, CdmaSurveyResponse.getDefaultInstance())

        override fun streamUmtsSurvey(r: StreamObserver<UmtsSurveyResponse>): StreamObserver<UmtsRecord> =
            collect(Stream.UMTS, r, UmtsSurveyResponse.getDefaultInstance())

        override fun streamLteSurvey(r: StreamObserver<LteSurveyResponse>): StreamObserver<LteRecord> =
            collect(Stream.LTE, r, LteSurveyResponse.getDefaultInstance())

        override fun streamNrSurvey(r: StreamObserver<NrSurveyResponse>): StreamObserver<NrRecord> =
            collect(Stream.NR, r, NrSurveyResponse.getDefaultInstance())

        override fun streamPhoneState(r: StreamObserver<PhoneStateResponse>): StreamObserver<PhoneState> =
            collect(Stream.PHONE_STATE, r, PhoneStateResponse.getDefaultInstance())

        override fun streamWifiBeaconSurvey(r: StreamObserver<WifiBeaconSurveyResponse>): StreamObserver<WifiBeaconRecord> =
            collect(Stream.WIFI_BEACON, r, WifiBeaconSurveyResponse.getDefaultInstance())

        override fun streamBluetoothSurvey(r: StreamObserver<BluetoothSurveyResponse>): StreamObserver<BluetoothRecord> =
            collect(Stream.BLUETOOTH, r, BluetoothSurveyResponse.getDefaultInstance())

        override fun streamGnssSurvey(r: StreamObserver<GnssSurveyResponse>): StreamObserver<GnssRecord> =
            collect(Stream.GNSS, r, GnssSurveyResponse.getDefaultInstance())
    }

    /** Builds a client-streaming receiver that records every message and replies once at the end. */
    private fun <T : Any, R : Any> collect(stream: Stream, responseObserver: StreamObserver<R>, reply: R): StreamObserver<T> =
        object : StreamObserver<T> {
            override fun onNext(value: T) {
                received.add(Received(stream, value.toString()))
            }

            override fun onError(t: Throwable) {
                // The client canceled the stream (a type was removed or the survey stopped).
            }

            override fun onCompleted() {
                responseObserver.onNext(reply)
                responseObserver.onCompleted()
            }
        }

    companion object {
        const val DEFAULT_PORT = 26210
    }
}

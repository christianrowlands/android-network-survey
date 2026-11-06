package com.craxiom.networksurvey.services

import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.DestinationCapabilities
import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Test

/** The gRPC thread pool fits every stream, Wi-Fi clients and device status included. */
class GrpcStreamsTest {

    @Test
    fun `the pool fits device status and every record type's streams`() {
        // Device status, 5 cellular, phone state, Wi-Fi, Bluetooth, GNSS, Wi-Fi clients, probe requests
        assertEquals(12, GrpcStreams.MAX_CONCURRENT)
    }

    @Test
    fun `exactly the types gRPC takes open streams`() {
        val accepted = DestinationCapabilities.accepts(Destination.GRPC)
        for (type in RecordType.entries) {
            assertEquals(type.wireName, type in accepted, GrpcStreams.forType(type) > 0)
        }
    }
}

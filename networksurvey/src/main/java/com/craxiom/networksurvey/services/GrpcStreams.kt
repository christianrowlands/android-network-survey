package com.craxiom.networksurvey.services

import com.craxiom.networksurvey.survey.model.RecordType

/**
 * How many gRPC streams [GrpcConnectionService] opens. Each stream's task holds one pool thread for
 * the life of the stream, so the pool has to fit all of them at once: a stream without a thread
 * never opens, while its queue keeps taking records until it is full. Every record type has a case,
 * so a new type has to say how many streams it needs.
 */
object GrpcStreams {
    /** Device status, which every connection carries. */
    private const val DEVICE_STATUS_STREAMS = 1

    /** @return The streams [type] opens while it is streamed. */
    fun forType(type: RecordType): Int = when (type) {
        // GSM, CDMA, UMTS, LTE and NR
        RecordType.CELLULAR -> 5
        RecordType.PHONE_STATE, RecordType.WIFI, RecordType.BLUETOOTH, RecordType.GNSS -> 1
        // Clients and probe requests
        RecordType.WIFI_CLIENTS -> 2
        // Files only
        RecordType.CDR, RecordType.WIFI_RAW -> 0
    }

    /** The most streams one connection can have open, with every record type streaming. */
    @JvmField
    val MAX_CONCURRENT: Int = DEVICE_STATUS_STREAMS + RecordType.entries.sumOf { forType(it) }
}

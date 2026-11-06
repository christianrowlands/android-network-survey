package com.craxiom.networksurvey.scout.fake

import android.content.Context
import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.BoardStatus
import com.craxiom.networksurvey.scout.link.ScoutFraming

/**
 * The NS Scout firmware's wire fixtures (src/scoutFixtures/scout-fixtures, copied from
 * network-survey-esp32), loaded through [load] by a path such as `proto/board_info.bin`. Debug
 * builds read them from assets; unit tests from resources. Debug only: release builds have
 * neither this class nor the files.
 */
class ScoutFixtures(private val load: (String) -> ByteArray) {
    /** The firmware's `BoardInfo` example: an ESP32-C5-DevKitC-1, short ID ABCD, firmware 0.1.0. */
    val boardInfo: BoardInfo by lazy { BoardInfo.parseFrom(load(BOARD_INFO)) }

    /** The firmware's `BoardStatus` example: surveying on channel 149, battery 3.9 V. */
    val boardStatus: BoardStatus by lazy { BoardStatus.parseFrom(load(BOARD_STATUS)) }

    /** The messages of every Records frame fixture, in the order the board sends them. */
    val recordFrames: List<List<ByteArray>> by lazy { RECORD_FRAMES.map { ScoutFraming.decode(load(it)).messages } }

    /** One encoded `RawFrame`: a beacon header cut at a 40-byte snaplen. */
    val rawFrame: ByteArray by lazy { load(RAW_FRAME) }

    /** @return The bytes of the fixture at [path] (relative to `scout-fixtures/`). */
    fun bytes(path: String): ByteArray = load(path)

    companion object {
        /** The folder the fixtures live in, in assets and on the test classpath. */
        const val ROOT = "scout-fixtures"

        const val BOARD_INFO = "proto/board_info.bin"
        const val BOARD_STATUS = "proto/board_status.bin"
        const val RAW_FRAME = "proto/raw_frame.bin"

        /** The Records frames at MTU 185 (ap+client, probe+ble, gps), then the MTU 517 frame. */
        val RECORD_FRAMES = listOf(
            "framing/records_mtu185_frame0.bin",
            "framing/records_mtu185_frame1.bin",
            "framing/records_mtu185_frame2.bin",
            "framing/records_mtu517_seq9.bin",
            "framing/records_exact_fit_mtu185_seq42.bin",
        )

        /** @return The fixtures from the debug app's assets. */
        @JvmStatic
        fun fromAssets(context: Context): ScoutFixtures =
            ScoutFixtures { path -> context.assets.open("$ROOT/$path").use { it.readBytes() } }

        /** @return The fixtures from the classpath, for unit tests. */
        @JvmStatic
        fun fromClasspath(): ScoutFixtures = ScoutFixtures { path ->
            val stream = ScoutFixtures::class.java.getResourceAsStream("/$ROOT/$path")
                ?: throw IllegalArgumentException("No Scout fixture $path on the classpath")
            stream.use { it.readBytes() }
        }
    }
}

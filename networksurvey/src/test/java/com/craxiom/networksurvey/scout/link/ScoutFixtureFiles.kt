package com.craxiom.networksurvey.scout.link

/**
 * Reads the NS Scout firmware fixtures (src/scoutFixtures/scout-fixtures, a test resources folder)
 * for the decoder tests.
 */
object ScoutFixtureFiles {
    private const val ROOT = "/scout-fixtures/"

    /** @return The bytes of the fixture at [path], for example `proto/board_info.bin`. */
    fun bytes(path: String): ByteArray {
        val stream = ScoutFixtureFiles::class.java.getResourceAsStream(ROOT + path)
            ?: throw IllegalArgumentException("No Scout fixture $path")
        return stream.use { it.readBytes() }
    }

    /** 2026-01-01T00:00:00.123Z, the base time of every fixture (proto_roundtrip/main.c T0). */
    const val T0 = 1_767_225_600_123L
}

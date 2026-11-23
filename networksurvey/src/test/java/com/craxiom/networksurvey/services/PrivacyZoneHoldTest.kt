package com.craxiom.networksurvey.services

import com.craxiom.messaging.DeviceStatus
import com.craxiom.messaging.DeviceStatusData
import com.craxiom.networksurvey.privacy.PrivacyZone
import com.craxiom.networksurvey.privacy.PrivacyZoneRulesTest.Companion.north
import com.craxiom.networksurvey.privacy.PrivacyZoneTestGate
import com.craxiom.networksurvey.privacy.PrivacyZoneTestGate.Companion.HOME
import com.craxiom.networksurvey.scout.wifi.PhoneFix
import com.google.protobuf.BoolValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A record placed by its own fix (an NS Scout record) is held when that fix is inside a zone, or
 * when the phone is in one; a coarse fix or none falls back to the phone's decision. Device status
 * loses every location while holding, and otherwise each location block that lies inside a zone.
 */
@RunWith(RobolectricTestRunner::class)
class PrivacyZoneHoldTest {

    private val zones = PrivacyZoneTestGate()
    private val hold = PrivacyZoneHold(zones.gate)

    @Test
    fun `a record heard inside a zone is held while the phone is outside`() {
        zones.leave()
        assertTrue(hold.holdsFix(fix(meters = 100.0, accuracy = 10f)))
        assertFalse(hold.holdsFix(fix(meters = 1_000.0, accuracy = 10f)))
    }

    @Test
    fun `the record's accuracy widens the zone`() {
        zones.leave()
        assertTrue(hold.holdsFix(fix(meters = 300.0, accuracy = 60f)))
    }

    @Test
    fun `a coarse or missing fix leaves it to the phone's decision`() {
        zones.leave()
        assertFalse(hold.holdsFix(fix(meters = 0.0, accuracy = 800f)))
        assertFalse(hold.holdsFix(fix(meters = 0.0, accuracy = null)))
        assertFalse(hold.holdsFix(null))

        zones.enter()
        assertTrue(hold.holdsFix(null))
        assertTrue(hold.holdsFix(fix(meters = 5_000.0, accuracy = 10f)))
    }

    @Test
    fun `no zones means nothing is held by place`() {
        zones.zones.value = emptyList()
        assertFalse(hold.holdsFix(fix(meters = 0.0, accuracy = 10f)))
    }

    @Test
    fun `outside the hold a gnss block inside a zone is cleared and the primary block kept`() {
        zones.leave()
        val (awayLat, awayLon) = north(HOME, AWAY_METERS)
        val data = hold.withoutLocationInZone(status(primary = AWAY_METERS, gnss = 0.0)).data

        assertEquals(awayLat, data.latitude, 0.0)
        assertEquals(awayLon, data.longitude, 0.0)
        assertEquals(ACCURACY, data.accuracy)
        assertTrue(data.hasMockLocation())
        assertEquals(0.0, data.gnssLatitude, 0.0)
        assertEquals(0.0, data.gnssLongitude, 0.0)
        assertEquals(0, data.gnssAccuracy)
        assertEquals(0, data.gnssAge)
        assertEquals("Pixel", data.deviceModel)
    }

    @Test
    fun `outside the hold a network block inside a zone is cleared`() {
        zones.leave()
        val data = hold.withoutLocationInZone(status(primary = AWAY_METERS, network = 100.0)).data

        assertEquals(0.0, data.networkLatitude, 0.0)
        assertEquals(0.0, data.networkLongitude, 0.0)
        assertEquals(0f, data.networkAltitude, 0f)
        assertEquals(0, data.networkAccuracy)
        assertEquals(0, data.networkAge)
        assertTrue(data.latitude != 0.0)
    }

    @Test
    fun `blocks outside every zone are kept`() {
        zones.leave()
        val status = status(primary = AWAY_METERS, gnss = AWAY_METERS, network = AWAY_METERS)
        assertSame(status, hold.withoutLocationInZone(status))
    }

    @Test
    fun `a coarse block whose circle touches a zone is cleared`() {
        zones.leave()
        // 1,000 m out with 800 m accuracy: coarser than holdsPlace decides on, but it may be inside
        val data = hold.withoutLocationInZone(status(primary = AWAY_METERS, network = 1_000.0, accuracy = 800)).data
        assertEquals(0.0, data.networkLatitude, 0.0)
    }

    @Test
    fun `a missing accuracy counts as zero`() {
        zones.leave()
        assertEquals(0.0, hold.withoutLocationInZone(status(primary = AWAY_METERS, gnss = 100.0, accuracy = 0)).data.gnssLatitude, 0.0)
        val justOutside = status(primary = AWAY_METERS, gnss = 300.0, accuracy = 0)
        assertSame(justOutside, hold.withoutLocationInZone(justOutside))
    }

    @Test
    fun `a block at 0, 0 is left alone`() {
        zones.zones.value = listOf(HOME, NULL_ISLAND)
        zones.leave()
        val status = status(primary = AWAY_METERS)
        assertSame(status, hold.withoutLocationInZone(status))
    }

    @Test
    fun `no zones means device status is unchanged`() {
        zones.zones.value = emptyList()
        val status = status(primary = 0.0, gnss = 0.0, network = 0.0)
        assertSame(status, hold.withoutLocationInZone(status))
    }

    @Test
    fun `while holding device status loses every location`() {
        zones.enter()
        val data = hold.withoutLocationInZone(status(primary = AWAY_METERS, gnss = AWAY_METERS, network = AWAY_METERS)).data

        assertEquals(0.0, data.latitude, 0.0)
        assertEquals(0, data.accuracy)
        assertFalse(data.hasMockLocation())
        assertEquals(0.0, data.gnssLatitude, 0.0)
        assertEquals(0.0, data.networkLongitude, 0.0)
        assertEquals("Pixel", data.deviceModel)
    }

    /**
     * A device status with each location block [primary], [gnss] and [network] meters north of
     * [HOME]'s center, or no location (0, 0) for a block given null.
     */
    private fun status(primary: Double? = null, gnss: Double? = null, network: Double? = null, accuracy: Int = ACCURACY): DeviceStatus {
        val data = DeviceStatusData.newBuilder().setDeviceModel("Pixel")
        primary?.let { north(HOME, it) }?.let { (lat, lon) ->
            data.setLatitude(lat).setLongitude(lon).setAltitude(ALTITUDE).setAccuracy(accuracy).setLocationAge(AGE)
                .setMockLocation(BoolValue.of(true))
        }
        gnss?.let { north(HOME, it) }?.let { (lat, lon) ->
            data.setGnssLatitude(lat).setGnssLongitude(lon).setGnssAltitude(ALTITUDE).setGnssAccuracy(accuracy).setGnssAge(AGE)
        }
        network?.let { north(HOME, it) }?.let { (lat, lon) ->
            data.setNetworkLatitude(lat).setNetworkLongitude(lon).setNetworkAltitude(ALTITUDE).setNetworkAccuracy(accuracy)
                .setNetworkAge(AGE)
        }
        return DeviceStatus.newBuilder().setData(data).build()
    }

    private fun fix(meters: Double, accuracy: Float?): PhoneFix {
        val (lat, lon) = north(HOME, meters)
        return PhoneFix(timeMs = 0L, latitude = lat, longitude = lon, altitude = null, accuracy = accuracy, speed = null)
    }

    private companion object {
        const val AWAY_METERS = 5_000.0
        const val ACCURACY = 10
        const val ALTITUDE = 1_600f
        const val AGE = 2_000

        /** A zone around 0, 0, so a block without a location would be inside it if it counted. */
        val NULL_ISLAND = PrivacyZone("null", "Null Island", 0.0001, 0.0001, 250)
    }
}

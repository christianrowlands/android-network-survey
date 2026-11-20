package com.craxiom.networksurvey.ui.newtowers

import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.TowerKey
import com.craxiom.networksurvey.util.SignalBuckets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How the New towers page groups, sorts and reads its saved finds. */
class NewTowerFindsTest {

    @Test
    fun finds_areGroupedBySession_newestSessionFirst_newestFindFirst() {
        val sessions = groupBySession(
            listOf(
                find(id = 1, firstSeen = 1_100, session = 1_000),
                find(id = 2, firstSeen = 5_300, session = 5_000),
                find(id = 3, firstSeen = 1_400, session = 1_000),
                find(id = 4, firstSeen = 5_100, session = 5_000),
                find(id = 5, firstSeen = 3_000, session = 3_000),
            )
        )

        assertEquals(listOf(5_000L, 3_000L, 1_000L), sessions.map { it.startedAt })
        assertEquals(listOf(listOf(2L, 4L), listOf(5L), listOf(3L, 1L)), sessions.map { session -> session.finds.map { it.id } })
    }

    @Test
    fun findsFoundAtTheSameMoment_keepAStableOrder() {
        val sessions = groupBySession(listOf(find(id = 7, firstSeen = 2_000), find(id = 9, firstSeen = 2_000)))
        assertEquals(listOf(9L, 7L), sessions.single().finds.map { it.id })
    }

    @Test
    fun noFinds_noSessions() {
        assertEquals(emptyList<NewTowerSession>(), groupBySession(emptyList()))
    }

    @Test
    fun aFind_isCheckedAgainWithItsOwnTowerKey() {
        val find = find(id = 1, radio = "NR").apply {
            mnc = "01"
            area = 77
            cellId = 123_456_789L
        }
        assertEquals(TowerKey("310", "01", 77, 123_456_789L, CellularProtocol.NR), find.towerKey())
    }

    @Test
    fun anUnknownRadio_hasNoTowerKey() {
        assertNull(find(id = 1, radio = "WIMAX").towerKey())
    }

    @Test
    fun theBand_readsPerTechnology() {
        assertEquals(66, find(id = 1, radio = "LTE").apply { band = "66" }.lteBand)
        assertNull(find(id = 1, radio = "LTE").apply { band = "66" }.nrBands)
        assertEquals("n77/n78", find(id = 1, radio = "NR").apply { band = "n77/n78" }.nrBands)
        assertNull(find(id = 1, radio = "NR").apply { band = "n78" }.lteBand)
    }

    @Test
    fun anLteFind_namesItsEnbAndSector_otherFindsTheirCellId() {
        val lte = find(id = 1, radio = "LTE").apply { cellId = 129_595L * 256 + 3 }
        assertEquals(129_595 to 3, lte.enbSector)
        assertNull(lte.identity)

        val gsm = find(id = 2, radio = "GSM").apply { cellId = 4_321 }
        assertNull(gsm.enbSector)
        assertEquals("CID", gsm.identity?.label)
        assertEquals("4321", gsm.identity?.value)
    }

    @Test
    fun theSignalBucket_usesTheMapCardScale_andIsUnknownWithoutASignal() {
        assertEquals(SignalBuckets.STRONG, find(id = 1, radio = "LTE").apply { signalDbm = -80 }.signalBucket)
        assertEquals(SignalBuckets.VERY_WEAK, find(id = 1, radio = "GSM").apply { signalDbm = -110 }.signalBucket)
        assertEquals(SignalBuckets.UNKNOWN, find(id = 1, radio = "LTE").signalBucket)
    }

    private fun find(id: Long, firstSeen: Long = 1_000, session: Long = 100, radio: String = "LTE") =
        NewTowerFindEntity().apply {
            this.id = id
            mcc = "310"
            mnc = "260"
            area = 12345
            cellId = id
            this.radio = radio
            firstSeenAt = firstSeen
            lastSeenAt = firstSeen
            sessionStartedAt = session
            lastCheckedAt = firstSeen
        }
}

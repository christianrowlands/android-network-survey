package com.craxiom.networksurvey.scout

import android.content.Context
import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.scout.link.ScoutFixtureFiles
import com.craxiom.networksurvey.scout.model.PairedScout
import com.craxiom.networksurvey.scout.model.ScoutId
import com.craxiom.networksurvey.scout.model.WifiSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The paired Scout list (keyed by Scout ID) and the Wi-Fi source survive in preferences. */
@RunWith(RobolectricTestRunner::class)
class ScoutStoreTest {
    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("scout-store-test", Context.MODE_PRIVATE)
    private val store = ScoutStore(preferences)
    private val info = BoardInfo.parseFrom(ScoutFixtureFiles.bytes("proto/board_info.bin"))

    @Test
    fun empty_hasNoScoutAndThePhoneAsSource() {
        assertNull(store.pairedScout())
        assertEquals(WifiSource.PHONE, store.wifiSource())
    }

    @Test
    fun pairedScout_roundTripsWithItsIdFromInfo() {
        val scout = PairedScout.from("02:00:5E:00:AB:CD", info, nowMs = 42)
        store.replacePairedScout(scout)

        val read = ScoutStore(preferences).pairedScout()
        assertEquals(scout, read)
        assertEquals(ScoutId("NS-02005E00ABCD"), read?.scoutId)
        assertEquals("NS-ABCD", read?.name)
        assertEquals(listOf(1, 2), read?.bands)
    }

    @Test
    fun replace_keepsOneScoutAndRemoveForgetsIt() {
        store.replacePairedScout(PairedScout.from("02:00:5E:00:AB:CD", info, nowMs = 1))
        val other = PairedScout.from("02:00:5E:00:5C:FA", info.toBuilder().clearBtAddress().setShortId("5CFA").build(), nowMs = 2)
        store.replacePairedScout(other)

        assertEquals(listOf(other), store.pairedScouts())
        assertEquals("NS-02005E005CFA", other.id)
        store.remove(other.scoutId)
        assertTrue(store.pairedScouts().isEmpty())
    }

    @Test
    fun wifiSource_persists() {
        store.setWifiSource(WifiSource.SCOUT)
        assertEquals(WifiSource.SCOUT, ScoutStore(preferences).wifiSource())
    }

    @Test
    fun corruptJson_readsAsNoScout() {
        preferences.edit().putString(ScoutConstants.PREFERENCE_PAIRED_SCOUTS, "{not json").commit()
        assertNull(store.pairedScout())
        preferences.edit().putString(ScoutConstants.PREFERENCE_PAIRED_SCOUTS, "[{\"address\":\"x\"}]").commit()
        assertNull(store.pairedScout())
    }
}

package com.craxiom.networksurvey.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.craxiom.networksurvey.util.PlmnColorMapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A provider color override applies to its network at any MNC width: the tower sheet can see
 * "440-010" (a tower stored at the old padded width) while survey points carry the phone's
 * "440-10", and both must get the color the user picked.
 */
@RunWith(RobolectricTestRunner::class)
class PlmnColorOverrideManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clear() {
        PlmnColorOverrideManager(context).clearAll()
    }

    @Test
    fun anOverrideSetAtOneWidth_appliesAtTheOther() {
        val manager = PlmnColorOverrideManager(context)
        manager.setOverride("440", "010", 5)

        assertEquals(5, manager.getOverride("440", "10"))
        assertEquals(5, PlmnColorMapper.getColorIndex("440", "10"))
        assertEquals(5, PlmnColorMapper.getColorIndex("440", "010"))
    }

    @Test
    fun settingAgainAtTheOtherWidth_replacesTheEntry_withTheNewSpelling() {
        val manager = PlmnColorOverrideManager(context)
        manager.setOverride("440", "010", 5)
        manager.setOverride("440", "10", 7)

        assertEquals(mapOf("440-10" to 7), manager.getOverrides())
        assertTrue(manager.removeOverride("440", "010"))
        assertNull(PlmnColorOverrideManager(context).getOverride("440", "10"))
    }

    @Test
    fun storedDuplicatesFromOlderVersions_collapseToOneOnLoad() {
        context.getSharedPreferences("network_survey_plmn_color_overrides", Context.MODE_PRIVATE).edit()
            .putString("overrides_json", """{"440-010":3,"440-10":4,"310-260":1}""").commit()

        val overrides = PlmnColorOverrideManager(context).getOverrides()

        assertEquals(2, overrides.size)
        assertEquals(1, overrides["310-260"])
        assertEquals(2, PlmnColorOverrideManager(context).getOverrides().size)
    }

    @Test
    fun differentNetworksInOneMcc_keepTheirOwnColors() {
        val manager = PlmnColorOverrideManager(context)
        manager.setOverride("310", "010", 2)
        manager.setOverride("310", "100", 9)

        assertEquals(2, PlmnColorMapper.getColorIndex("310", "10"))
        assertEquals(9, PlmnColorMapper.getColorIndex("310", "100"))
    }
}

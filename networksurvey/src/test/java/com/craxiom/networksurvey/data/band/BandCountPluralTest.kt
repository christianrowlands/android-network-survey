package com.craxiom.networksurvey.data.band

import com.craxiom.networksurvey.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Checks both quantity forms of the band count.
 *
 * The singular is unreachable through the UI: the catalogue never drops below about fifty entries,
 * because the technology chips refuse to switch the last one off. That makes this the only place
 * the singular is exercised, which matters because a malformed plural (a missing quantity, or a
 * placeholder that does not match the argument) throws at runtime rather than at build time.
 */
@RunWith(RobolectricTestRunner::class)
class BandCountPluralTest {

    private val resources get() = RuntimeEnvironment.getApplication().resources

    @Test
    fun singularAndPluralBothResolve() {
        assertEquals("1 band", resources.getQuantityString(R.plurals.band_count, 1, 1))
        assertEquals("2 bands", resources.getQuantityString(R.plurals.band_count, 2, 2))
        assertEquals("149 bands", resources.getQuantityString(R.plurals.band_count, 149, 149))
        assertEquals("0 bands", resources.getQuantityString(R.plurals.band_count, 0, 0))
    }
}

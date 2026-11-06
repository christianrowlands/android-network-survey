package com.craxiom.networksurvey.ui.main.more

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The More list before its first read off the main thread. */
@RunWith(RobolectricTestRunner::class)
class MoreViewModelTest {

    @Test
    fun beforeTheFirstRead_theHardwareRowIsHidden_soABanningOrganizationNeverShowsIt() {
        val viewModel = MoreViewModel(RuntimeEnvironment.getApplication())
        val hardware = viewModel.state.value.groups.first { it.section == MoreSection.HARDWARE }
        assertEquals(emptyList<MoreItem>(), hardware.items)
    }
}

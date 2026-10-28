package com.craxiom.networksurvey.ui.survey

import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.survey.status.StatusDetail
import com.craxiom.networksurvey.survey.status.WaitReason
import com.craxiom.networksurvey.ui.common.CountFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The counts in the destination statuses (Survey rows, destination pages, the notification) and the
 * "Still to do" panel are grouped like the Records panel's ("8,696"), while the plural still agrees
 * with the raw count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DestinationStatusTextTest {

    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun waiting_groupsTheCount() {
        val detail = StatusDetail.Waiting(8696, WaitReason.NextUpload(MINUTE_MS, 15))
        assertEquals("8,696 waiting · next upload in about 1 min", detail(detail))
    }

    @Test
    fun uploading_andWaitingToSend_groupTheCount_andOneStaysSingular() {
        assertEquals("Uploading 1,006 records", detail(StatusDetail.Uploading(1006)))
        assertEquals("Uploading 1 record", detail(StatusDetail.Uploading(1)))
        assertEquals("broker · not connected yet · 12,500 waiting to send", detail(StatusDetail.StreamConnecting(12_500)))
    }

    @Test
    fun stillToDo_groupsTheCount() {
        assertEquals(
            "1,006 records for NS Analytics",
            CountFormat.plural(resources, R.plurals.survey_still_to_do_records, 1006, resources.getString(R.string.survey_dest_ns_analytics)),
        )
    }

    private fun detail(detail: StatusDetail): String? =
        DestinationStatusText.detail(resources, detail, address = "broker", formatTime = { "09:12" }, nowMs = 0L)

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}

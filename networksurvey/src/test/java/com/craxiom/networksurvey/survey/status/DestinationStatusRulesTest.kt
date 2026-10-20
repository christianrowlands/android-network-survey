package com.craxiom.networksurvey.survey.status

import com.craxiom.networksurvey.survey.engine.CommunityUploadHealth
import com.craxiom.networksurvey.survey.engine.DestinationRuntime
import com.craxiom.networksurvey.survey.engine.LinkFailure
import com.craxiom.networksurvey.survey.engine.LinkState
import com.craxiom.networksurvey.survey.engine.StreamLink
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * One case per row of the status table: what each destination's records are doing, and the word,
 * level and detail that say so.
 */
class DestinationStatusRulesTest {

    private fun status(
        destination: Destination,
        runtime: DestinationRuntime? = DestinationRuntime(setOf(RecordType.CELLULAR)),
        paused: Boolean = false,
        queues: DeliveryQueues = DeliveryQueues(),
        autoUpload: DestinationStatusRules.AutoUpload = DestinationStatusRules.AutoUpload(),
    ) = DestinationStatusRules.status(destination, runtime, paused, queues, autoUpload, NOW)

    private fun stream(link: StreamLink) = status(Destination.MQTT, DestinationRuntime(setOf(RecordType.CELLULAR), link = link))

    private fun nsAnalytics(queue: NsAnalyticsQueue, autoUpload: Boolean = true) = status(
        Destination.NS_ANALYTICS,
        queues = DeliveryQueues(nsAnalytics = queue),
        autoUpload = DestinationStatusRules.AutoUpload(nsAnalytics = autoUpload),
    )

    // Log files

    @Test
    fun `log files save, or say which types have no file`() {
        assertEquals(DestinationStatus(StatusWord.SAVING), status(Destination.LOG_FILES))

        val partly = status(Destination.LOG_FILES, DestinationRuntime(setOf(RecordType.CELLULAR), failedTypes = setOf(RecordType.CDR)))
        assertEquals(StatusWord.PARTLY_SAVING, partly.word)
        assertEquals(StatusLevel.NOT_ARRIVING, partly.level)
        assertEquals(StatusDetail.FilesFailed(setOf(RecordType.CDR)), partly.detail)
    }

    // NS Analytics

    @Test
    fun `NS Analytics with auto upload says when the next upload is`() {
        val queued = nsAnalytics(NsAnalyticsQueue(pending = 312, nextUploadMs = NOW + 6 * 60_000L))
        assertEquals(StatusWord.QUEUED, queued.word)
        assertEquals(StatusLevel.ON_ITS_WAY, queued.level)
        assertEquals(StatusDetail.Waiting(312, WaitReason.NextUpload(NOW + 6 * 60_000L, 15)), queued.detail)
    }

    @Test
    fun `NS Analytics with nothing waiting is up to date`() {
        val upToDate = nsAnalytics(NsAnalyticsQueue(pending = 0, lastUploadMs = NOW - 60_000L))
        assertEquals(StatusWord.UP_TO_DATE, upToDate.word)
        assertEquals(StatusDetail.NothingWaiting(NOW - 60_000L), upToDate.detail)
    }

    @Test
    fun `NS Analytics with auto upload off holds the records and offers Upload now`() {
        val off = nsAnalytics(NsAnalyticsQueue(pending = 312), autoUpload = false)
        assertEquals(StatusWord.NOT_UPLOADING, off.word)
        assertEquals(StatusLevel.HELD, off.level)
        assertEquals(StatusAction.UPLOAD_NOW, off.action)

        val nothingYet = nsAnalytics(NsAnalyticsQueue(pending = 0), autoUpload = false)
        assertEquals("Still held: nothing will upload on its own", StatusWord.NOT_UPLOADING, nothingYet.word)
        assertNull("Nothing to upload yet", nothingYet.action)
    }

    @Test
    fun `NS Analytics uploading, failed, offline and quota`() {
        assertEquals(StatusWord.UPLOADING, nsAnalytics(NsAnalyticsQueue(pending = 312, uploading = true)).word)
        assertEquals(StatusWord.WILL_RETRY, nsAnalytics(NsAnalyticsQueue(pending = 312, lastUploadFailed = true)).word)
        assertEquals(StatusWord.WAITING_FOR_INTERNET, nsAnalytics(NsAnalyticsQueue(pending = 312, online = false)).word)

        val quota = nsAnalytics(NsAnalyticsQueue(pending = 312, quotaPaused = true))
        assertEquals(StatusWord.QUOTA_PAUSED, quota.word)
        assertEquals(StatusLevel.HELD, quota.level)
        assertEquals(StatusDetail.QuotaReached(312), quota.detail)
    }

    // MQTT and gRPC

    @Test
    fun `a first connection is amber, then red once it passes the escalation time`() {
        val justStarted = stream(StreamLink(LinkState.CONNECTING, sinceMs = NOW - 19_999L, waiting = 40))
        assertEquals(StatusWord.CONNECTING, justStarted.word)
        assertEquals(StatusLevel.HELD, justStarted.level)
        assertEquals(StatusDetail.StreamConnecting(40), justStarted.detail)

        val tooLong = stream(StreamLink(LinkState.CONNECTING, sinceMs = NOW - 20_000L, waiting = 340))
        assertEquals(StatusWord.CANT_CONNECT, tooLong.word)
        assertEquals(StatusLevel.NOT_ARRIVING, tooLong.level)
        assertEquals(StatusDetail.StreamUnreachable(NOW - 20_000L, 340), tooLong.detail)
    }

    @Test
    fun `a dropped connection reconnects in amber however long it takes`() {
        val lost = stream(StreamLink(LinkState.CONNECTING, sinceMs = NOW - 600_000L, everConnected = true, waiting = 340))
        assertEquals(StatusWord.RECONNECTING, lost.word)
        assertEquals(StatusLevel.HELD, lost.level)
    }

    @Test
    fun `live, sign-in failed and dropping`() {
        assertEquals(StatusWord.LIVE, stream(StreamLink(LinkState.CONNECTED, everConnected = true)).word)

        val rejected = stream(StreamLink(LinkState.FAILED, failure = LinkFailure.SIGN_IN_REJECTED))
        assertEquals(StatusWord.SIGN_IN_FAILED, rejected.word)
        assertEquals(StatusLevel.NOT_ARRIVING, rejected.level)

        val dropping = stream(StreamLink(LinkState.CONNECTING, sinceMs = NOW, everConnected = true, dropping = true))
        assertEquals("Throwing records away beats reconnecting", StatusWord.DROPPING, dropping.word)
    }

    @Test
    fun `settings the broker can't use say so instead of sign-in failed`() {
        val invalid = stream(StreamLink(LinkState.FAILED, failure = LinkFailure.INVALID_SETTINGS))
        assertEquals(StatusWord.SETTINGS_INVALID, invalid.word)
        assertEquals(StatusDetail.SettingsInvalid, invalid.detail)
        assertEquals(StatusLevel.NOT_ARRIVING, invalid.level)
    }

    @Test
    fun `a pause shows on a connected stream but not over a connection problem`() {
        assertEquals(StatusWord.PAUSED, status(Destination.GRPC, DestinationRuntime(emptySet(), link = StreamLink(LinkState.CONNECTED)), paused = true).word)
        val reconnecting = status(
            Destination.GRPC,
            DestinationRuntime(emptySet(), link = StreamLink(LinkState.CONNECTING, sinceMs = NOW, everConnected = true)),
            paused = true,
        )
        assertEquals(StatusWord.RECONNECTING, reconnecting.word)
    }

    // Community

    @Test
    fun `community collects, says when uploads failed, and when the key is refused`() {
        val collecting = status(Destination.COMMUNITY, queues = DeliveryQueues(community = CommunityQueue(pending = 812)))
        assertEquals(StatusWord.COLLECTING, collecting.word)
        assertEquals(StatusDetail.Waiting(812, WaitReason.EveryRecords(500)), collecting.detail)

        val failing = status(
            Destination.COMMUNITY,
            DestinationRuntime(setOf(RecordType.CELLULAR), upload = CommunityUploadHealth(failures = 3, retryAtMs = NOW + 60_000L)),
        )
        assertEquals(StatusWord.WILL_RETRY, failing.word)
        assertEquals(StatusDetail.UploadsFailed(3, NOW + 60_000L), failing.detail)

        val rejected = status(Destination.COMMUNITY, DestinationRuntime(setOf(RecordType.CELLULAR), upload = CommunityUploadHealth(keyRejected = true)))
        assertEquals(StatusWord.KEY_REJECTED, rejected.word)
    }

    @Test
    fun `community with auto upload off holds the records`() {
        val off = status(
            Destination.COMMUNITY,
            queues = DeliveryQueues(community = CommunityQueue(pending = 812)),
            autoUpload = DestinationStatusRules.AutoUpload(community = false),
        )
        assertEquals(StatusWord.NOT_UPLOADING, off.word)
        assertEquals(StatusAction.UPLOAD_NOW, off.action)
    }

    @Test
    fun `a waiting community upload says which network it waits for`() {
        fun queued(networkWait: NetworkWait) = CommunityQueue(pending = 40, uploadQueued = true, networkWait = networkWait)

        assertEquals(StatusDetail.Waiting(40, WaitReason.OnWifi), DestinationStatusRules.communityIdle(queued(NetworkWait.WIFI), autoUpload = true).detail)
        assertEquals(StatusDetail.Waiting(40, WaitReason.Offline), DestinationStatusRules.communityIdle(queued(NetworkWait.ANY), autoUpload = true).detail)
        assertEquals(StatusDetail.Waiting(40, WaitReason.UploadStarting), DestinationStatusRules.communityIdle(queued(NetworkWait.NONE), autoUpload = true).detail)

        val recording = status(Destination.COMMUNITY, queues = DeliveryQueues(community = queued(NetworkWait.WIFI)))
        assertEquals(StatusWord.QUEUED, recording.word)
        assertEquals(StatusDetail.Waiting(40, WaitReason.OnWifi), recording.detail)
    }

    @Test
    fun `community records left after a survey wait for the next one, or for Upload now`() {
        val auto = DestinationStatusRules.communityIdle(CommunityQueue(pending = 12), autoUpload = true)
        assertEquals(StatusDetail.Waiting(12, WaitReason.NextCommunitySurvey), auto.detail)
        assertEquals(StatusAction.UPLOAD_NOW, auto.action)

        val manual = DestinationStatusRules.communityIdle(CommunityQueue(pending = 12), autoUpload = false)
        assertEquals(StatusDetail.Waiting(12, WaitReason.AutoUploadOff), manual.detail)
    }

    @Test
    fun `a failed community upload says so and opens the Community page, while Ready and while recording`() {
        val failed = CommunityQueue(pending = 12, lastFailure = CommunityUploadFailure(INVALID_KEY))
        val expected = DestinationStatus(StatusWord.UPLOAD_FAILED, StatusDetail.UploadFailed(INVALID_KEY), StatusAction.OPEN)

        assertEquals(expected, DestinationStatusRules.communityIdle(failed, autoUpload = true))
        assertEquals(expected, DestinationStatusRules.communityIdle(failed, autoUpload = false))
        assertEquals(expected, status(Destination.COMMUNITY, queues = DeliveryQueues(community = failed)))
        assertEquals(StatusLevel.HELD, expected.level)
    }

    @Test
    fun `a cancelled or acknowledged upload leaves no failure behind`() {
        // The repository reports no failure for a cancel, and none once the Community page was opened
        val noFailure = CommunityQueue(pending = 12, lastFailure = null)

        val idle = DestinationStatusRules.communityIdle(noFailure, autoUpload = true)
        assertEquals(StatusWord.NOT_UPLOADING, idle.word)
        assertEquals(StatusAction.UPLOAD_NOW, idle.action)
        assertEquals(StatusWord.COLLECTING, status(Destination.COMMUNITY, queues = DeliveryQueues(community = noFailure)).word)
    }

    @Test
    fun `the next upload replaces the failure`() {
        val failure = CommunityUploadFailure(INVALID_KEY)
        val uploading = CommunityQueue(pending = 12, uploading = true, lastFailure = failure)
        assertEquals(StatusWord.UPLOADING, DestinationStatusRules.communityIdle(uploading, autoUpload = true).word)

        val queued = CommunityQueue(pending = 12, uploadQueued = true, lastFailure = failure)
        assertEquals(StatusWord.QUEUED, status(Destination.COMMUNITY, queues = DeliveryQueues(community = queued)).word)
    }

    // Escalation timing

    @Test
    fun `the next escalation is the earliest first connection still under the limit`() {
        val runtimes = listOf(
            DestinationRuntime(emptySet(), link = StreamLink(LinkState.CONNECTING, sinceMs = NOW - 15_000L)),
            DestinationRuntime(emptySet(), link = StreamLink(LinkState.CONNECTING, sinceMs = NOW - 5_000L)),
            DestinationRuntime(emptySet(), link = StreamLink(LinkState.CONNECTING, sinceMs = NOW - 15_000L, everConnected = true)),
            DestinationRuntime(emptySet(), link = StreamLink(LinkState.CONNECTING, sinceMs = NOW - 25_000L)),
        )
        assertEquals(NOW + 5_000L, DestinationStatusRules.nextEscalationMs(runtimes, NOW))
        assertNull(DestinationStatusRules.nextEscalationMs(emptyList(), NOW))
    }

    @Test
    fun `the notification statuses leave NS Analytics out`() {
        val statuses = DestinationStatusRules.serviceStatuses(
            mapOf(
                Destination.NS_ANALYTICS to DestinationRuntime(setOf(RecordType.CELLULAR)),
                Destination.LOG_FILES to DestinationRuntime(setOf(RecordType.CELLULAR)),
            ),
            paused = false,
            communityAutoUpload = true,
            nowMs = NOW,
        )
        assertEquals(listOf(Destination.LOG_FILES), statuses.keys.toList())
    }

    private companion object {
        const val INVALID_KEY = "Invalid API key"
        const val NOW = 1_800_000_000_000L
    }
}

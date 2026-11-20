package com.craxiom.networksurvey.ui.activesurvey

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.logging.db.model.NewTowerFindEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager

/**
 * The new tower alert and the end-of-survey summary, both posted as notification 104: the alert
 * lists the session's newest finds under the session count and summarizes the rest as "+N more",
 * the summary is silent, a session with no finds leaves an earlier summary in place, and cancel
 * removes whichever is showing.
 */
@RunWith(RobolectricTestRunner::class)
class NewTowerNotificationHelperTest {

    private val app: Application = RuntimeEnvironment.getApplication()
    private lateinit var notifications: ShadowNotificationManager

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    }

    @Test
    fun theAlert_listsTheNewestFinds_underTheSessionCount() {
        val newest = finds(3)

        NewTowerNotificationHelper.showNewTowerNotification(app, newest, 3)

        val extras = posted().extras
        assertEquals(app.getString(R.string.new_tower_alert_title), extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(countText(3), extras.getCharSequence(Notification.EXTRA_TITLE_BIG).toString())
        assertTrue("The collapsed text is the tower just found", extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith(content(newest[0])))
        assertListed(newest)
        assertNull("Every find is listed", summaryText())
        assertNotEquals("The alert is not silent", NotificationCompat.GROUP_KEY_SILENT, posted().group)
    }

    @Test
    fun theAlert_listsAtMostFiveFinds_andSummarizesTheRest() {
        val newest = finds(NewTowerNotificationHelper.MAX_LINES + 2)

        NewTowerNotificationHelper.showNewTowerNotification(app, newest, newest.size)

        assertListed(newest.take(NewTowerNotificationHelper.MAX_LINES))
        assertEquals(moreText(2), summaryText())
    }

    @Test
    fun theMoreSummary_countsTheSessionsFindsNotListed() {
        val max = NewTowerNotificationHelper.MAX_LINES

        NewTowerNotificationHelper.showNewTowerNotification(app, finds(max), max)
        assertNull("Exactly five finds, all listed", summaryText())

        NewTowerNotificationHelper.showNewTowerNotification(app, finds(max), max + 1)
        assertEquals(moreText(1), summaryText())

        NewTowerNotificationHelper.showNewTowerNotification(app, finds(2), 4)
        assertEquals("Fewer finds passed than the session found", moreText(2), summaryText())
    }

    @Test
    fun theAlert_withNoFinds_postsNothing() {
        NewTowerNotificationHelper.showNewTowerNotification(app, emptyList(), 3)

        assertNull(notifications.getNotification(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID))
    }

    @Test
    fun theSummary_replacesTheAlert_silently() {
        NewTowerNotificationHelper.showNewTowerNotification(app, finds(1), 1)

        NewTowerNotificationHelper.showSessionSummary(app, 4)

        assertEquals("Replaced, not stacked", 1, notifications.size())
        val summary = posted()
        assertEquals(app.getString(R.string.new_tower_summary_title), summary.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(countText(4), summary.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        // NotificationCompat's setSilent: the silent group, alerting only for a summary it never posts
        assertEquals(NotificationCompat.GROUP_KEY_SILENT, summary.group)
        assertEquals(Notification.GROUP_ALERT_SUMMARY, summary.groupAlertBehavior)
    }

    @Test
    fun aSessionWithNoFinds_leavesAnEarlierSummaryInPlace() {
        NewTowerNotificationHelper.showSessionSummary(app, 2)

        NewTowerNotificationHelper.showSessionSummary(app, 0)

        assertEquals(countText(2), posted().extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun cancel_removesTheNotification() {
        NewTowerNotificationHelper.showSessionSummary(app, 2)

        NewTowerNotificationHelper.cancel(app)

        assertNull(notifications.getNotification(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID))
    }

    private fun posted(): Notification {
        val notification = notifications.getNotification(NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID)
        assertNotNull("Nothing posted as notification ${NetworkSurveyConstants.NEW_TOWER_NOTIFICATION_ID}", notification)
        return notification!!
    }

    /** The expanded alert lists exactly [expected], in order, each with the time it was found. */
    private fun assertListed(expected: List<NewTowerFindEntity>) {
        val lines = posted().extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() }
        assertEquals(expected.size, lines.size)
        expected.zip(lines).forEach { (find, line) ->
            assertTrue("Expected ${content(find)} in: $line", line.startsWith(content(find) + LINE_SEPARATOR))
        }
    }

    private fun summaryText(): String? = posted().extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()

    private fun countText(count: Int) = app.resources.getQuantityString(R.plurals.new_towers_found_count, count, count)

    private fun moreText(count: Int) = app.resources.getQuantityString(R.plurals.new_tower_alert_more, count, count)

    /** The tower part of an alert line, before its time. */
    private fun content(find: NewTowerFindEntity) =
        app.getString(R.string.new_tower_alert_content, find.radio, find.mcc, find.mnc, find.cellId)

    /** [count] finds, newest first, the way the detector passes them. */
    private fun finds(count: Int): List<NewTowerFindEntity> = (count downTo 1).map { index ->
        NewTowerFindEntity().apply {
            id = index.toLong()
            mcc = "310"
            mnc = "260"
            area = 12345
            cellId = 1_000L + index
            radio = "LTE"
            firstSeenAt = index * 60_000L
            lastSeenAt = firstSeenAt
            sessionStartedAt = 100
            lastCheckedAt = firstSeenAt
        }
    }

    private companion object {
        /** Between the tower and the time in new_tower_alert_line. */
        const val LINE_SEPARATOR = " · "
    }
}

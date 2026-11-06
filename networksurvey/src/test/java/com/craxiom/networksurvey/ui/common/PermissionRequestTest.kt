package com.craxiom.networksurvey.ui.common

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** A permission fix either asks Android, or goes to the app's settings once it is denied for good. */
@RunWith(RobolectricTestRunner::class)
class PermissionRequestTest {
    private val application = RuntimeEnvironment.getApplication()
    private var asked = 0
    private val request = PermissionRequest(launch = { asked++ }, grantedNow = { false }, onGranted = {})

    @Test
    fun whileItCanStillAsk_itAsksAndroid() {
        request.requestOrOpenSettings(application)
        assertEquals(1, asked)
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test
    fun deniedForGood_itOpensTheAppSettings_withoutAsking() {
        request.access = PermissionAccess.BLOCKED
        request.requestOrOpenSettings(application)
        assertEquals(0, asked)
        val intent = shadowOf(application).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals(application.packageName, intent.data?.schemeSpecificPart)
    }
}

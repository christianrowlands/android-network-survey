package com.craxiom.networksurvey.services

import android.content.Context
import com.craxiom.networksurvey.constants.NsAnalyticsConstants
import com.craxiom.networksurvey.util.NsAnalyticsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * While NS Analytics is in the running survey, asks the server every few minutes whether this
 * phone is still in its workspace. When it was removed from the web, the registration is cleaned up
 * through [NsAnalyticsUtils.cleanupAfterDeregistration], whose listeners take NS Analytics out of
 * the survey. Lives in the service, so it works however the survey was started and whether or not
 * a screen is open.
 */
class NsAnalyticsRegistrationWatcher(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Starts checking, if not checking already. */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(NsAnalyticsConstants.REGISTRATION_CHECK_INTERVAL_MS)
                check()
            }
        }
    }

    /** Stops checking. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun check() {
        when (val result = NsAnalyticsUtils.checkDeviceRegistrationStatus(context)) {
            is NsAnalyticsUtils.DeviceStatusResult.Deregistered -> {
                Timber.i("This phone was removed from its NS Analytics workspace")
                NsAnalyticsUtils.cleanupAfterDeregistration(context)
            }

            is NsAnalyticsUtils.DeviceStatusResult.Active -> Timber.d("NS Analytics registration check: still active")
            is NsAnalyticsUtils.DeviceStatusResult.CheckFailed -> Timber.d("NS Analytics registration check failed: %s", result.reason)
        }
    }
}

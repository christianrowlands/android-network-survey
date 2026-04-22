package com.craxiom.networksurvey.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Crash reporting facade, regular variant routes to Firebase Crashlytics.
 *
 * Callers should stay unaware of the flavor. The CDR variant ships a no-op implementation with
 * the same API, so feature code can unconditionally call into this object.
 */
object NsCrashReporter {

    fun recordException(throwable: Throwable, message: String? = null) {
        try {
            if (!message.isNullOrBlank()) FirebaseCrashlytics.getInstance().log(message)
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (ignored: Throwable) {
            Timber.w(throwable, message ?: "recordException failed")
        }
    }

    fun setCustomKey(key: String, value: String) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (ignored: Throwable) {
        }
    }
}

package com.craxiom.networksurvey.util

import timber.log.Timber

/**
 * Crash reporting facade. CDR variant is a no-op. CDR ships without Firebase/Crashlytics so
 * this file exists only to satisfy the `main` sourceset contract.
 */
object NsCrashReporter {

    fun recordException(throwable: Throwable, message: String? = null) {
        Timber.w(throwable, message ?: "")
    }

    fun setCustomKey(key: String, value: String) {
        // no-op
    }
}

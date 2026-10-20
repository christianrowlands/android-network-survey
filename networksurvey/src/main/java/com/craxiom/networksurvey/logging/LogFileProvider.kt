package com.craxiom.networksurvey.logging

import androidx.core.content.FileProvider

/**
 * Shares the CSV log files with other apps from the Log files page. Its own subclass, so its
 * manifest entry can never clash with a FileProvider a library declares.
 */
class LogFileProvider : FileProvider() {
    companion object {
        /** Appended to the application id to form the provider authority. */
        const val AUTHORITY_SUFFIX = ".logfiles"
    }
}

package com.craxiom.networksurvey.util

import android.content.Context

/** This build has no analytics, so there is no App Instance ID and Settings leaves its row out. */
object AppInstanceId {

    @Suppress("UNUSED_PARAMETER")
    fun fetch(context: Context, onResult: (String) -> Unit) = Unit
}

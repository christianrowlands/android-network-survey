package com.craxiom.networksurvey.util

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

/** The Firebase App Instance ID shown (and copied) on Settings, for matching a phone to its reports. */
object AppInstanceId {

    /** Delivers the ID once Firebase has it; nothing is delivered when there is none. */
    fun fetch(context: Context, onResult: (String) -> Unit) {
        FirebaseAnalytics.getInstance(context).appInstanceId.addOnSuccessListener { id -> if (id != null) onResult(id) }
    }
}

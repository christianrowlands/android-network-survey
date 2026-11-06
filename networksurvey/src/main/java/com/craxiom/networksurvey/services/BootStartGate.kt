package com.craxiom.networksurvey.services

import android.app.Service
import android.content.Context
import android.content.Intent
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tells a boot start from this app apart from another app sending the same extra.
 *
 * [NetworkSurveyService] is exported for the Intent API and can't see who sent an intent, so
 * [NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT] alone would let any app start the saved auto start
 * setup, past the user's Intent control setting. The boot code gets its intent from [newStartIntent],
 * which carries a one-time token that only exists in this process's memory.
 *
 * Android replays a start the service answered with START_REDELIVER_INTENT after the process dies,
 * when the token is gone, so a redelivered start is accepted too. The flag is set by the system, and
 * the service must answer a refused start with START_NOT_STICKY so it is never redelivered.
 */
object BootStartGate {
    private const val EXTRA_TOKEN = "com.craxiom.networksurvey.extra.BOOT_START_TOKEN"

    private val tokens: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** @return The intent that starts [NetworkSurveyService] for a boot, which [accept] lets through once. */
    @JvmStatic
    fun newStartIntent(context: Context): Intent {
        val token = UUID.randomUUID().toString()
        tokens += token
        return Intent(context, NetworkSurveyService::class.java)
            .putExtra(NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT, true)
            .putExtra(EXTRA_TOKEN, token)
    }

    /**
     * @param intent     A start that has [NetworkSurveyConstants.EXTRA_STARTED_AT_BOOT] set.
     * @param startFlags The flags from `onStartCommand`.
     * @return True when the boot code sent [intent], or Android is replaying a start accepted earlier.
     */
    @JvmStatic
    fun accept(intent: Intent, startFlags: Int): Boolean {
        if (startFlags and Service.START_FLAG_REDELIVERY != 0) return true
        return intent.getStringExtra(EXTRA_TOKEN)?.let(tokens::remove) ?: false
    }
}

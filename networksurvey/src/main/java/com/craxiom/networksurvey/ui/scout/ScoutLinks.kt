package com.craxiom.networksurvey.ui.scout

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.main.more.ExternalLinks
import timber.log.Timber

/** Where the NS Scout page sends people: the installer (shared to a computer), the Scout site and Bluetooth settings. */
object ScoutLinks {
    /** Shares the installer link, for opening on a computer. @return False when nothing can share. */
    fun shareInstaller(context: Context): Boolean {
        val text = context.getString(R.string.scout_install_share_text, ExternalLinks.NS_SCOUT_INSTALLER)
        val send = Intent(Intent.ACTION_SEND).setType(PLAIN_TEXT).putExtra(Intent.EXTRA_TEXT, text)
        return start(context, Intent.createChooser(send, context.getString(R.string.scout_install_share)))
    }

    /** Opens the NS Scout site (boards and where to buy them). */
    fun openScoutSite(context: Context): Boolean = ExternalLinks.open(context, ExternalLinks.NS_SCOUT)

    /** Opens Android's Bluetooth settings, where a removed Scout is forgotten. */
    fun openBluetoothSettings(context: Context): Boolean = start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "Nothing can handle %s", intent.action)
        false
    }

    private const val PLAIN_TEXT = "text/plain"
}

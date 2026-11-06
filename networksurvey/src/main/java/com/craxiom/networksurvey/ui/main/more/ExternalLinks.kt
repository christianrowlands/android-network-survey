package com.craxiom.networksurvey.ui.main.more

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import timber.log.Timber

/** The web pages the app links to, and the one way it opens them. */
object ExternalLinks {
    const val USER_MANUAL = "https://networksurvey.app/manual"
    const val MESSAGING_DOCS = "https://messaging.networksurvey.app/"
    const val REPORT_ISSUE = "https://github.com/christianrowlands/android-network-survey/issues/new/choose"
    const val GITHUB = "https://github.com/christianrowlands/android-network-survey"
    const val PRIVACY_POLICY = "https://networksurvey.app/privacy-policy"

    /** The NS Scout page: what it is, which boards, where to buy them. */
    const val NS_SCOUT = "https://networksurvey.app/scout"

    /** The NS Scout web installer's address, as the app shows it. */
    const val NS_SCOUT_INSTALLER_HOST = "install.networksurvey.app"

    /** The NS Scout web installer, opened from a computer. */
    const val NS_SCOUT_INSTALLER = "https://$NS_SCOUT_INSTALLER_HOST/"

    /** Opens [url] in the browser. @return False when no app on the phone can open it. */
    fun open(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        true
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No app can open %s", url)
        false
    }
}

package com.craxiom.networksurvey.ui.main

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.watchlist.WatchlistImportHolder
import com.craxiom.networksurvey.util.NsAnalyticsDeepLinkHandler
import com.craxiom.networksurvey.util.NsAnalyticsSecureStorage
import com.craxiom.networksurvey.util.WatchlistDeepLinkHandler
import timber.log.Timber

/**
 * Acts on what [DeepLinkRouter] decides for an incoming intent: reads and stores a registration
 * or watchlist link, says when one cannot be used, and asks [deepLinkViewModel] for the page or
 * tab.
 */
class DeepLinkDispatcher(private val context: Context, private val deepLinkViewModel: DeepLinkViewModel) {

    /** Routes [intent] and acts on the target, if any. */
    fun dispatch(intent: Intent?) {
        intent ?: return
        val trueExtras = DeepLinkRouter.NAVIGATION_EXTRAS.filterTo(mutableSetOf()) { intent.getBooleanExtra(it, false) }
        val target = DeepLinkRouter.route(intent.action, intent.dataString, trueExtras) ?: return
        Timber.i("Handling %s", target::class.simpleName)
        when (target) {
            DeepLinkTarget.GrpcPage -> deepLinkViewModel.navigateToPage(NavDrawerOption.ServerConnection.name)
            is DeepLinkTarget.NsAnalyticsRegister -> register(intent)
            is DeepLinkTarget.WatchlistImport -> importWatchlist(intent)
            DeepLinkTarget.NsAnalyticsPage -> deepLinkViewModel.navigateToPage(NavDrawerOption.NsAnalyticsConnection.name)
            DeepLinkTarget.WatchlistHistory -> deepLinkViewModel.navigateToPage(NavOption.WatchlistHistory.name)
            DeepLinkTarget.CommunityPage -> deepLinkViewModel.navigateToPage(NavOption.CommunityDatabases.name)
            is DeepLinkTarget.HomeTab -> deepLinkViewModel.navigateToHomeTab(target.route)
        }
    }

    private fun register(intent: Intent) {
        when (val result = NsAnalyticsDeepLinkHandler.parseIntent(intent)) {
            is NsAnalyticsDeepLinkHandler.DeepLinkResult.Success -> {
                NsAnalyticsSecureStorage.storeQrData(context.applicationContext, result.qrData)
                deepLinkViewModel.navigateToPage(NavDrawerOption.NsAnalyticsConnection.name)
            }

            is NsAnalyticsDeepLinkHandler.DeepLinkResult.Error -> {
                val reason = context.getString(result.messageRes)
                Timber.w("Invalid NS Analytics deep link: %s", context.resources.getResourceEntryName(result.messageRes))
                Toast.makeText(context, context.getString(R.string.deep_link_invalid_registration, reason), Toast.LENGTH_LONG).show()
            }

            NsAnalyticsDeepLinkHandler.DeepLinkResult.NotApplicable -> Unit
        }
    }

    private fun importWatchlist(intent: Intent) {
        when (val result = WatchlistDeepLinkHandler.parseIntent(intent)) {
            is WatchlistDeepLinkHandler.Result.Success -> {
                WatchlistImportHolder.set(result.importSet)
                deepLinkViewModel.navigateToPage(NavDrawerOption.Watchlist.name)
            }

            is WatchlistDeepLinkHandler.Result.Error -> {
                Timber.w("Invalid watchlist import link: %s", context.resources.getResourceEntryName(result.messageRes))
                Toast.makeText(context, context.getString(result.messageRes), Toast.LENGTH_LONG).show()
            }

            WatchlistDeepLinkHandler.Result.NotApplicable -> Unit
        }
    }
}

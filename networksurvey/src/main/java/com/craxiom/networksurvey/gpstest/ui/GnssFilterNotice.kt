package com.craxiom.networksurvey.gpstest.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.Application
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.InlineNotice
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.util.PreferenceUtils

/**
 * Says how many signals the constellation filter lets through ("Showing 5 of 9 signals"), with Show
 * all; the Status and Sky pages show it while the filter hides some.
 */
@Composable
fun GnssFilterNotice(shown: Int, total: Int, onShowAll: () -> Unit, modifier: Modifier = Modifier) {
    InlineNotice(
        text = pluralStringResource(R.plurals.filter_signal_text, total, shown, total),
        icon = R.drawable.ic_filter,
        modifier = modifier,
    ) {
        NsTextAction(text = stringResource(R.string.gnss_filter_show_all), onClick = onShowAll)
    }
}

/** Shows every constellation again: what Show all does on both pages and on the filter-hides-all message. */
fun clearGnssFilter() = PreferenceUtils.clearGnssFilter(Application.get(), Application.getPrefs())

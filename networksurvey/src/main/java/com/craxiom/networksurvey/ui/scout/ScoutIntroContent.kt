package com.craxiom.networksurvey.ui.scout

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsActionRow
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.SMALL_ICON
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.main.more.ExternalLinks
import com.craxiom.networksurvey.ui.scout.components.ScoutBadge
import com.craxiom.networksurvey.ui.survey.components.boldArguments
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * The NS Scout page before a Scout is paired: what the Scout is and adds over the phone, what to
 * buy, and the one-time install from a computer (with a link to share to that computer). Add a
 * Scout sits in the page's bottom bar. Every claim here is checked against the firmware's README.
 */
@Composable
fun ColumnScope.ScoutIntroContent(onShareInstallLink: () -> Unit, onOpenScoutSite: () -> Unit) {
    NsPanel {
        Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m), verticalAlignment = Alignment.CenterVertically) {
                ScoutBadge(R.drawable.ic_sensors, size = HERO_BADGE)
                Column {
                    Text(
                        stringResource(R.string.scout_page_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.scout_intro_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Benefit(R.drawable.ic_speed, R.string.scout_intro_no_limit)
            Benefit(R.drawable.ic_wifi, R.string.scout_intro_bands)
            Benefit(R.drawable.ic_shield, R.string.scout_intro_details)
            Benefit(R.drawable.ic_devices, R.string.scout_intro_clients)
            Benefit(R.drawable.ic_hearing, R.string.scout_intro_listens)
        }
    }

    SectionHeader(stringResource(R.string.scout_need_heading))
    NsPanel {
        Text(
            text = boldArguments(stringResource(R.string.scout_need_body), listOf(RECOMMENDED_BOARD)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionHeader(stringResource(R.string.scout_install_heading))
    NsPanel {
        Text(
            text = boldArguments(stringResource(R.string.scout_install_body), listOf(ExternalLinks.NS_SCOUT_INSTALLER_HOST)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Text buttons pad their label; pull the row back so the first label lines up with the text
        val inset = ButtonDefaults.TextButtonContentPadding.calculateStartPadding(LocalLayoutDirection.current)
        NsActionRow(Modifier.offset(x = -inset)) {
            IconTextAction(R.drawable.ic_share, R.string.scout_install_share, onShareInstallLink)
            IconTextAction(R.drawable.ic_open_in_new, R.string.scout_where_to_buy, onOpenScoutSite)
        }
    }
}

@Composable
private fun Benefit(@DrawableRes icon: Int, @StringRes text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(SMALL_ICON))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A 48dp text action with a leading glyph ("Share link", "Where to buy"). */
@Composable
internal fun IconTextAction(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = NsTheme.spacing.touchTarget)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(SMALL_ICON))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = NsTheme.spacing.s),
        )
    }
}

/** The board the page recommends, a product name that is not translated. */
private const val RECOMMENDED_BOARD = "ESP32-C5-DevKitC-1"
private val HERO_BADGE = 48.dp

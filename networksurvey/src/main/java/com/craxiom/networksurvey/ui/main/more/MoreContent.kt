package com.craxiom.networksurvey.ui.main.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.SMALL_ICON
import com.craxiom.networksurvey.ui.common.SectionHeader
import com.craxiom.networksurvey.ui.main.appbar.TabTopBar
import com.craxiom.networksurvey.ui.survey.components.destinationDetail
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.theme.NsTheme

/** The widest the More list grows on a tablet or in landscape. */

/** What the More rows do. */
interface MoreActions {
    /** Opens the page at [route]. */
    fun onOpen(route: String)

    /** Opens [url] in the browser. */
    fun onOpenExternal(url: String)

    /** Copies the app name and version. */
    fun onCopyVersion()
}

/**
 * The More tab, stateless: Settings, the destinations, the tools, and help and about, replacing the
 * old drawer. Rows that open a web page say so with an external-link icon and their click label.
 */
@Composable
fun MoreContent(state: MoreUiState, actions: MoreActions, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        TabTopBar(title = stringResource(R.string.nav_more))
        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .nsContentWidth()
                        .padding(horizontal = NsTheme.spacing.screenGutter)
                        .padding(top = NsTheme.spacing.s, bottom = NsTheme.spacing.l),
                    verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s),
                ) {
                    state.groups.filter { it.items.isNotEmpty() }.forEach { group ->
                        group.section.title?.let { SectionHeader(stringResource(it)) }
                        NsRowGroup {
                            group.items.forEachIndexed { itemIndex, item ->
                                if (itemIndex > 0) NsRowDivider()
                                MoreRow(item, state, actions)
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem, state: MoreUiState, actions: MoreActions) {
    val (subtitle, subtitleColor) = when (val line = item.subtitle) {
        is MoreSubtitle.Text -> stringResource(line.text) to MaterialTheme.colorScheme.onSurfaceVariant
        is MoreSubtitle.Literal -> line.text to MaterialTheme.colorScheme.onSurfaceVariant
        is MoreSubtitle.OfDestination -> destinationDetail(line.destination, line.detail, state.info)
    }
    val action = item.action
    NsRow(
        title = stringResource(item.title),
        subtitle = subtitle,
        subtitleColor = subtitleColor,
        icon = item.icon,
        onClickLabel = when (action) {
            is MoreAction.External -> stringResource(R.string.more_open_in_browser)
            MoreAction.CopyVersion -> stringResource(R.string.more_copy_version)
            is MoreAction.Open -> null
        },
        onClick = {
            when (action) {
                is MoreAction.Open -> actions.onOpen(action.route)
                is MoreAction.External -> actions.onOpenExternal(action.url)
                MoreAction.CopyVersion -> actions.onCopyVersion()
            }
        },
    ) {
        when (action) {
            is MoreAction.Open -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is MoreAction.External -> TrailingGlyph(R.drawable.ic_open_in_new)
            MoreAction.CopyVersion -> TrailingGlyph(R.drawable.ic_content_copy)
        }
    }
}

@Composable
private fun TrailingGlyph(icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(SMALL_ICON),
    )
}

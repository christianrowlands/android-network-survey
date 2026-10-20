package com.craxiom.networksurvey.ui.signals

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.ui.common.InlineNotice
import com.craxiom.networksurvey.ui.common.NsTextAction
import com.craxiom.networksurvey.ui.theme.NsTheme

private val MESSAGE_MAX_WIDTH = 360.dp
private val MESSAGE_ICON_SIZE = 40.dp

/**
 * The centered message a Signals view shows in place of its content when something is in the way
 * (a permission, a radio, nothing found yet), with at most one button that fixes it.
 *
 * @param prominent A tonal button for the fix; false shows a text button for a secondary action.
 */
@Composable
fun SignalsStateMessage(
    @DrawableRes icon: Int,
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
    prominent: Boolean = true,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = MESSAGE_MAX_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = NsTheme.spacing.xxl, vertical = NsTheme.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(MESSAGE_ICON_SIZE),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                if (prominent) {
                    FilledTonalButton(onClick = onAction) { Text(text = action) }
                } else {
                    NsTextAction(text = action, onClick = onAction)
                }
            }
        }
    }
}

/**
 * A one-line notice at the top of a Signals details page when its radio cannot scan (a permission,
 * location or the radio off): the page keeps what it already shows, the chart takes no new points,
 * and the one button fixes what is in the way.
 */
@Composable
fun SignalsBlockedNotice(
    @DrawableRes icon: Int,
    text: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InlineNotice(
        text = text,
        icon = icon,
        emphasized = true,
        modifier = modifier,
    ) {
        NsTextAction(text = action, onClick = onAction)
    }
}

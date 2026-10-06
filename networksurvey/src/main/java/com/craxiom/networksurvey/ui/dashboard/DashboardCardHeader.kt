package com.craxiom.networksurvey.ui.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R

/**
 * Default styling values for dashboard cards, ensuring visual consistency
 * with the original XML-based cards.
 */
object DashboardCardDefaults {
    val shape = RoundedCornerShape(16.dp)
    val defaultElevation = 4.dp
    val margin = 8.dp

    /** Header band behind a card's icon and title; the XML colorCardDarkTitle points at the same role. */
    val HeaderBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primaryContainer

    /** Primary text color for dashboard cards; the XML @color/normalText points at the same role. */
    val TextPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurface

    /** Secondary text color for dashboard cards; the XML @color/fadedText points at the same role. */
    val TextSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    /**
     * Returns the standard card elevation for dashboard cards. Must be called
     * from a @Composable context since CardDefaults.cardElevation is composable.
     */
    @Composable
    fun elevation() = CardDefaults.cardElevation(defaultElevation = defaultElevation)
}

/**
 * Reusable card header composable that displays the semi-transparent blue band
 * with an icon, title, and optional help button found on dashboard cards.
 */
@Composable
fun DashboardCardHeader(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    onHelpClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DashboardCardDefaults.HeaderBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )

        if (onHelpClick != null) {
            IconButton(
                onClick = onHelpClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_help),
                    contentDescription = stringResource(R.string.help_button_description),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

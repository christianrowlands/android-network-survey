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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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

    /** Original cyan-blue header band color (#03A9F4 at 40% alpha), matching the XML colorCardDarkTitle. */
    val CardHeaderBlue = Color(0x6603A9F4)

    /** Primary text color matching the original XML @color/normalText for dashboard cards. */
    val TextPrimary = Color(0xFFEFEFEF)

    /** Secondary/faded text color matching the original XML @color/fadedText for dashboard cards. */
    val TextSecondary = Color(0xFFC2C1C1)

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
            .background(DashboardCardDefaults.CardHeaderBlue)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f),
        )

        if (onHelpClick != null) {
            IconButton(
                onClick = onHelpClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_help),
                    contentDescription = "Help",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

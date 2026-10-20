package com.craxiom.networksurvey.ui.main.appbar

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.nsContentWidth

/**
 * The top bar of a top-level tab (Survey, Signals, Map, More): the tab's title and its actions,
 * with no navigation icon. Its insets cover a landscape display cutout as well as the status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(title: String, actions: List<AppBarAction> = emptyList()) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        },
        actions = { actions.forEach { AppBarAction(it) } },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        // The title and actions line up with the tab's capped content column in landscape
        modifier = Modifier.nsContentWidth(),
    )
}

@Composable
fun AppBarAction(appBarAction: AppBarAction) {
    IconButton(onClick = appBarAction.onClick) {
        BadgedBox(
            badge = {
                if (appBarAction.active) {
                    // A surface-colored ring keeps the dot visible where it overlaps the
                    // icon, which is tinted with the same primary color when active.
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(10.dp)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }
        ) {
            Icon(
                painter = painterResource(id = appBarAction.icon),
                modifier = Modifier.size(24.dp),
                tint = if (appBarAction.active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                contentDescription = stringResource(id = appBarAction.description)
            )
        }
    }
}

/** The top bar of a page above the tabs: Back, the title and the page's actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleBar(
    title: String,
    appBarActions: List<AppBarAction>? = null,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = { BackIcon(onBackClick) },
        actions = {
            appBarActions?.let {
                for (appBarAction in it) {
                    AppBarAction(appBarAction)
                }
            }
        }
    )
}

@Composable
private fun BackIcon(onBackClick: () -> Unit) {
    IconButton(onClick = {
        onBackClick.invoke()
    }) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            tint = MaterialTheme.colorScheme.onBackground,
            contentDescription = stringResource(R.string.back_button_description)
        )
    }
}

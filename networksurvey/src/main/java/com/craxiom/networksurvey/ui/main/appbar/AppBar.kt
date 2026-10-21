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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.nsContentWidth
import com.craxiom.networksurvey.ui.common.rememberCompactHeight

/** The top bar height in a short window, where a full 64dp bar would crowd out the content. */
private val COMPACT_TOP_BAR_HEIGHT = 48.dp

/**
 * @return The height for a top app bar: [COMPACT_TOP_BAR_HEIGHT] in a short window (a phone in
 * landscape, or a large display size), otherwise the Material default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun nsTopBarHeight(): Dp = if (rememberCompactHeight()) COMPACT_TOP_BAR_HEIGHT else TopAppBarDefaults.TopAppBarExpandedHeight

/**
 * The top bar of a top-level tab (Survey, More): the tab's title and its actions, with no
 * navigation icon. Its insets cover a landscape display cutout as well as the status bar.
 *
 * @param scrollBehavior Slides the bar away as the tab's content scrolls down and back as it scrolls
 *                       up; the content must pass its nested scroll connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(title: String, actions: List<AppBarAction> = emptyList(), scrollBehavior: TopAppBarScrollBehavior? = null) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        },
        actions = { actions.forEach { AppBarAction(it) } },
        expandedHeight = nsTopBarHeight(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        // The same color scrolled or not, so the bar doesn't flash as it slides back in
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
        ),
        scrollBehavior = scrollBehavior,
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
        expandedHeight = nsTopBarHeight(),
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

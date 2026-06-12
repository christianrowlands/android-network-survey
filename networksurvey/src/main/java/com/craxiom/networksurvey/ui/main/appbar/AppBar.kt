package com.craxiom.networksurvey.ui.main.appbar

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    drawerState: DrawerState? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    @StringRes title: Int? = null,
    appBarActions: List<AppBarAction>? = null
) {
    TopAppBar(
        title = {
            title?.let {
                Text(
                    text = stringResource(id = title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        actions = {
            appBarActions?.let {
                for (appBarAction in it) {
                    AppBarAction(appBarAction)
                }
            }
        },
        navigationIcon = {
            if (drawerState != null && navigationIcon == null) {
                DrawerIcon(drawerState = drawerState)
            } else {
                navigationIcon?.invoke()
            }
        },
    )
}

@Composable
private fun DrawerIcon(drawerState: DrawerState) {
    val coroutineScope = rememberCoroutineScope()
    IconButton(onClick = {
        coroutineScope.launch {
            drawerState.open()
        }
    }) {
        Icon(
            Icons.Rounded.Menu,
            tint = MaterialTheme.colorScheme.onBackground,
            contentDescription = stringResource(id = R.string.drawer_menu_description)
        )
    }
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
            contentDescription = "Back button"
        )
    }
}

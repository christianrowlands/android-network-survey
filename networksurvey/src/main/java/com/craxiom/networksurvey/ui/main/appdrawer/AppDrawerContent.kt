package com.craxiom.networksurvey.ui.main.appdrawer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.main.DrawerParams
import com.craxiom.networksurvey.ui.main.NavDrawerOption
import com.craxiom.networksurvey.ui.theme.NsTheme
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun <T : Enum<T>> AppDrawerContent(
    appVersion: String,
    drawerState: DrawerState,
    menuItems: List<AppDrawerItemInfo<T>>,
    externalLinks: List<AppDrawerItemInfo<T>>,
    defaultPick: T,
    onClick: (T) -> Unit
) {
    var currentPick by remember { mutableStateOf(defaultPick) }
    val coroutineScope = rememberCoroutineScope()

    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.padding(8.dp)
            ) {
                AppIcon()

                // App Title
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

                // Menu Items
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    items(menuItems) { item ->
                        AppDrawerItem(item = item) { navOption ->
                            currentPick = navOption
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            onClick(navOption)
                        }
                    }

                    item {
                        // Divider between menu and external links
                        HorizontalDivider(thickness = 1.dp, modifier = Modifier.width(220.dp))
                    }

                    items(externalLinks) { item ->
                        AppDrawerItem(item = item) { navOption ->
                            currentPick = navOption
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            onClick(navOption)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // App Version at Bottom
                Text(
                    text = appVersion,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun AppIcon() {
    val context = LocalContext.current
    val iconBitmap: ImageBitmap? = remember(context) {
        try {
            val drawable = ResourcesCompat.getDrawable(
                context.resources,
                R.mipmap.ic_launcher,
                context.theme
            ) ?: return@remember null

            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (t: Throwable) {
            Timber.w(t, "Failed to load launcher icon for drawer")
            null
        }
    }

    iconBitmap?.let { bitmap ->
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "Network Survey App Icon",
                modifier = Modifier.requiredSize(54.dp)
            )
        }
    }
}

@Preview
@Composable
fun AppDrawerContentPreview() {
    val context = LocalContext.current
    NsTheme {
        AppDrawerContent(
            appVersion = "1.0",
            drawerState = DrawerState(DrawerValue.Closed),
            menuItems = DrawerParams.drawerButtons(context),
            externalLinks = DrawerParams.externalDrawerLinks,
            defaultPick = NavDrawerOption.CellularCalculators,
            onClick = {}
        )
    }
}
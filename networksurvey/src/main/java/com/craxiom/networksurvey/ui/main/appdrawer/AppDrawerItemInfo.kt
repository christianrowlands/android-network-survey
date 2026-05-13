package com.craxiom.networksurvey.ui.main.appdrawer

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class AppDrawerItemInfo<T>(
    val drawerOption: T,
    @StringRes val title: Int,
    @DrawableRes val drawableId: Int,
    @StringRes val descriptionId: Int
)

/**
 * A row in the navigation drawer: either a section header label or a tappable item.
 * Used to render the drawer as a mix of grouped sections and standalone items.
 */
sealed class DrawerEntry<T> {
    data class Header<T>(@StringRes val titleResId: Int) : DrawerEntry<T>()
    data class Item<T>(val info: AppDrawerItemInfo<T>) : DrawerEntry<T>()
}

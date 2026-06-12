package com.craxiom.networksurvey.ui.main.appbar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * One action icon on the top app bar. When [active] is true the icon is tinted with the theme
 * primary color and a small badge dot is drawn on it (e.g. to indicate an active Wi-Fi display
 * filter).
 */
data class AppBarAction(
    @DrawableRes val icon: Int,
    @StringRes val description: Int,
    val onClick: () -> Unit,
    val active: Boolean = false
)

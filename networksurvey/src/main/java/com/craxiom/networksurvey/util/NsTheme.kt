package com.craxiom.networksurvey.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.craxiom.networksurvey.R

@Composable
fun NsTheme(content: @Composable () -> Unit) {
    val darkColorScheme =
        darkColorScheme(
            surface = Color(color = DARK_SURFACE),
            background = Color.Black,
            primary = colorResource(id = R.color.colorAccent),
            tertiary = colorResource(id = R.color.colorAccent),
        )
    val lightColorScheme =
        lightColorScheme(
            surface = Color.White,
            background = Color(color = LIGHT_BACKGROUND),
        )
    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography(),
        content = content,
    )
}

private const val LIGHT_BACKGROUND = 0xFFF5F5F7
private const val DARK_SURFACE = 0xFF1C1E21

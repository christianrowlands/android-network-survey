package com.craxiom.networksurvey.ui.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.craxiom.networksurvey.ui.theme.NsTheme

@Composable
@Suppress("ModifierMissing")
fun NsPreview(
    darkTheme: Boolean = isSystemInDarkTheme(),
    showBackground: Boolean = true,
    content: @Composable () -> Unit
) {
    NsTheme(darkTheme = darkTheme) {
        if (showBackground) {
            // If we have a proper contentColor applied we need a Surface instead of a Box
            Surface(content = content)
        } else {
            content()
        }
    }
}

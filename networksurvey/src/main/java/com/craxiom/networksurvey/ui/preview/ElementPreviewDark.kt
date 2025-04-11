package com.craxiom.networksurvey.ui.preview

import androidx.compose.runtime.Composable

@Composable
fun NsPreviewDark(
    showBackground: Boolean = true,
    content: @Composable () -> Unit
) {
    NsPreview(
        darkTheme = true,
        showBackground = showBackground,
        content = content
    )
}

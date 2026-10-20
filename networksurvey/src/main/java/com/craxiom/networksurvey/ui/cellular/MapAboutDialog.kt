package com.craxiom.networksurvey.ui.cellular

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.NsMessageDialog

/** The map's About dialog: where the towers come from and what the survey point dots are. */
@Composable
fun MapAboutDialog(onDismiss: () -> Unit) {
    NsMessageDialog(
        title = stringResource(R.string.map_about_title),
        message = stringResource(R.string.map_about_towers) + "\n\n" + stringResource(R.string.survey_points_info),
        onDismiss = onDismiss,
        icon = Icons.Outlined.Info,
    )
}

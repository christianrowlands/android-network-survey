package com.craxiom.networksurvey.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.HelpItem
import com.craxiom.networksurvey.ui.common.HelpSection
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * DialogFragment bridge that hosts the [PhoneStateHelpDialog] composable. Use this to show
 * the Phone State help dialog from Java Fragment code.
 */
class PhoneStateHelpDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NsTheme {
                    PhoneStateHelpDialog(onDismissRequest = { dismissAllowingStateLoss() })
                }
            }
        }
    }

    companion object {
        const val TAG = "PhoneStateHelpDialog"
    }
}

/**
 * Material 3 help dialog explaining the Phone State survey feature, the events it captures,
 * and how it operates.
 */
@Composable
fun PhoneStateHelpDialog(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.phone_state_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.phone_state_help_overview),
                )

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.phone_state_help_events_title)) {
                    HelpItem(
                        stringResource(R.string.phone_state_help_event_registration_label),
                        stringResource(R.string.phone_state_help_event_registration_desc)
                    )
                    HelpItem(
                        stringResource(R.string.phone_state_help_event_sim_label),
                        stringResource(R.string.phone_state_help_event_sim_desc)
                    )
                    HelpItem(
                        stringResource(R.string.phone_state_help_event_handover_label),
                        stringResource(R.string.phone_state_help_event_handover_desc)
                    )
                    HelpItem(
                        stringResource(R.string.phone_state_help_event_access_tech_label),
                        stringResource(R.string.phone_state_help_event_access_tech_desc)
                    )
                    HelpItem(
                        stringResource(R.string.phone_state_help_event_roaming_label),
                        stringResource(R.string.phone_state_help_event_roaming_desc)
                    )
                    HelpItem(
                        stringResource(R.string.phone_state_help_event_ntn_label),
                        stringResource(R.string.phone_state_help_event_ntn_desc)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HelpSection(title = stringResource(R.string.phone_state_help_how_title)) {
                    HelpItem(
                        stringResource(R.string.phone_state_help_how_event_driven_label),
                        stringResource(R.string.phone_state_help_how_event_driven_desc)
                    )
                    HelpItem(
                        stringResource(R.string.phone_state_help_how_auto_include_label),
                        stringResource(R.string.phone_state_help_how_auto_include_desc)
                    )
                    HelpItem(
                        stringResource(R.string.phone_state_help_how_dual_sim_label),
                        stringResource(R.string.phone_state_help_how_dual_sim_desc)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.phone_state_help_dismiss))
            }
        }
    )
}

@PreviewDayNight
@Composable
private fun PhoneStateHelpDialogPreview() {
    NsPreview {
        PhoneStateHelpDialog(onDismissRequest = {})
    }
}

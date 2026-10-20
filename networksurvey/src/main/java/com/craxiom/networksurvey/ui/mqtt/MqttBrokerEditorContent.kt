package com.craxiom.networksurvey.ui.mqtt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.NsCtaBar
import com.craxiom.networksurvey.ui.common.NsFormField
import com.craxiom.networksurvey.ui.common.NsIconAction
import com.craxiom.networksurvey.ui.common.NsPageScaffold
import com.craxiom.networksurvey.ui.common.NsPanel
import com.craxiom.networksurvey.ui.common.NsPrimaryCta
import com.craxiom.networksurvey.ui.common.NsRadioRow
import com.craxiom.networksurvey.ui.common.NsRow
import com.craxiom.networksurvey.ui.common.NsRowDivider
import com.craxiom.networksurvey.ui.common.NsRowGroup
import com.craxiom.networksurvey.ui.common.NsSummaryText
import com.craxiom.networksurvey.ui.common.NsSwitchRow
import com.craxiom.networksurvey.ui.common.dialogs.NsConfirmationDialog
import com.craxiom.networksurvey.ui.theme.NsTheme

/** Everything the broker editor can ask for. */
interface MqttBrokerEditorActions {
    fun onBack() {}
    fun onEdit(change: (MqttBrokerForm) -> MqttBrokerForm) {}
    fun onSave() {}
    fun onShare() {}
    fun onRemove() {}
}

/**
 * The broker editor, stateless: name, host, port, TLS and login, with the topic prefix and QoS
 * under "More options", then Share and Remove for a saved broker. Save is pinned at the bottom.
 */
@Composable
fun MqttBrokerEditorContent(
    form: MqttBrokerForm,
    actions: MqttBrokerEditorActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val reconnects = !form.isNew && form.isActive && form.streaming

    NsPageScaffold(
        title = stringResource(if (form.isNew) R.string.mqtt_editor_add_title else R.string.mqtt_editor_edit_title),
        onBack = actions::onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        bottomBar = {
            NsCtaBar {
                if (reconnects) {
                    Text(
                        text = stringResource(R.string.mqtt_streaming_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NsPrimaryCta(
                    text = stringResource(if (form.isNew) R.string.mqtt_save_and_select else R.string.save),
                    onClick = actions::onSave,
                    icon = R.drawable.ic_check,
                )
            }
        },
    ) {
        NsPanel {
            Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
                NsFormField(R.string.mqtt_field_name, form.name, placeholder = form.host) { v -> actions.onEdit { it.copy(name = v) } }
                NsFormField(
                    R.string.mqtt_field_host, form.host,
                    error = if (form.hostError) stringResource(R.string.mqtt_error_host) else null,
                    keyboardType = KeyboardType.Uri,
                ) { v -> actions.onEdit { it.copy(host = v) } }
                NsFormField(
                    R.string.mqtt_field_port, form.port,
                    error = if (form.portError) stringResource(R.string.mqtt_error_port) else null,
                    keyboardType = KeyboardType.Number,
                    digitsOnly = true,
                ) { v -> actions.onEdit { it.copy(port = v) } }
            }
        }
        NsRowGroup {
            NsSwitchRow(
                title = stringResource(R.string.mqtt_field_tls),
                checked = form.tlsEnabled,
                onCheckedChange = { on -> actions.onEdit { it.copy(tlsEnabled = on) } },
                icon = R.drawable.ic_baseline_lock_24,
            )
        }
        NsPanel {
            Column(verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.m)) {
                NsFormField(R.string.mqtt_field_username, form.username) { v -> actions.onEdit { it.copy(username = v) } }
                PasswordField(form) { v -> actions.onEdit { it.copy(password = v) } }
            }
        }
        MoreOptions(form, actions)
        if (!form.isNew) SavedBrokerRows(form, actions, onRemove = { confirmRemove = true })
    }

    if (confirmRemove) {
        val extra = listOfNotNull(
            when {
                !form.isActive || !form.streaming -> null
                form.onlyDestination -> stringResource(R.string.mqtt_remove_streaming_only)
                else -> stringResource(R.string.mqtt_remove_streaming)
            },
            stringResource(R.string.mqtt_remove_last).takeIf { form.isLast },
        )
        NsConfirmationDialog(
            title = stringResource(R.string.mqtt_remove_title, form.name.ifBlank { form.host }),
            message = (listOf(stringResource(R.string.mqtt_remove_body)) + extra).joinToString(PARAGRAPH),
            confirmText = stringResource(R.string.remove),
            destructive = true,
            onConfirm = actions::onRemove,
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun MoreOptions(form: MqttBrokerForm, actions: MqttBrokerEditorActions) {
    NsRowGroup {
        NsRow(
            title = stringResource(R.string.mqtt_more_options),
            onClick = { actions.onEdit { it.copy(moreOptionsExpanded = !it.moreOptionsExpanded) } },
        ) {
            Icon(
                painter = painterResource(if (form.moreOptionsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (form.moreOptionsExpanded) {
            Column(modifier = Modifier.padding(bottom = NsTheme.spacing.m), verticalArrangement = Arrangement.spacedBy(NsTheme.spacing.s)) {
                NsFormField(
                    R.string.mqtt_field_topic_prefix, form.topicPrefix,
                    error = if (form.topicPrefixError) stringResource(R.string.mqtt_error_topic_prefix) else null,
                    supporting = stringResource(R.string.mqtt_field_topic_prefix_help),
                ) { v -> actions.onEdit { it.copy(topicPrefix = v) } }
                Text(stringResource(R.string.mqtt_field_qos), style = MaterialTheme.typography.titleSmall)
                Column(modifier = Modifier.fillMaxWidth()) {
                    QOS_LABELS.forEachIndexed { level, label ->
                        NsRadioRow(
                            title = stringResource(label),
                            selected = form.qos == level,
                            onSelect = { actions.onEdit { it.copy(qos = level) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedBrokerRows(form: MqttBrokerForm, actions: MqttBrokerEditorActions, onRemove: () -> Unit) {
    NsRowGroup {
        if (form.canShare) {
            NsRow(title = stringResource(R.string.mqtt_share), icon = R.drawable.ic_qr_code_scanner, onClick = actions::onShare)
            NsRowDivider()
        }
        NsRow(
            title = stringResource(R.string.mqtt_remove),
            icon = R.drawable.ic_delete,
            titleColor = MaterialTheme.colorScheme.error,
            iconTint = MaterialTheme.colorScheme.error,
            onClick = onRemove,
        )
    }
    if (form.canShare) NsSummaryText(stringResource(R.string.mqtt_share_password_warning))
}

@Composable
private fun PasswordField(form: MqttBrokerForm, onChange: (String) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = form.password,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.mqtt_field_password)) },
        supportingText = if (form.isNew) null else ({ Text(stringResource(R.string.mqtt_field_password_unchanged)) }),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            NsIconAction(
                icon = if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                label = stringResource(if (visible) R.string.mqtt_hide_password else R.string.mqtt_show_password),
                onClick = { visible = !visible },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private val QOS_LABELS = listOf(R.string.mqtt_qos_0, R.string.mqtt_qos_1, R.string.mqtt_qos_2)
private const val PARAGRAPH = "\n\n"

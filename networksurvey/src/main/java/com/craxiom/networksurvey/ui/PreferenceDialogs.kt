@file:JvmName("PreferenceDialogs")

package com.craxiom.networksurvey.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.dialogs.ComposeDialogFragment
import com.craxiom.networksurvey.ui.common.dialogs.NsAlertDialogScaffold
import com.craxiom.networksurvey.ui.preview.NsPreview
import com.craxiom.networksurvey.ui.preview.PreviewDayNight
import java.util.function.IntConsumer
import kotlin.math.roundToInt

private const val TAG_BATTERY_THRESHOLD = "BatteryThreshold"
private const val TAG_COLOR_CHOICE = "ColorChoice"

private const val BATTERY_THRESHOLD_MAX = 95f
private const val BATTERY_THRESHOLD_STEP = 5

/**
 * Resolves the host [FragmentManager] from a preference's context, which may be wrapped in a
 * [ContextWrapper] (such as a themed context) around the hosting [FragmentActivity].
 */
fun fragmentManagerFrom(context: Context): FragmentManager? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current.supportFragmentManager
        current = current.baseContext
    }
    return null
}

/**
 * Shows the battery threshold slider dialog. The chosen value (a percentage, 0 = disabled) is
 * delivered to [onValueSelected] when the user confirms.
 */
fun showBatteryThresholdDialog(
    fragmentManager: FragmentManager,
    title: String,
    initialValue: Int,
    onValueSelected: IntConsumer,
) {
    ComposeDialogFragment.show(fragmentManager, TAG_BATTERY_THRESHOLD) { dismiss ->
        BatteryThresholdDialog(
            title = title,
            initialValue = initialValue,
            onConfirm = { onValueSelected.accept(it) },
            onDismiss = dismiss,
        )
    }
}

/**
 * Shows the color selection dialog with a colored swatch next to each option. The selection is
 * applied immediately on tap and delivered to [onSelected].
 */
fun showColorChoiceDialog(
    fragmentManager: FragmentManager,
    title: String,
    names: Array<String>,
    colors: IntArray,
    selectedIndex: Int,
    onSelected: IntConsumer,
) {
    ComposeDialogFragment.show(fragmentManager, TAG_COLOR_CHOICE) { dismiss ->
        ColorChoiceDialog(
            title = title,
            names = names.toList(),
            colors = colors.toList(),
            selectedIndex = selectedIndex,
            onSelected = { onSelected.accept(it) },
            onDismiss = dismiss,
        )
    }
}

@Composable
private fun BatteryThresholdDialog(
    title: String,
    initialValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(initialValue.toFloat()) }
    val value = sliderValue.roundToInt()

    NsAlertDialogScaffold(
        title = title,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(value)
                onDismiss()
            }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        body = {
            Text(
                text = if (value == 0) {
                    stringResource(R.string.battery_management_disabled)
                } else {
                    "$value%"
                },
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (value == 0) {
                    stringResource(R.string.battery_management_disabled_description)
                } else {
                    stringResource(R.string.battery_pause_active_description, value)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0f..BATTERY_THRESHOLD_MAX,
                steps = (BATTERY_THRESHOLD_MAX.toInt() / BATTERY_THRESHOLD_STEP) - 1,
            )
        },
    )
}

@Composable
private fun ColorChoiceDialog(
    title: String,
    names: List<String>,
    colors: List<Int>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    NsAlertDialogScaffold(
        title = title,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        body = {
            names.forEachIndexed { index, name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = index == selectedIndex,
                            onClick = {
                                onSelected(index)
                                onDismiss()
                            },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = index == selectedIndex,
                        onClick = {
                            onSelected(index)
                            onDismiss()
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Spacer(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(colors.getOrElse(index) { 0 }))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = name)
                }
            }
        },
    )
}

@PreviewDayNight
@Composable
private fun BatteryThresholdDialogPreview() {
    NsPreview {
        BatteryThresholdDialog(title = "Battery threshold", initialValue = 20, onConfirm = {}, onDismiss = {})
    }
}

@PreviewDayNight
@Composable
private fun ColorChoiceDialogPreview() {
    NsPreview {
        ColorChoiceDialog(
            title = "Color",
            names = listOf("Default", "Red", "Green"),
            colors = listOf(0xFF888888.toInt(), 0xFFE53935.toInt(), 0xFF43A047.toInt()),
            selectedIndex = 1,
            onSelected = {},
            onDismiss = {},
        )
    }
}

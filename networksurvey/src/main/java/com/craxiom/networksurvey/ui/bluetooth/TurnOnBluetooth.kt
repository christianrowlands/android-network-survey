package com.craxiom.networksurvey.ui.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.craxiom.networksurvey.ui.common.openSystemSettings
import timber.log.Timber

/**
 * @return What the "Turn on" button of a Bluetooth view runs: it asks Android to turn Bluetooth on
 * in place, or opens the Bluetooth settings if it cannot. [onResult] runs when the request returns.
 */
@Composable
fun rememberTurnOnBluetooth(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult() }
    return remember(context, launcher) {
        {
            try {
                launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "No activity to turn Bluetooth on; opening the Bluetooth settings")
                openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            } catch (e: SecurityException) {
                Timber.w(e, "Not allowed to ask for Bluetooth; opening the Bluetooth settings")
                openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            }
        }
    }
}

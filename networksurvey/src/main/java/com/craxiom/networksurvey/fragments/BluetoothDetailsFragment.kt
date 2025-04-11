package com.craxiom.networksurvey.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.craxiom.messaging.BluetoothRecord
import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.constants.NetworkSurveyConstants.PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS
import com.craxiom.networksurvey.listeners.IBluetoothSurveyRecordListener
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.UNKNOWN_RSSI
import com.craxiom.networksurvey.ui.bluetooth.BluetoothDetailsScreen
import com.craxiom.networksurvey.ui.bluetooth.BluetoothDetailsViewModel
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.PreferenceUtils
import timber.log.Timber

const val BLUETOOTH_DATA_KEY = "bluetoothData"

/**
 * The fragment that displays the details of a single Bluetooth device from the scan results.
 */
class BluetoothDetailsFragment : AServiceDataFragment(), IBluetoothSurveyRecordListener {
    private lateinit var bluetoothData: BluetoothRecordData
    private lateinit var viewModel: BluetoothDetailsViewModel

    private lateinit var sharedPreferences: SharedPreferences
    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS) {
                val bluetoothScanRateMs = PreferenceUtils.getScanRatePreferenceMs(
                    PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS,
                    NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS,
                    context
                )
                viewModel.setScanRateSeconds(bluetoothScanRateMs / 1_000)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                viewModel = viewModel()
                viewModel.bluetoothData = bluetoothData
                if (bluetoothData.hasSignalStrength()) {
                    viewModel.addInitialRssi(bluetoothData.signalStrength.value)
                } else {
                    viewModel.addInitialRssi(UNKNOWN_RSSI)
                }

                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
                val scanRateMs = PreferenceUtils.getScanRatePreferenceMs(
                    PROPERTY_BLUETOOTH_SCAN_INTERVAL_SECONDS,
                    NetworkSurveyConstants.DEFAULT_BLUETOOTH_SCAN_INTERVAL_SECONDS,
                    context
                )
                viewModel.setScanRateSeconds(scanRateMs / 1_000)

                NsTheme {
                    BluetoothDetailsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() },
                        onNavigateToSettings = { navigateToSettings() }
                    )
                }
            }
        }

        return composeView
    }

    override fun onResume() {
        super.onResume()

        startAndBindToService()
    }

    override fun onPause() {
        try {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        } catch (e: UninitializedPropertyAccessException) {
            // no-op
        }

        super.onPause()
    }

    override fun onSurveyServiceConnected(service: NetworkSurveyService?) {
        if (service == null) return
        service.registerBluetoothSurveyRecordListener(this)
    }

    override fun onSurveyServiceDisconnecting(service: NetworkSurveyService?) {
        if (service == null) return
        service.unregisterBluetoothSurveyRecordListener(this)

        super.onSurveyServiceDisconnecting(service)
    }

    override fun onBluetoothSurveyRecord(bluetoothRecord: BluetoothRecord?) {
        if (bluetoothRecord == null) return
        if (bluetoothRecord.data.sourceAddress.equals(bluetoothData.sourceAddress)) {
            if (bluetoothRecord.data.hasSignalStrength()) {
                viewModel.addNewRssi(bluetoothRecord.data.signalStrength.value)
            } else {
                Timber.i("No signal strength present for ${bluetoothData.sourceAddress} in the bluetooth record")
                viewModel.addNewRssi(UNKNOWN_RSSI)
            }
        }
    }

    override fun onBluetoothSurveyRecords(bluetoothRecords: MutableList<BluetoothRecord>?) {
        val matchedRecord =
            bluetoothRecords?.find { it.data.sourceAddress.equals(bluetoothData.sourceAddress) }

        if (matchedRecord == null) {
            Timber.i("No bluetooth record found for ${bluetoothData.sourceAddress} in the bluetooth scan results")
            viewModel.addNewRssi(UNKNOWN_RSSI)
            return
        }

        if (matchedRecord.data.hasSignalStrength()) {
            viewModel.addNewRssi(matchedRecord.data.signalStrength.value)
        } else {
            Timber.i("No signal strength present for ${bluetoothData.sourceAddress} in the bluetooth record")
            viewModel.addNewRssi(UNKNOWN_RSSI)

        }
    }

    /**
     * Sets the BluetoothRecordData that this fragment should display. This needs to be called
     * right after the fragment is created.
     *
     * @param bluetoothRecordData The BluetoothRecordData to display.
     */
    fun setBluetoothData(bluetoothRecordData: BluetoothRecordData) {
        this.bluetoothData = bluetoothRecordData
        // TODO We might need to update the ViewModel with the new BluetoothRecordData if it has already
        // been initialized
    }

    /**
     * Navigates to the Settings UI (primarily for the user to change the scan rate)
     */
    fun navigateToSettings() {
        val nsActivity = activity ?: return

        val viewModel = ViewModelProvider(nsActivity)[SharedViewModel::class.java]
        viewModel.triggerNavigationToSettings()
    }

    fun navigateBack() {
        val nsActivity = activity ?: return
        nsActivity.onBackPressed()
    }
}
package com.craxiom.networksurvey.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.craxiom.networksurvey.SimChangeReceiver
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.cellular.TowerMapScreen
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.cellular.model.TowerMapLibreViewModel
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.PreferenceUtils
import timber.log.Timber
import java.util.Collections

/**
 * A map view of all the towers in the area as pulled from the NS Tower Service.
 */
class TowerMapFragment : AServiceDataFragment(), ICellularSurveyRecordListener {
    private var viewModel: TowerMapLibreViewModel? = null
    private lateinit var composeView: ComposeView
    private var paddingValues: PaddingValues = PaddingValues(2.dp)
    private var servingCell: ServingCellInfo? = null
    private var locationListener: LocationListener? = null
    private val simBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Timber.i("SIM State Change Detected. Resetting the tower map VM")
            viewModel?.resetSimCount()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context?.let {
            LocalBroadcastManager.getInstance(it).registerReceiver(
                simBroadcastReceiver,
                IntentFilter(SimChangeReceiver.SIM_CHANGED_INTENT)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Grab the last known serving cell from SharedViewModel
        servingCell = ViewModelProvider(requireActivity())[SharedViewModel::class.java]
            .latestServingCellInfo

        composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }

        setupComposeView(servingCell)
        return composeView
    }

    override fun onResume() {
        super.onResume()
        checkLocationServicesEnabledAndPrompt()
        startAndBindToService()
        // Update serving cell overlay without resetting the entire UI
        updateServingCellOverlay()
    }

    override fun onPause() {
        super.onPause()
        // Save last viewport
        context?.let { context ->
            viewModel?.saveViewport(context)
        }
    }

    override fun onDestroy() {
        context?.let {
            LocalBroadcastManager.getInstance(it).unregisterReceiver(simBroadcastReceiver)
        }
        super.onDestroy()
    }

    override fun onSurveyServiceConnected(service: NetworkSurveyService?) {
        if (service == null) return
        service.registerCellularSurveyRecordListener(this)

        var removeListener = false
        // Try centering map to last known location once
        service.primaryLocationListener?.latestLocation?.let { loc ->
            viewModel?.let { vm ->
                removeListener = vm.setMapCenterLocation(loc)
            }
        }
        if (!removeListener) {
            locationListener = LocationListener { location ->
                viewModel?.let { vm ->
                    removeListener = vm.setMapCenterLocation(location)
                    if (removeListener) service.unregisterLocationListener(locationListener)
                }
            }
            service.registerLocationListener(locationListener)
        }
    }

    override fun onSurveyServiceDisconnecting(service: NetworkSurveyService?) {
        service ?: return
        service.unregisterCellularSurveyRecordListener(this)
        locationListener?.let { service.unregisterLocationListener(it) }
        super.onSurveyServiceDisconnecting(service)
    }

    override fun onCellularBatch(
        cellularGroup: MutableList<CellularRecordWrapper?>?,
        subscriptionId: Int
    ) {
        viewModel?.onCellularBatchResults(cellularGroup, subscriptionId)
    }

    fun setPaddingInsets(paddingInsets: PaddingValues) {
        paddingValues = paddingInsets
    }


    /**
     * Checks if the location services are enabled on the device. If they are not, then a dialog is shown to the user
     * explaining that they need to enable location services for a better experience.
     */
    private fun checkLocationServicesEnabledAndPrompt() {
        val locationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = LocationManagerCompat.isLocationEnabled(locationManager)

        if (!isLocationEnabled) {
            AlertDialog.Builder(requireContext())
                .setTitle("Location Services Disabled")
                .setMessage("Location services are disabled. Enable them to show your location on the map.")
                .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun setupComposeView(servingCell: ServingCellInfo?) {
        composeView.setContent {
            viewModel = viewModel<TowerMapLibreViewModel>()
            viewModel?.setPaddingInsets(paddingValues)
            viewModel?.setTowerSource(
                PreferenceUtils.getLastSelectedTowerSource(requireContext())
            )
            servingCell?.servingCell?.let { cell ->
                if (cell.cellularProtocol != CellularProtocol.NONE) {
                    viewModel?.setSelectedRadioType(cell.cellularProtocol.name)
                }
                cell.plmn?.let { viewModel?.setPlmnFilter(it) }
            }

            NsTheme {
                TowerMapScreen(
                    viewModel = viewModel!!,
                    onBackButtonPressed = ::navigateBack,
                    onNavigateToTowerMapSettings = ::navigateToTowerMapSettings
                )
            }

            // Ensure we display the initial serving cell overlay
            updateServingCellOverlay()
        }
    }
    
    /**
     * Updates the serving cell overlay without resetting the radio type selection.
     * This is called from onResume to ensure the overlay is current after screen lock/unlock.
     */
    private fun updateServingCellOverlay() {
        servingCell?.let { info ->
            onCellularBatch(
                Collections.singletonList(info.servingCell),
                info.subscriptionId
            )
        }
    }

    private fun navigateToTowerMapSettings() {
        ViewModelProvider(requireActivity())[SharedViewModel::class.java]
            .triggerNavigationToTowerMapSettings()
    }

    private fun navigateBack() {
        requireActivity().onBackPressed()
    }
}

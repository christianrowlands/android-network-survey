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
import com.craxiom.networksurvey.ui.cellular.model.TowerMapViewModel
import com.craxiom.networksurvey.ui.main.SharedViewModel
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.util.PreferenceUtils
import timber.log.Timber
import java.util.Collections

/**
 * A map view of all the towers in the area as pulled from the NS Tower Service.
 */
class TowerMapFragment : AServiceDataFragment(), ICellularSurveyRecordListener {
    private var viewModel: TowerMapViewModel? = null
    private lateinit var composeView: ComposeView
    private var paddingValues: PaddingValues = PaddingValues(2.dp, 2.dp, 2.dp, 2.dp)
    private var servingCell: ServingCellInfo? = null
    private var locationListener: LocationListener? = null
    private var simBroadcastReceiver = object : BroadcastReceiver(
    ) {
        override fun onReceive(context: Context, intent: Intent) {
            Timber.i("SIM State Change Detected. Updating the tower map view model")
            viewModel?.resetSimCount()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = context
        if (context != null) {
            LocalBroadcastManager.getInstance(context).registerReceiver(
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
        val viewModel = ViewModelProvider(requireActivity())[SharedViewModel::class.java]
        servingCell = viewModel.latestServingCellInfo

        composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }

        if (PreferenceUtils.hasAcceptedMapPrivacy(requireContext())) {
            setupComposeView(servingCell)
        }

        return composeView
    }

    override fun onResume() {
        super.onResume()

        if (PreferenceUtils.hasAcceptedMapPrivacy(requireContext())) {
            setupComposeView(servingCell)
            viewModel?.mapView?.onResume()
        }

        checkAcceptedMapPrivacy()
        checkLocationServicesEnabledAndPrompt()

        startAndBindToService()
    }

    override fun onPause() {
        viewModel?.mapView?.boundingBox?.let {
            PreferenceUtils.saveTowerMapViewBoundingBox(requireContext(), it)
        }

        viewModel?.mapView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        val context = context
        if (context != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(simBroadcastReceiver)
        }

        super.onDestroy()
    }

    override fun onSurveyServiceConnected(service: NetworkSurveyService?) {
        if (service == null) return
        service.registerCellularSurveyRecordListener(this)

        var removeListener = false
        val initialLocation = service.primaryLocationListener?.latestLocation
        initialLocation?.let {
            if (viewModel == null) {
                removeListener = false
            } else {
                removeListener = viewModel!!.setMapCenterLocation(it)
            }
        }

        if (!removeListener) {
            locationListener = LocationListener { location ->
                if (viewModel == null) return@LocationListener
                removeListener = viewModel!!.setMapCenterLocation(location)
                if (removeListener) service.unregisterLocationListener(locationListener)
            }
            service.registerLocationListener(locationListener)
        }
    }

    override fun onSurveyServiceDisconnecting(service: NetworkSurveyService?) {
        if (service == null) return
        service.unregisterCellularSurveyRecordListener(this)

        locationListener?.let {
            service.unregisterLocationListener(it)
        }

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
     * Checks if the user has accepted the privacy implications of using the tower map feature. If they have not,
     * then a dialog is shown to them explaining the privacy implications.
     */
    private fun checkAcceptedMapPrivacy() {
        if (!PreferenceUtils.hasAcceptedMapPrivacy(requireContext())) {
            showPrivacyDialog()
        }
    }

    /**
     * Shows a dialog to the user explaining the privacy implications of using the tower map feature.
     */
    private fun showPrivacyDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Privacy Notice")
        builder.setMessage(
            """
            When using the tower map feature, a request is made from your device to our Network Survey server. This request will reveal your device's public IP address and the location associated with the map view. 
            
            By continuing, you accept these terms and allow the feature to function as intended. If you reject, the feature will be disabled.
        """.trimIndent()
        )
        builder.setPositiveButton("Accept") { dialog, _ ->
            PreferenceUtils.setAcceptMapPrivacy(requireContext(), true)
            setupComposeView(servingCell)
            dialog.dismiss()
        }
        builder.setNegativeButton("Reject") { dialog, _ ->
            PreferenceUtils.setAcceptMapPrivacy(requireContext(), false)
            dialog.dismiss()
            navigateBack()
        }
        builder.show()
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
            viewModel = viewModel()
            viewModel!!.setPaddingInsets(paddingValues)
            viewModel!!.setTowerSource(PreferenceUtils.getLastSelectedTowerSource(requireContext()))
            if (servingCell?.servingCell != null) {
                if (servingCell.servingCell.cellularProtocol != CellularProtocol.NONE) {
                    viewModel!!.setSelectedRadioType(servingCell.servingCell.cellularProtocol.name)
                }
                val plmn = servingCell.servingCell.plmn
                if (plmn != null) {
                    viewModel!!.setPlmnFilter(plmn)
                }
            }

            NsTheme {
                TowerMapScreen(
                    viewModel = viewModel!!,
                    onBackButtonPressed = ::navigateBack,
                    onNavigateToTowerMapSettings = ::navigateToTowerMapSettings
                )
            }

            if (servingCell != null)
                onCellularBatch(
                    Collections.singletonList(servingCell.servingCell),
                    servingCell.subscriptionId
                )
        }
    }

    private fun navigateToTowerMapSettings() {
        val nsActivity = activity ?: return

        val viewModel = ViewModelProvider(nsActivity)[SharedViewModel::class.java]
        viewModel.triggerNavigationToTowerMapSettings()
    }

    private fun navigateBack() {
        val nsActivity = activity ?: return
        nsActivity.onBackPressed()
    }
}

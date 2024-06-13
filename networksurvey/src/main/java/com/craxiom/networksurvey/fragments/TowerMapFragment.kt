package com.craxiom.networksurvey.fragments

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.cellular.TowerMapScreen
import com.craxiom.networksurvey.ui.cellular.model.ServingCellInfo
import com.craxiom.networksurvey.ui.cellular.model.TowerMapViewModel
import com.craxiom.networksurvey.util.NsTheme
import com.craxiom.networksurvey.util.PreferenceUtils
import java.util.Collections

/**
 * A map view of all the towers in the area as pulled from the NS Tower Service.
 */
class TowerMapFragment : AServiceDataFragment(), MenuProvider, ICellularSurveyRecordListener {
    private var viewModel: TowerMapViewModel? = null
    private lateinit var composeView: ComposeView
    private var servingCell: ServingCellInfo? = null
    private var locationListener: LocationListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args: TowerMapFragmentArgs by navArgs()
        servingCell = args.servingCell
        activity?.addMenuProvider(this, getViewLifecycleOwner())

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
        checkLocationServicesEnabled()

        startAndBindToService()
    }

    override fun onPause() {
        viewModel?.mapView?.onPause()
        super.onPause()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.cellular_map_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.action_open_tower_map_info) {
            showTowerMapInfoDialog()
            return true
        }
        return false
    }

    override fun onSurveyServiceConnected(service: NetworkSurveyService?) {
        if (service == null) return
        service.registerCellularSurveyRecordListener(this)

        var removeListener = false
        val initialLocation = service.primaryLocationListener?.latestLocation
        initialLocation?.let {
            if (viewModel == null) {
                removeListener = true
            } else {
                removeListener = viewModel!!.updateLocation(it)
            }

        }

        if (!removeListener) {
            locationListener = LocationListener { location ->
                removeListener = viewModel!!.updateLocation(location)
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
        cellularGroup: MutableList<CellularRecordWrapper>?,
        subscriptionId: Int
    ) {
        viewModel?.onCellularBatchResults(cellularGroup, subscriptionId)
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
            findNavController().popBackStack() // Go back to the previous fragment
        }
        builder.show()
    }

    /**
     * Checks if the location services are enabled on the device. If they are not, then a dialog is shown to the user
     * explaining that they need to enable location services for a better experience.
     */
    private fun checkLocationServicesEnabled() {
        val locationManager =
            requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled =
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
            )

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
            viewModel!!.servingCellInfo = servingCell
            if (servingCell?.servingCell != null && servingCell.servingCell.cellularProtocol != CellularProtocol.NONE) {
                viewModel!!.setSelectedRadioType(servingCell.servingCell.cellularProtocol.name)
            }

            NsTheme {
                TowerMapScreen(viewModel = viewModel!!)
            }

            if (servingCell != null)
                onCellularBatch(
                    Collections.singletonList(servingCell.servingCell),
                    servingCell.subscriptionId
                )
        }
    }

    /**
     * Shows a dialog to the user explaining where the tower map data comes from and some nuances of it.
     */
    private fun showTowerMapInfoDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Tower Map Information")
        builder.setMessage(
            """
            The tower locations are sourced from OpenCelliD.
            Please note that these locations may not be accurate as they are generated from crowd-sourced data and based on survey results.
            The tower locations are provided for your convenience, but they should not be relied upon for precise accuracy.
            We recommend verifying tower locations through additional sources if accuracy is critical.
        """.trimIndent()
        )
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }
}

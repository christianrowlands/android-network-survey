package com.craxiom.networksurvey.fragments

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
    private lateinit var viewModel: TowerMapViewModel
    private lateinit var composeView: ComposeView
    private lateinit var servingCell: ServingCellInfo

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

        checkAcceptedMapPrivacy()

        startAndBindToService()
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
    }

    override fun onSurveyServiceDisconnecting(service: NetworkSurveyService?) {
        if (service == null) return
        service.unregisterCellularSurveyRecordListener(this)

        super.onSurveyServiceDisconnecting(service)
    }

    override fun onCellularBatch(
        cellularGroup: MutableList<CellularRecordWrapper>?,
        subscriptionId: Int
    ) {
        // FIXME FINISH THIS
        /*viewModel.onCellularBatchResults(
            ServingCellInfo(
                cellularGroup?.firstOrNull { it.data.subscriptionId == subscriptionId } ?: return
            )
        )*/
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

    private fun setupComposeView(servingCell: ServingCellInfo) {
        composeView.setContent {
            viewModel = viewModel()
            viewModel.servingCellInfo = servingCell

            NsTheme {
                TowerMapScreen(viewModel = viewModel)
            }

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

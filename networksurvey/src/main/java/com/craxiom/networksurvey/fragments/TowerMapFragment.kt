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
import androidx.navigation.fragment.navArgs
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.cellular.TowerMapScreen
import com.craxiom.networksurvey.ui.cellular.model.TowerMapViewModel
import com.craxiom.networksurvey.util.NsTheme
import com.craxiom.networksurvey.util.SatelliteUtil
import com.craxiom.networksurvey.util.SortUtil
import java.util.Collections

/**
 * A map view of all the towers in the area as pulled from the NS Tower Service.
 */
class TowerMapFragment : AServiceDataFragment(), MenuProvider, ICellularSurveyRecordListener {
    private lateinit var viewModel: TowerMapViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args: TowerMapFragmentArgs by navArgs()
        val servingCell = args.servingCell
        activity?.addMenuProvider(this, getViewLifecycleOwner())

        val composeView = ComposeView(requireContext())

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
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

        return composeView
    }

    override fun onResume() {
        super.onResume()

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

    private fun showTowerMapInfoDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Tower Map Information")
        builder.setMessage("""
            The tower locations are sourced from OpenCelliD.
            Please note that these locations may not be accurate as they are generated from crowd-sourced data and based on survey results.
            The tower locations are provided for your convenience, but they should not be relied upon for precise accuracy.
            We recommend verifying tower locations through additional sources if accuracy is critical.
        """.trimIndent())
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }
}

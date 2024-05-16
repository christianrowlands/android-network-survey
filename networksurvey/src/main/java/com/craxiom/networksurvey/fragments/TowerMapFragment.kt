package com.craxiom.networksurvey.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.fragment.navArgs
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.services.NetworkSurveyService
import com.craxiom.networksurvey.ui.cellular.TowerMapScreen
import com.craxiom.networksurvey.ui.cellular.model.TowerMapViewModel
import com.craxiom.networksurvey.util.NsTheme
import java.util.Collections

/**
 * A map view of all the towers in the area as pulled from the NS Tower Service.
 */
class TowerMapFragment : AServiceDataFragment(), ICellularSurveyRecordListener {
    private lateinit var viewModel: TowerMapViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args: TowerMapFragmentArgs by navArgs()
        val servingCell = args.servingCell

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
}

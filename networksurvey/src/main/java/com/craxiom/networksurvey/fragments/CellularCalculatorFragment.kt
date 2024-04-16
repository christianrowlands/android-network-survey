package com.craxiom.networksurvey.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.craxiom.networksurvey.ui.cellular.CalculatorScreen
import com.craxiom.networksurvey.ui.cellular.model.CalculatorViewModel
import com.craxiom.networksurvey.util.NsTheme

/**
 * The fragment that is responsible for displaying the cellular calculators screen.
 */
class CellularCalculatorFragment : Fragment() {

    private val viewModel: CalculatorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                NsTheme {
                    CalculatorScreen(viewModel)
                }
            }
        }
    }
}

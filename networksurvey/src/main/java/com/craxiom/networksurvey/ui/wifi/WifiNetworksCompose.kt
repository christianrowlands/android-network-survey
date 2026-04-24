package com.craxiom.networksurvey.ui.wifi

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.craxiom.networksurvey.model.WifiNetwork
import com.craxiom.networksurvey.ui.theme.NsTheme
import com.craxiom.networksurvey.ui.wifi.model.WifiListViewModel

/**
 * Java-friendly bridge for installing the Compose Wi-Fi list UI into the fragment's
 * [ComposeView]. Keeps the fragment Java by exposing a single static entry point.
 */
object WifiNetworksCompose {

    @JvmStatic
    fun setContent(
        composeView: ComposeView,
        viewModel: WifiListViewModel,
        onNetworkSelected: WifiNetworkSelected,
        onTogglePause: Runnable,
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            NsTheme {
                WifiListScreen(
                    viewModel = remember { viewModel },
                    onNetworkSelected = { network -> onNetworkSelected.invoke(network) },
                    onTogglePause = { onTogglePause.run() },
                )
            }
        }
    }

    /** Functional interface compatible with Java method references (e.g. `this::navigateToWifiDetails`). */
    fun interface WifiNetworkSelected {
        fun invoke(network: WifiNetwork)
    }
}

package com.craxiom.networksurvey.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.compose.AndroidFragment
import androidx.navigation.NavHostController
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.fragments.CodeScannerFragment
import com.craxiom.networksurvey.fragments.NsAnalyticsQrScannerFragment
import com.craxiom.networksurvey.fragments.QrCodeShareFragment
import com.craxiom.networksurvey.ui.main.appbar.TitleBar

// The screens that stay Fragments (the camera scanners and the QR share), each hosted
// with AndroidFragment under a title bar with Back.

@Composable
fun QrCodeScannerInCompose(mainNavController: NavHostController) {
    TitledPage(stringResource(R.string.mqtt_broker_scan), mainNavController) { modifier ->
        AndroidFragment<CodeScannerFragment>(modifier = modifier)
    }
}

@Composable
fun QrCodeShareInCompose(mainNavController: NavHostController) {
    TitledPage(stringResource(R.string.mqtt_share), mainNavController) { modifier ->
        AndroidFragment<QrCodeShareFragment>(modifier = modifier)
    }
}

@Composable
fun NsAnalyticsQrScannerInCompose(mainNavController: NavHostController) {
    TitledPage(stringResource(R.string.ns_analytics_scan_title), mainNavController) { modifier ->
        AndroidFragment<NsAnalyticsQrScannerFragment>(modifier = modifier)
    }
}

/** A page above the tabs: a title bar with Back, then the content (a fragment or a Compose page). */
@Composable
internal fun TitledPage(title: String, mainNavController: NavHostController, fragment: @Composable (Modifier) -> Unit) {
    Scaffold(topBar = { TitleBar(title) { mainNavController.navigateUp() } }) { innerPadding ->
        // Fill the page: AndroidFragment wraps its view otherwise, and match_parent layouts collapse.
        fragment(
            Modifier
                .padding(paddingValues = innerPadding)
                .fillMaxSize()
        )
    }
}

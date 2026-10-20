package com.craxiom.networksurvey.ui.wifi.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.common.PermissionAccess
import com.craxiom.networksurvey.ui.signals.SignalsBlockedNotice
import com.craxiom.networksurvey.ui.wifi.model.WifiScanState

/** The fixes a Wi-Fi page offers when the phone cannot scan. */
interface WifiScanFixes {
    fun onAllow()
    fun onOpenAppSettings()
    fun onTurnOnLocation()
    fun onTurnOnWifi()
}

/**
 * The notice at the top of the Wi-Fi details and spectrum pages while the phone cannot scan; the
 * page keeps what it shows, and the one button fixes what is in the way. Nothing while it can.
 */
@Composable
fun WifiBlockedNotice(
    scanState: WifiScanState,
    permissionAccess: PermissionAccess,
    fixes: WifiScanFixes,
    modifier: Modifier = Modifier,
) {
    when (scanState) {
        WifiScanState.PERMISSION_NEEDED -> {
            val blocked = permissionAccess == PermissionAccess.BLOCKED
            SignalsBlockedNotice(
                icon = R.drawable.ic_info,
                text = stringResource(R.string.wifi_blocked_permission_notice),
                action = stringResource(if (blocked) R.string.open_settings else R.string.survey_location_allow),
                onAction = if (blocked) fixes::onOpenAppSettings else fixes::onAllow,
                modifier = modifier,
            )
        }

        WifiScanState.LOCATION_OFF -> SignalsBlockedNotice(
            icon = R.drawable.ic_location_off,
            text = stringResource(R.string.signals_blocked_location_notice),
            action = stringResource(R.string.survey_location_turn_on),
            onAction = fixes::onTurnOnLocation,
            modifier = modifier,
        )

        WifiScanState.WIFI_OFF -> SignalsBlockedNotice(
            icon = R.drawable.ic_wifi,
            text = stringResource(R.string.wifi_blocked_off_notice),
            action = stringResource(R.string.survey_location_turn_on),
            onAction = fixes::onTurnOnWifi,
            modifier = modifier,
        )

        WifiScanState.READY -> Unit
    }
}

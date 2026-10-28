package com.craxiom.networksurvey.ui.common

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.ui.theme.NsTheme

private val CAMERA_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

/**
 * Remembers a [PermissionRequest] for the camera that calls [onGranted] once it may be used. On a
 * device with no camera at all it never asks or calls back; [CameraPermissionNotice] says why.
 */
@Composable
fun rememberCameraPermissionRequest(onGranted: () -> Unit): PermissionRequest =
    rememberPermissionRequest(CAMERA_PERMISSIONS, hardwareFeature = PackageManager.FEATURE_CAMERA_ANY, onGranted = onGranted)

/**
 * The line shown after the camera was refused: why it is needed, with [Allow] while Android can
 * still ask and [Open settings] once it cannot. On a device with no camera it says so instead, with
 * no action. It answers a tap on a Scan control, so it scrolls itself into view when it appears or
 * changes, even when that control sat at the bottom edge of a scrolling page.
 */
@Composable
fun CameraPermissionNotice(request: PermissionRequest, modifier: Modifier = Modifier) {
    if (!request.hardwareMissing && request.access == PermissionAccess.AVAILABLE) return
    val context = LocalContext.current
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(request.access, request.hardwareMissing) { bringIntoView.bringIntoView() }
    if (request.hardwareMissing) {
        InlineNotice(
            text = stringResource(R.string.camera_missing),
            icon = R.drawable.ic_info,
            color = NsTheme.colors.status.warningText,
            modifier = modifier.bringIntoViewRequester(bringIntoView),
        )
        return
    }
    InlineNotice(
        text = stringResource(R.string.camera_needed),
        icon = R.drawable.ic_info,
        color = NsTheme.colors.status.warningText,
        modifier = modifier.bringIntoViewRequester(bringIntoView),
    ) {
        if (request.access == PermissionAccess.DENIED) {
            NsTextAction(stringResource(R.string.camera_allow), request::request)
        } else {
            NsTextAction(stringResource(R.string.open_settings), onClick = { openAppSettings(context) })
        }
    }
}

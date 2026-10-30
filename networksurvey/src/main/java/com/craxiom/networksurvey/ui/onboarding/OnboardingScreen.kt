package com.craxiom.networksurvey.ui.onboarding

import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.craxiom.networksurvey.AppPermissions
import com.craxiom.networksurvey.ui.common.openLocationSettings

/**
 * First run, stateful: which card shows (kept across rotation), the launch permission request,
 * Back to the previous card, and handing the user's choice to [viewModel]. On the first card Back
 * leaves the app and first run shows again next time.
 *
 * @param onFinished Called after [viewModel] stored the choice (null for Skip), so the caller can
 *                   open the next page, such as the NS Analytics connect walkthrough.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: (OnboardingChoice?) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(OnboardingStep.INTRO) }
    // Bumped on resume and after the permission request, so the permission and location facts are read again
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val permissions = remember { OnboardingPermission.forRequested(AppPermissions.PERMISSIONS) }
    val missing = remember(refresh) { AppPermissions.missingRegularPermissions(context) }
    val locationOff = remember(refresh) { !isLocationEnabled(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        step = OnboardingStep.DESTINATION
    }

    BackHandler(enabled = step != OnboardingStep.INTRO) { step = step.previous() }

    val actions = object : OnboardingActions {
        override fun onContinue() {
            step = step.next()
        }

        override fun onAllowPermissions() {
            viewModel.onPermissionsRequested()
            launcher.launch(missing)
        }

        override fun onSkip() = finish(null)

        override fun onTurnOnLocation() = openLocationSettings(context)

        override fun onChoose(choice: OnboardingChoice) = finish(choice)

        private fun finish(choice: OnboardingChoice?) {
            viewModel.finish(choice)
            onFinished(choice)
        }
    }

    OnboardingContent(
        step = step,
        permissions = permissions,
        permissionsMissing = missing.isNotEmpty(),
        locationOff = locationOff,
        actions = actions,
    )
}

private fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
    return LocationManagerCompat.isLocationEnabled(manager)
}

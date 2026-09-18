package com.craxiom.networksurvey.ui.cellular.bands

import androidx.fragment.app.FragmentManager
import com.craxiom.networksurvey.data.band.BandTapTarget
import com.craxiom.networksurvey.ui.common.dialogs.ComposeDialogFragment

/**
 * Opens the band reference from a Fragment that is still built on XML views.
 *
 * A composable cannot be called from Java, so the cellular details screen goes through this
 * instead of building the Compose content itself. Kept as a launcher rather than a full Compose
 * host because the details screen only needs this one dialog.
 */
object BandLookupLauncher {

    private const val DIALOG_TAG = "band_detail_dialog"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, target: BandTapTarget) {
        ComposeDialogFragment.show(fragmentManager, DIALOG_TAG) { dismiss ->
            BandLookupDialog(target = target, onDismiss = dismiss)
        }
    }
}

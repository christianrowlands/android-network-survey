package com.craxiom.networksurvey.ui.manufacturer

import android.content.Context
import android.content.RestrictionsManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.data.oui.OuiResult
import com.craxiom.networksurvey.data.oui.OuiStatus

/**
 * UI-level label for a single-MAC manufacturer lookup used by the WiFi details screen.
 *
 * Also models the two "off" paths, user-toggled vs MDM-locked, so the Compose layer can pick
 * the correct copy without re-doing the detection logic.
 */
sealed class ManufacturerLabel {
    data object Loading : ManufacturerLabel()
    data class Resolved(val vendor: String) : ManufacturerLabel()
    data object Randomized : ManufacturerLabel()
    data object Unknown : ManufacturerLabel()
    data object Private : ManufacturerLabel()
    data object SharedVendorBlock : ManufacturerLabel()
    data object Offline : ManufacturerLabel()

    /** User flipped the pref off themselves. Offer a "enable in Settings" link. */
    data object DisabledByUser : ManufacturerLabel()

    /** Enterprise admin locked the pref off via MDM. No actionable link. */
    data object DisabledByAdmin : ManufacturerLabel()
}

/**
 * Maps an [OuiResult] plus the current MDM state into a [ManufacturerLabel]. The MDM check is
 * cheap (single bundle lookup) but call sites should still memoize the result with `remember` to
 * avoid per-recomposition work.
 */
fun OuiResult.toManufacturerLabel(context: Context): ManufacturerLabel = when (status) {
    OuiStatus.RESOLVED -> vendor?.let { ManufacturerLabel.Resolved(it) }
        ?: ManufacturerLabel.Unknown

    OuiStatus.PRIVATE -> ManufacturerLabel.Private
    OuiStatus.LAA -> ManufacturerLabel.Randomized
    OuiStatus.UNKNOWN -> ManufacturerLabel.Unknown
    OuiStatus.SHARED_VENDOR_BLOCK -> ManufacturerLabel.SharedVendorBlock
    OuiStatus.OFFLINE, OuiStatus.TRANSIENT_FAILURE -> ManufacturerLabel.Offline
    OuiStatus.LOADING -> ManufacturerLabel.Loading
    OuiStatus.LOOKUP_DISABLED ->
        if (isOuiLookupLockedByMdm(context)) ManufacturerLabel.DisabledByAdmin
        else ManufacturerLabel.DisabledByUser
}

/**
 * Returns true when the `oui_lookup_enabled` restriction is present in the active MDM bundle
 * (i.e. an admin has set the value). Does not inspect the value itself, a locked pref is a
 * locked pref regardless of direction. Safe to call with no MDM active; returns false.
 */
fun isOuiLookupLockedByMdm(context: Context): Boolean {
    return try {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return false
        val bundle = rm.applicationRestrictions ?: return false
        bundle.containsKey(NetworkSurveyConstants.PROPERTY_OUI_LOOKUP_ENABLED)
    } catch (t: Throwable) {
        false
    }
}

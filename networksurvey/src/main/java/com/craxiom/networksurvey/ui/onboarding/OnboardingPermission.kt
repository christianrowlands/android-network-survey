package com.craxiom.networksurvey.ui.onboarding

import android.Manifest
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.craxiom.networksurvey.R

/**
 * One launch permission as the permission card explains it. The card lists exactly what the app
 * asks for on this Android version (see `AppPermissions.PERMISSIONS`).
 */
enum class OnboardingPermission(
    val manifestName: String,
    @StringRes val title: Int,
    @StringRes val reason: Int,
    @DrawableRes val icon: Int,
) {
    LOCATION(
        Manifest.permission.ACCESS_FINE_LOCATION,
        R.string.onboarding_permission_location,
        R.string.onboarding_permission_location_reason,
        R.drawable.ic_location_pin,
    ),
    PHONE(
        Manifest.permission.READ_PHONE_STATE,
        R.string.onboarding_permission_phone,
        R.string.onboarding_permission_phone_reason,
        R.drawable.ic_phone_state,
    ),
    NOTIFICATIONS(
        "android.permission.POST_NOTIFICATIONS",
        R.string.onboarding_permission_notifications,
        R.string.onboarding_permission_notifications_reason,
        R.drawable.ic_notifications,
    ),
    STORAGE(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        R.string.onboarding_permission_storage,
        R.string.onboarding_permission_storage_reason,
        R.drawable.ic_folder,
    );

    companion object {
        /** @return The card rows for [requested], in the order the card lists them. */
        fun forRequested(requested: Array<String>): List<OnboardingPermission> =
            entries.filter { it.manifestName in requested }
    }
}

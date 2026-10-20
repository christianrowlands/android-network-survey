package com.craxiom.networksurvey.util

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.craxiom.networksurvey.constants.NetworkSurveyConstants

/**
 * Decides how OpenCelliD uploads are signed, the one rule the Upload settings, the Community
 * databases page, its upload dialog, automatic uploads and the upload worker all follow:
 *
 * - With no valid personal API key, uploads are anonymous, using the shared token.
 * - With a valid personal key, uploads use it, unless the user turned on "Anonymous contribution".
 *   Saving a key turns that off, since entering a key means wanting it used.
 * - A build without the shared token can't upload anonymously, so a personal key is always used,
 *   and without one OpenCelliD can't upload at all.
 *
 * The "Anonymous contribution" choice only exists while a valid personal key is saved and the
 * shared token is in the build; otherwise there is nothing to choose.
 */
object OpenCelliDSigning {

    /** @return True if a personal OpenCelliD API key that looks valid is saved. */
    @JvmStatic
    fun hasValidPersonalKey(context: Context): Boolean = PreferenceUtils.isApiKeyValid(PreferenceUtils.getUserOcidApiKey(context))

    /** @return True if this build has the shared token that anonymous uploads use. */
    @JvmStatic
    fun hasSharedToken(context: Context): Boolean = PreferenceUtils.isApiKeyValid(PreferenceUtils.getSharedApiKey(context))

    /** @return True if the user can choose between their key and an anonymous upload. */
    @JvmStatic
    fun canChooseAnonymous(context: Context): Boolean = hasValidPersonalKey(context) && hasSharedToken(context)

    /** @return True if OpenCelliD uploads go out anonymously with the shared token. */
    @JvmStatic
    fun isAnonymous(context: Context): Boolean = isAnonymous(
        hasPersonalKey = hasValidPersonalKey(context),
        hasSharedToken = hasSharedToken(context),
        anonymousChosen = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, false),
    )

    /**
     * @return True if OpenCelliD uploads go out anonymously: always without a personal key, never
     * without the shared token, and otherwise as the user chose.
     */
    fun isAnonymous(hasPersonalKey: Boolean, hasSharedToken: Boolean, anonymousChosen: Boolean): Boolean = when {
        !hasPersonalKey -> true
        !hasSharedToken -> false
        else -> anonymousChosen
    }

    /**
     * Saves the user's anonymous choice for when they have a key. Ignored when there is nothing to
     * choose, so a choice made without a key can't override a key saved later.
     */
    @JvmStatic
    fun setAnonymousChosen(context: Context, anonymous: Boolean) {
        if (!canChooseAnonymous(context)) return
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, anonymous)
        }
    }

    /**
     * Saves the personal API key in encrypted storage, or removes it when [key] is blank. A key
     * that looks valid is used from now on, so the anonymous choice is turned off; removing the key
     * forgets the choice.
     */
    @JvmStatic
    fun savePersonalKey(context: Context, key: String) {
        val trimmed = key.trim()
        CredentialSecureStorage.storeOcidApiKey(context, trimmed.ifEmpty { null })
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            when {
                trimmed.isEmpty() -> remove(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD)
                PreferenceUtils.isApiKeyValid(trimmed) -> putBoolean(NetworkSurveyConstants.PROPERTY_ANONYMOUS_OPENCELLID_UPLOAD, false)
            }
        }
    }
}

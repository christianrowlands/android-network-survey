package com.craxiom.networksurvey.survey.setup

import com.craxiom.networksurvey.survey.mdm.SurveyMdmKeys

/**
 * Shared preference keys owned by the survey setup model. Removing or renaming one of these
 * silently resets a user's setting, so treat them like a stored format.
 */
object SurveyPreferenceKeys {
    /** JSON of the working setup (what the Survey screen shows and Start uses). */
    const val CURRENT_SETUP = "survey_setup_current"

    /** JSON array of the saved setups, built-in presets included. */
    const val SAVED_SETUPS = "survey_setups_saved"

    /** The id of the saved setup the working setup was picked from, if any. */
    const val SELECTED_SETUP_ID = "survey_setup_selected"

    /** The version of the stored survey setup format. Missing means the migration has not run. */
    const val SCHEMA_VERSION = "survey_setup_schema_version"

    /** The user's own "start the survey automatically" choice (app open and boot). */
    const val AUTO_START_SURVEY = SurveyMdmKeys.AUTO_START_SURVEY

    /** Set when the user stopped a managed survey, so app-open auto start leaves it stopped. */
    const val STOPPED_BY_USER = "survey_stopped_by_user"

    /** Destinations the user hid from the Survey screen list. */
    const val HIDDEN_DESTINATIONS = "survey_hidden_destinations"

    /** Set once by the upgrade migration so the Survey screen can explain where the setup went. */
    const val MIGRATION_NOTICE_PENDING = "survey_setup_migration_notice"

    /** Fingerprint of the last managed configuration the app acted on. */
    const val ORG_CONFIG_FINGERPRINT = "survey_org_config_fingerprint"

    /** Set when the organization changed its configuration while the user had the override on. */
    const val ORG_UPDATED_WHILE_OVERRIDDEN = "survey_org_updated_while_overridden"
}

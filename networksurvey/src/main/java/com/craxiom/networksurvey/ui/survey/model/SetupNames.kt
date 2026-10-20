package com.craxiom.networksurvey.ui.survey.model

import com.craxiom.networksurvey.survey.model.SurveySetup
import com.craxiom.networksurvey.survey.setup.SurveySetupStore

/** What is wrong with a name typed for a saved setup. */
enum class SetupNameProblem { BLANK, TOO_LONG, TAKEN }

/** The rules for saved setup names, shared by the Save sheet and the Rename dialog. */
object SetupNames {

    /**
     * @param exceptId The setup being renamed, which may keep its own name.
     * @return The first problem with [name], or null if it can be used as is. [SetupNameProblem.TAKEN]
     * means another saved setup has this name (trimmed, ignoring case).
     */
    fun problem(name: String, saved: List<SurveySetup>, exceptId: String? = null): SetupNameProblem? {
        val trimmed = name.trim()
        val key = SurveySetupStore.nameKey(trimmed)
        return when {
            trimmed.isEmpty() -> SetupNameProblem.BLANK
            trimmed.length > SurveySetup.MAX_NAME_LENGTH -> SetupNameProblem.TOO_LONG
            saved.any { it.id != exceptId && SurveySetupStore.nameKey(it.name) == key } -> SetupNameProblem.TAKEN
            else -> null
        }
    }
}

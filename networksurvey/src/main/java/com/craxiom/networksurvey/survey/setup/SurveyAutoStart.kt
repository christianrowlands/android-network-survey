package com.craxiom.networksurvey.survey.setup

import android.content.Context
import com.craxiom.networksurvey.survey.engine.SurveyTypeResolver

/**
 * Answers "should the survey start on its own?" for the places that run before the survey service
 * exists, such as the boot receiver.
 */
object SurveyAutoStart {

    /**
     * @return True if the survey should start on its own: the organization turned auto start on, or
     * the organization leaves it to the user and the user turned it on.
     */
    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        SurveyTypeResolver.currentPolicy(context).isAutoStartEnabled(SurveySetupStore(context).userAutoStart)
}

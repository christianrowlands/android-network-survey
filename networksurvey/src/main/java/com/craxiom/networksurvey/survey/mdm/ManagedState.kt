package com.craxiom.networksurvey.survey.mdm

/**
 * How the organization controls one survey setup item through MDM.
 */
enum class ManagedState {
    /** The person using the phone decides. This is the default when the key is absent. */
    USER,

    /** The organization turned the item on; the user cannot turn it off. */
    ON,

    /** The organization turned the item off; it is hidden from the Survey screen. */
    OFF;

    /** True when the organization decides this item. */
    val isManaged: Boolean
        get() = this != USER

    companion object {
        /**
         * Parses a raw restriction value. Strings `on`/`off`/`user` are the documented form. A
         * boolean (from an EMM that only offers raw key/value entry) maps to on or off. Anything
         * else, including null, means [USER], so a malformed value never locks anything.
         */
        @JvmStatic
        fun parse(value: Any?): ManagedState = when (value) {
            is String -> when (value.trim().lowercase()) {
                SurveyMdmKeys.VALUE_ON, "true" -> ON
                SurveyMdmKeys.VALUE_OFF, "false" -> OFF
                else -> USER
            }

            is Boolean -> if (value) ON else OFF
            else -> USER
        }
    }
}

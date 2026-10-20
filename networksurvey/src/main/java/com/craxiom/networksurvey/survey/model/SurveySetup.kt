package com.craxiom.networksurvey.survey.model

/**
 * One survey plan: what to record and where to send it. Picking a setup decides both at once.
 *
 * A setup is pure data. Whether it can actually run (a destination is configured, MDM allows it,
 * a radio is on) is decided by the survey orchestrator when it is started.
 *
 * @property id        A stable identifier. Saved setups get a generated id; the unsaved working
 *                     setup uses [CUSTOM_ID].
 * @property name      The name the user saved it under. Empty for the working setup.
 * @property recordTypes  What the survey records.
 * @property destinations Where the records go.
 * @property starred   True if the user starred it; starred setups are listed first.
 */
data class SurveySetup(
    val id: String,
    val name: String,
    val recordTypes: Set<RecordType>,
    val destinations: Set<Destination>,
    val starred: Boolean = false,
) {
    /** True if the setup has no record types or no destinations, so it cannot record anything. */
    val isEmpty: Boolean
        get() = recordTypes.isEmpty() || destinations.isEmpty()

    /** @return True if this setup records the same types to the same destinations as [other]. */
    fun hasSameContentAs(other: SurveySetup): Boolean =
        recordTypes == other.recordTypes && destinations == other.destinations

    companion object {
        /** The id of the working setup when it does not match a saved setup. */
        const val CUSTOM_ID = "custom"

        /** The longest name a saved setup can have, after trimming. */
        const val MAX_NAME_LENGTH = 40

        /** A setup with nothing selected. Used as the user layer on a managed device. */
        @JvmStatic
        fun empty(): SurveySetup = SurveySetup(CUSTOM_ID, "", emptySet(), emptySet())

        /** Convenience factory for Java callers. */
        @JvmStatic
        fun custom(recordTypes: Set<RecordType>, destinations: Set<Destination>): SurveySetup =
            SurveySetup(CUSTOM_ID, "", recordTypes, destinations)
    }
}

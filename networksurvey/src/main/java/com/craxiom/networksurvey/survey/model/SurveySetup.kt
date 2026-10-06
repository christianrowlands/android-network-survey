package com.craxiom.networksurvey.survey.model

/**
 * One survey plan: what to record and where to send it. Picking a setup decides both at once.
 *
 * A setup is pure data. Whether it can actually run (a destination is configured, MDM allows it,
 * a radio is on) is decided by the survey orchestrator when it is started.
 *
 * @property id        A stable identifier. Built-in presets use fixed ids from [SurveyPresets];
 *                     user-saved setups get a generated id; the unsaved working setup uses
 *                     [CUSTOM_ID].
 * @property name      The user-facing name for user-saved setups. Built-in presets resolve their
 *                     display name from a string resource by [id] instead, so this value is only a
 *                     fallback for them.
 * @property recordTypes  What the survey records.
 * @property destinations Where the records go.
 * @property builtIn   True for the presets that ship with the app.
 */
data class SurveySetup(
    val id: String,
    val name: String,
    val recordTypes: Set<RecordType>,
    val destinations: Set<Destination>,
    val builtIn: Boolean = false,
) {
    /** True if the setup has no record types or no destinations, so it cannot record anything. */
    val isEmpty: Boolean
        get() = recordTypes.isEmpty() || destinations.isEmpty()

    /** @return A copy of this setup with a different id and name, used when saving it under a name. */
    fun renamed(newId: String, newName: String): SurveySetup =
        copy(id = newId, name = newName, builtIn = false)

    /** @return True if this setup records the same types to the same destinations as [other]. */
    fun hasSameContentAs(other: SurveySetup): Boolean =
        recordTypes == other.recordTypes && destinations == other.destinations

    companion object {
        /** The id of the working setup when it does not match a saved or built-in setup. */
        const val CUSTOM_ID = "custom"

        /** A setup with nothing selected. Used as the user layer on a managed device. */
        @JvmStatic
        fun empty(): SurveySetup = SurveySetup(CUSTOM_ID, "", emptySet(), emptySet())

        /** Convenience factory for Java callers. */
        @JvmStatic
        fun custom(recordTypes: Set<RecordType>, destinations: Set<Destination>): SurveySetup =
            SurveySetup(CUSTOM_ID, "", recordTypes, destinations)
    }
}

package com.craxiom.networksurvey.survey.mdm

import com.craxiom.networksurvey.constants.NetworkSurveyConstants
import com.craxiom.networksurvey.survey.model.Destination
import com.craxiom.networksurvey.survey.model.RecordType
import com.craxiom.networksurvey.survey.model.SurveySetup

/**
 * Reads the organization's survey policy out of a [RestrictionsSnapshot].
 *
 * Two kinds of control are kept apart:
 * - **Setup locks** (`survey_*`, `destination_*`, `auto_start_survey`): settings the organization
 *   chose for this phone. The user's "Override organization settings" switch lifts them.
 * - **Data policy gates** (`allow_external_data_upload`, `allow_ns_analytics`): rules about where
 *   data may go at all. They are never lifted by the override and they beat any `destination_*`
 *   key.
 */
object SurveyPolicy {

    /**
     * @param snapshot   The managed configuration.
     * @param overrideOn The user's "Override organization settings" switch.
     */
    @JvmStatic
    fun evaluate(snapshot: RestrictionsSnapshot, overrideOn: Boolean): SurveyPolicyState {
        val blocked = blockedDestinations(snapshot)

        if (overrideOn) {
            return SurveyPolicyState(
                isManaged = snapshot.isManaged,
                overrideOn = true,
                typeLocks = RecordType.entries.associateWith { ManagedState.USER },
                destinationLocks = Destination.entries.associateWith { ManagedState.USER },
                blockedDestinations = blocked,
                autoStart = ManagedState.USER,
                seedSetup = null,
                hasOrganizationSetup = hasOrganizationSetup(snapshot),
                fingerprint = snapshot.fingerprint(),
            )
        }

        val typeLocks = RecordType.entries.associateWith { snapshot.managedState(SurveyMdmKeys.keyFor(it)) }
        val v2DestinationLocks = Destination.entries.associateWith { snapshot.managedState(SurveyMdmKeys.keyFor(it)) }
        val v2AutoStart = snapshot.managedState(SurveyMdmKeys.AUTO_START_SURVEY)

        val legacy = LegacyMdmMapper.map(
            snapshot,
            typesManagedByV2 = typeLocks.values.any { it.isManaged },
            destinationsManagedByV2 = v2DestinationLocks.values.any { it.isManaged },
            autoStartManagedByV2 = v2AutoStart.isManaged,
        )

        val destinationLocks = v2DestinationLocks.mapValues { (destination, state) ->
            if (state == ManagedState.USER) legacy.destinationLocks[destination] ?: ManagedState.USER else state
        }
        val autoStart = if (v2AutoStart.isManaged) v2AutoStart else legacy.autoStart

        return SurveyPolicyState(
            isManaged = snapshot.isManaged,
            overrideOn = false,
            typeLocks = typeLocks,
            destinationLocks = destinationLocks,
            blockedDestinations = blocked,
            autoStart = autoStart,
            seedSetup = legacy.seedSetup,
            hasOrganizationSetup = hasOrganizationSetup(snapshot),
            fingerprint = snapshot.fingerprint(),
        )
    }

    /**
     * @return The destinations the data policy gates forbid. On a managed phone, community uploads
     * are denied unless the organization explicitly allows them; NS Analytics is allowed unless the
     * organization explicitly blocks it.
     */
    @JvmStatic
    fun blockedDestinations(snapshot: RestrictionsSnapshot): Set<Destination> = buildSet {
        if (!isExternalDataUploadAllowed(snapshot)) add(Destination.COMMUNITY)
        if (snapshot.getBoolean(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_NS_ANALYTICS) == false) {
            add(Destination.NS_ANALYTICS)
        }
    }

    /**
     * @return True if data may be sent to the public community databases. Any managed phone
     * (one with any managed configuration at all) is denied unless the organization sets
     * `allow_external_data_upload` to true.
     */
    @JvmStatic
    fun isExternalDataUploadAllowed(snapshot: RestrictionsSnapshot): Boolean {
        if (!snapshot.isManaged) return true
        return snapshot.getBoolean(NetworkSurveyConstants.MDM_PROPERTY_ALLOW_EXTERNAL_DATA_UPLOAD) == true
    }

    /**
     * @return True if the organization sets any part of the survey setup (including through the
     * deprecated keys). The override switch is only offered when this is true, because a phone
     * whose configuration only holds defaults has nothing to override.
     */
    @JvmStatic
    fun hasOrganizationSetup(snapshot: RestrictionsSnapshot): Boolean {
        val v2 = SurveyMdmKeys.ALL_SETUP_KEYS.any { snapshot.managedState(it).isManaged }
        if (v2) return true
        val legacy = LegacyMdmMapper.map(snapshot, false, false, false)
        return !legacy.isEmpty
    }
}

/**
 * The organization's survey policy at one point in time.
 *
 * @property isManaged            True if the phone has any managed configuration.
 * @property overrideOn           True if the user turned on "Override organization settings".
 * @property typeLocks            The lock state of every record type ([ManagedState.USER] when the
 *                                override is on).
 * @property destinationLocks     The lock state of every destination ([ManagedState.USER] when the
 *                                override is on).
 * @property blockedDestinations  Destinations the data policy gates forbid. Never lifted by the
 *                                override.
 * @property autoStart            The organization's auto start choice.
 * @property seedSetup            The organization default setup from the deprecated keys, used to
 *                                seed the user's setup on a managed phone. Null when there is none.
 * @property hasOrganizationSetup True if the organization sets any part of the survey setup,
 *                                regardless of the override.
 * @property fingerprint          A fingerprint of the whole managed configuration.
 */
data class SurveyPolicyState(
    val isManaged: Boolean,
    val overrideOn: Boolean,
    val typeLocks: Map<RecordType, ManagedState>,
    val destinationLocks: Map<Destination, ManagedState>,
    val blockedDestinations: Set<Destination>,
    val autoStart: ManagedState,
    val seedSetup: OrganizationSeed?,
    val hasOrganizationSetup: Boolean,
    val fingerprint: String,
) {
    /** True if any record type or destination is locked by the organization right now. */
    val hasSetupLocks: Boolean
        get() = typeLocks.values.any { it.isManaged } || destinationLocks.values.any { it.isManaged }

    /**
     * True if the organization shapes the survey setup right now (locks or a seed), so
     * [managedSeedSetup] has something to seed. False while the override is on.
     */
    val shapesSetup: Boolean
        get() = hasSetupLocks || seedSetup != null

    /** @return The lock state of [type]. */
    fun lockFor(type: RecordType): ManagedState = typeLocks[type] ?: ManagedState.USER

    /** @return The lock state of [destination]. */
    fun lockFor(destination: Destination): ManagedState = destinationLocks[destination] ?: ManagedState.USER

    /** True if the organization turned Phone state off, which beats the auto include rule. */
    val phoneStateForcedOff: Boolean
        get() = lockFor(RecordType.PHONE_STATE) == ManagedState.OFF

    /**
     * Applies the organization's locks and data policy gates to the user's setup: items the
     * organization turned on are added, items it turned off or that a policy gate blocks are
     * removed.
     */
    fun effectiveSetup(userSetup: SurveySetup): SurveySetup {
        val types = userSetup.recordTypes.toMutableSet()
        for ((type, state) in typeLocks) {
            when (state) {
                ManagedState.ON -> types += type
                ManagedState.OFF -> types -= type
                ManagedState.USER -> Unit
            }
        }

        val destinations = userSetup.destinations.toMutableSet()
        for ((destination, state) in destinationLocks) {
            when (state) {
                ManagedState.ON -> destinations += destination
                ManagedState.OFF -> destinations -= destination
                ManagedState.USER -> Unit
            }
        }
        destinations -= blockedDestinations

        return userSetup.copy(recordTypes = types, destinations = destinations)
    }

    /** @return True if [destination] may be used at all, considering locks and policy gates. */
    fun isDestinationAvailable(destination: Destination): Boolean =
        destination !in blockedDestinations && lockFor(destination) != ManagedState.OFF

    /** @return True if [type] may be recorded at all, considering locks. */
    fun isRecordTypeAvailable(type: RecordType): Boolean = lockFor(type) != ManagedState.OFF

    /** @return The effective auto start choice given the user's own [userAutoStart] preference. */
    fun isAutoStartEnabled(userAutoStart: Boolean): Boolean = when (autoStart) {
        ManagedState.ON -> true
        ManagedState.OFF -> false
        ManagedState.USER -> userAutoStart
    }

    /**
     * The setup to seed the user layer with when a phone becomes managed (and on Restore), so the
     * organization's stream does not inherit the user's earlier picks. It works per dimension:
     * - Record types: the deprecated keys' seed if they decide the record types (even as none, for
     *   a heartbeat only MDM broker), otherwise empty if the organization locks any record type,
     *   otherwise the user's own.
     * - Destinations: the deprecated keys' seed if they name any, otherwise empty if the
     *   organization locks any destination, otherwise the user's own.
     *
     * @param current The user's setup before seeding.
     * @return The seeded setup, or null when the organization does not shape the setup at all.
     */
    fun managedSeedSetup(current: SurveySetup): SurveySetup? {
        val typesManaged = typeLocks.values.any { it.isManaged }
        val destinationsManaged = destinationLocks.values.any { it.isManaged }
        if (!typesManaged && !destinationsManaged && seedSetup == null) return null

        val types = when {
            seedSetup?.namesRecordTypes == true -> seedSetup.recordTypes
            typesManaged -> emptySet()
            else -> current.recordTypes
        }
        val destinations = when {
            !seedSetup?.destinations.isNullOrEmpty() -> seedSetup!!.destinations
            destinationsManaged -> emptySet()
            else -> current.destinations
        }
        return SurveySetup.custom(types, destinations)
    }

    companion object {
        /** The policy of an unmanaged phone. */
        @JvmField
        val UNMANAGED: SurveyPolicyState = SurveyPolicy.evaluate(RestrictionsSnapshot.EMPTY, overrideOn = false)
    }
}

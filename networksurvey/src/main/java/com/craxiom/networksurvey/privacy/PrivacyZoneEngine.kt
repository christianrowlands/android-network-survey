package com.craxiom.networksurvey.privacy

/**
 * Whether the survey is paused because the phone is in a privacy zone.
 *
 * @property decided True once a usable fix has placed the phone this survey session. Until then
 *                   records are kept and nothing may claim the phone is in a zone.
 * @property zone    The zone the phone is in, or null when outside every zone, undecided, or zones
 *                   are turned off.
 */
data class PrivacyZoneState(
    val decided: Boolean,
    val zone: PrivacyZone?,
) {
    /** True while records must be held back from every sink because the phone is in a zone. */
    val pausedForZone: Boolean get() = zone != null

    /** The name of the zone the phone is in, or null when not paused. May be blank. */
    val zoneName: String? get() = zone?.name

    companion object {
        /** No usable fix yet this session: records are kept. */
        @JvmField
        val UNDECIDED = PrivacyZoneState(decided = false, zone = null)
    }
}

/**
 * The sticky privacy zone decision for one phone, with no Android types and no locking (the caller,
 * [PrivacyZoneGate], serializes every call).
 *
 * - A usable fix ([PrivacyZoneRules.isUsableFix]) decides: it becomes the deciding fix.
 * - A fix that is not usable (coarse, stale, no accuracy) or no fix at all makes no new decision;
 *   the last one holds until the next usable fix, with no time limit.
 * - With no deciding fix this session, records are kept.
 *
 * The state is always derived from the deciding fix and the current zones, so adding, editing or
 * removing a zone (or the organization turning zones off) takes effect at once, even without a new
 * fix.
 */
class PrivacyZoneEngine {
    private var zones: List<PrivacyZone> = emptyList()
    private var allowed = true

    /** The most recent fix fed in, usable or not, so a new session can start from it. */
    private var latestFix: PrivacyZoneFix? = null

    /** The last usable fix this session; it decides until the next usable fix. */
    private var decidingFix: PrivacyZoneFix? = null

    /** @return The current decision. */
    fun state(): PrivacyZoneState {
        val fix = decidingFix ?: return PrivacyZoneState.UNDECIDED
        val zone = if (allowed) PrivacyZoneRules.zoneContaining(zones, fix) else null
        return PrivacyZoneState(decided = true, zone = zone)
    }

    /** @return The zones in force: none when the organization turned zones off. */
    fun activeZones(): List<PrivacyZone> = if (allowed) zones else emptyList()

    /**
     * Feeds a new fix from the phone's location provider. A null fix (the provider turned off) only
     * forgets the latest fix; the decision holds.
     */
    fun onFix(fix: PrivacyZoneFix?, nowElapsedRealtimeMs: Long): PrivacyZoneState {
        latestFix = fix
        if (PrivacyZoneRules.isUsableFix(fix, nowElapsedRealtimeMs)) decidingFix = fix
        return state()
    }

    /** Replaces the zones (the store changed). */
    fun setZones(zones: List<PrivacyZone>): PrivacyZoneState {
        this.zones = zones.toList()
        return state()
    }

    /** Sets whether the organization allows privacy zones. When false, zones are ignored. */
    fun setAllowed(allowed: Boolean): PrivacyZoneState {
        this.allowed = allowed
        return state()
    }

    /**
     * Starts a new survey session: forgets the last session's decision, so an old "inside" can't
     * pause a survey started far away without a fix. The latest fix decides again if it is still
     * usable now (at most a minute old).
     */
    fun startSession(nowElapsedRealtimeMs: Long): PrivacyZoneState {
        decidingFix = latestFix?.takeIf { PrivacyZoneRules.isUsableFix(it, nowElapsedRealtimeMs) }
        return state()
    }

    /** Ends the survey session: no decision until the next usable fix. */
    fun endSession(): PrivacyZoneState {
        decidingFix = null
        return state()
    }
}

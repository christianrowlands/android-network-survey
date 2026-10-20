package com.craxiom.networksurvey.survey.mdm

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import java.security.MessageDigest

/**
 * An immutable copy of the app's managed configuration (MDM restrictions) at one point in time.
 *
 * Holding the values in a plain map keeps the policy logic free of Android types so it can be
 * unit tested on the JVM, and lets one snapshot be read consistently across a whole decision.
 */
class RestrictionsSnapshot(private val values: Map<String, Any?>) {

    /**
     * True when the organization has pushed any managed configuration at all. This is the
     * definition of "managed" used by the data policy gates: a phone configured with only MQTT
     * broker keys is managed too.
     */
    val isManaged: Boolean
        get() = values.isNotEmpty()

    /** @return True if [key] is present in the managed configuration. */
    fun contains(key: String): Boolean = values.containsKey(key)

    /** @return The raw value for [key], or null if it is absent. */
    fun get(key: String): Any? = values[key]

    /** @return The boolean value for [key], or null if it is absent or not a boolean. */
    fun getBoolean(key: String): Boolean? = values[key] as? Boolean

    /** @return The string value for [key], or null if it is absent or not a string. */
    fun getString(key: String): String? = values[key] as? String

    /** @return The integer value for [key], or null if it is absent or not an integer. */
    fun getInt(key: String): Int? = values[key] as? Int

    /** @return Every key the organization set, for listing them on the Organization settings page. */
    fun keys(): Set<String> = values.keys

    /** @return The managed state for a three-way survey setup [key]. */
    fun managedState(key: String): ManagedState = ManagedState.parse(values[key])

    /**
     * A stable fingerprint of the restriction values, used to tell a real configuration change from
     * an identical re-push (some EMMs re-push the same configuration routinely).
     */
    fun fingerprint(): String {
        val canonical = values.toSortedMap().entries.joinToString("\n") { (key, value) ->
            "$key=${canonicalValue(value)}"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun canonicalValue(value: Any?): String = when (value) {
        is Array<*> -> value.joinToString(",", "[", "]") { canonicalValue(it) }
        is Bundle -> value.keySet().sorted().joinToString(",", "{", "}") {
            @Suppress("DEPRECATION")
            "$it=${canonicalValue(value.get(it))}"
        }

        else -> value.toString()
    }

    companion object {
        /** A snapshot of an unmanaged phone. */
        @JvmField
        val EMPTY = RestrictionsSnapshot(emptyMap())

        /** Copies [bundle] into a snapshot. A null bundle is treated as unmanaged. */
        @JvmStatic
        fun fromBundle(bundle: Bundle?): RestrictionsSnapshot {
            if (bundle == null || bundle.isEmpty) return EMPTY
            val map = HashMap<String, Any?>()
            for (key in bundle.keySet()) {
                @Suppress("DEPRECATION")
                map[key] = bundle.get(key)
            }
            return RestrictionsSnapshot(map)
        }
    }
}

/**
 * Where restriction snapshots come from. Production code uses [AndroidRestrictionsSource]; tests
 * supply a fixed snapshot.
 */
fun interface RestrictionsSource {
    fun snapshot(): RestrictionsSnapshot
}

/**
 * Reads the managed configuration from the platform [RestrictionsManager].
 */
class AndroidRestrictionsSource(context: Context) : RestrictionsSource {
    private val appContext = context.applicationContext

    override fun snapshot(): RestrictionsSnapshot {
        val restrictionsManager =
            appContext.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
                ?: return RestrictionsSnapshot.EMPTY
        return RestrictionsSnapshot.fromBundle(restrictionsManager.applicationRestrictions)
    }
}

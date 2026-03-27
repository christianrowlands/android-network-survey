package com.craxiom.networksurvey.model

/**
 * Represents a PLMN (Public Land Mobile Network) identifier consisting of MCC and MNC.
 * The [mncString] field preserves leading zeros when available (e.g., "01" instead of 1).
 * Equality and hashing are based only on [mcc] and [mnc] (the integer fields), so that two
 * Plmn objects representing the same network are considered equal regardless of whether
 * [mncString] is populated.
 */
data class Plmn @JvmOverloads constructor(
    val mcc: Int,
    val mnc: Int,
    val mncString: String? = null
) {

    /**
     * Returns true if either mcc or mnc is not 0, false otherwise.
     */
    fun isSet(): Boolean {
        return mcc != 0 || mnc != 0
    }

    /**
     * Returns the PLMN as a formatted string with leading zeros preserved when available.
     * For example, "310-01" instead of "310-1".
     */
    override fun toString(): String {
        return if (mcc == 0 && mnc == 0) {
            "Not Set"
        } else if (mncString != null) {
            "$mcc-$mncString"
        } else {
            "$mcc-$mnc"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Plmn) return false
        return mcc == other.mcc && mnc == other.mnc
    }

    override fun hashCode(): Int {
        var result = mcc
        result = 31 * result + mnc
        return result
    }
}

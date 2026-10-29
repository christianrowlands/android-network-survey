package com.craxiom.networksurvey.ui.cellular.model

/**
 * Builds the ids used to identify a tower. This is NOT the CGI because the TAC is included for LTE
 * and NR, and the CGI does not include the TAC.
 *
 * The fields are delimited so that a two digit MNC followed by an area cannot produce the same
 * string as a three digit MNC followed by a shorter area.
 */
object TowerIdentity {
    private const val DELIMITER = "-"

    /** The id with the MNC as reported, which survey points store as their cell id. */
    @JvmStatic
    fun towerId(mcc: String?, mnc: String?, area: Int, cid: Long): String =
        join(mcc, mnc ?: "0", area, cid)

    /**
     * The id the map matches towers by. The MNC is keyed by its integer value, because the phone
     * and the tower database can spell the same network at different widths (the phone's
     * broadcast "440-10" against a tower stored as "440-010", or an unregistered "055" against
     * OpenCelliD's integer 55). No MCC has two networks with the same integer MNC, so this never
     * merges two networks.
     */
    @JvmStatic
    fun mapId(mcc: String?, mnc: String?, area: Int, cid: Long): String =
        join(mcc, mnc?.toIntOrNull()?.toString() ?: mnc ?: "0", area, cid)

    private fun join(mcc: String?, mnc: String, area: Int, cid: Long): String =
        listOf(mcc ?: "0", mnc, area.toString(), cid.toString()).joinToString(DELIMITER)
}

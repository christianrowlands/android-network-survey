package com.craxiom.networksurvey.ui.cellular.model

/**
 * Builds the id used to identify a tower on the map. This is NOT the CGI because the TAC is
 * included for LTE and NR, and the CGI does not include the TAC.
 *
 * The fields are delimited so that a two digit MNC followed by an area cannot produce the same
 * string as a three digit MNC followed by a shorter area.
 */
object TowerIdentity {
    private const val DELIMITER = "-"

    @JvmStatic
    fun towerId(mcc: String?, mnc: String?, area: Int, cid: Long): String =
        listOf(mcc ?: "0", mnc ?: "0", area.toString(), cid.toString()).joinToString(DELIMITER)
}

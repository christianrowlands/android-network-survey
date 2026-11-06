package com.craxiom.networksurvey.scout.link

/**
 * Turns Android GATT status codes into [ScoutLinkFailure]s, with the stale-bond rules: when the
 * phone holds a bond but the Scout no longer does, Android never re-pairs on its own (it does not
 * retry after Insufficient Authorization, 0x08), so the link fails and the user has to remove the
 * Scout in Android's Bluetooth settings.
 *
 * The values are kept here because several are hidden or newer than the minimum SDK.
 */
object GattStatus {
    /** ATT Insufficient Authentication: the link is not encrypted. */
    const val INSUFFICIENT_AUTHENTICATION = 0x05

    /** ATT Insufficient Authorization: encrypted, but the board holds no bond for this phone. */
    const val INSUFFICIENT_AUTHORIZATION = 0x08

    /** ATT Insufficient Encryption. */
    const val INSUFFICIENT_ENCRYPTION = 0x0F

    /** Android's GATT_AUTH_FAIL (hidden), reported when encryption with the stored keys fails. */
    const val AUTH_FAIL = 0x89

    /** HCI Authentication Failure, as a disconnect reason. */
    const val HCI_AUTHENTICATION_FAILURE = 0x05

    /** HCI PIN or Key Missing: the peer has no key for this phone's bond. */
    const val HCI_PIN_OR_KEY_MISSING = 0x06

    /** HCI Connection Timeout: the supervision timeout ran out (out of range). */
    const val HCI_CONNECTION_TIMEOUT = 0x08

    /** HCI Connection Terminated due to MIC Failure: encryption with a stale key. */
    const val HCI_MIC_FAILURE = 0x3D

    private val AUTH_STATUSES = setOf(INSUFFICIENT_AUTHENTICATION, INSUFFICIENT_AUTHORIZATION, INSUFFICIENT_ENCRYPTION, AUTH_FAIL)
    private val STALE_KEY_DISCONNECTS = setOf(HCI_AUTHENTICATION_FAILURE, HCI_PIN_OR_KEY_MISSING, HCI_MIC_FAILURE)

    /**
     * @return The failure for a GATT operation that ended with [status]. A security error means a
     * stale bond when the phone is bonded ([phoneBonded]), else that pairing did not happen.
     */
    @JvmStatic
    fun operationFailure(status: Int, phoneBonded: Boolean): ScoutLinkFailure = when {
        status in AUTH_STATUSES && phoneBonded -> ScoutLinkFailure.BOND_MISSING
        status in AUTH_STATUSES -> ScoutLinkFailure.PAIRING_REJECTED
        else -> ScoutLinkFailure.GATT_ERROR
    }

    /**
     * @return The failure for a disconnect with HCI reason [status]. A key failure on a bonded
     * phone points at a stale bond, but one can be stray, so the link controller decides; anything
     * else counts as the link dropping.
     */
    @JvmStatic
    fun disconnectFailure(status: Int, phoneBonded: Boolean): ScoutLinkFailure =
        if (phoneBonded && status in STALE_KEY_DISCONNECTS) ScoutLinkFailure.KEY_REJECTED else ScoutLinkFailure.LINK_LOST
}

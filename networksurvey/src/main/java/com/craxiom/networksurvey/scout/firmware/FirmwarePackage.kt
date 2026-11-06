package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.BoardInfo
import java.security.MessageDigest

/** Something the user must accept explicitly before an install (protocol.md section 10). */
sealed interface FirmwareConsent {
    /** The same version as the board runs ([FirmwareComparison.SAME]) or an older one. */
    data class NotNewer(val comparison: FirmwareComparison) : FirmwareConsent

    /** A newer release was verified on this phone before: this manifest may be stale. */
    data class StaleRelease(val newer: String) : FirmwareConsent
}

/**
 * A verified release image, ready to send to a Scout. A port of the iOS app's `FirmwarePackage`.
 *
 * @property version The firmware version the signed manifest names.
 * @property boardModel The `board_model` the signed manifest lists this image for.
 * @property newerReleaseSeen A release newer than this one was verified before.
 */
class FirmwarePackage internal constructor(
    val image: ByteArray,
    val sha256: ByteArray,
    val version: String,
    val boardModel: String,
    val header: EspAppImage,
    val newerReleaseSeen: String? = null,
) {
    /**
     * Refuses a board this image is not for. Checked on every link, so a package kept for a resume
     * can't reach another board.
     *
     * @throws FirmwareUpdateError.WrongChip When the chips differ.
     * @throws FirmwareUpdateError.WrongBoard When the board models differ.
     */
    fun checkCompatible(info: BoardInfo) {
        if (header.chip != info.chip) {
            throw FirmwareUpdateError.WrongChip(
                board = FirmwareBoardNames.displayChip(info.chip) ?: info.chip.name,
                image = FirmwareBoardNames.displayChip(header.chip) ?: "chip ID 0x%04X".format(header.chipId),
            )
        }
        if (FirmwareBoardNames.manifestModel(info.boardModel) != boardModel) {
            throw FirmwareUpdateError.WrongBoard(board = info.boardModel.name, image = boardModel)
        }
    }

    /**
     * @return What the user must explicitly accept before this package goes to the board described
     * by [info]: a version that is not newer, and a release older than one seen before.
     */
    fun requiredConsents(info: BoardInfo): List<FirmwareConsent> = buildList {
        val comparison = FirmwareComparison.of(version, info.firmwareVersion)
        if (comparison == FirmwareComparison.SAME || comparison == FirmwareComparison.OLDER) add(FirmwareConsent.NotNewer(comparison))
        newerReleaseSeen?.let { add(FirmwareConsent.StaleRelease(it)) }
    }

    companion object {
        private const val SHA_256 = "SHA-256"

        /** @return The SHA-256 of [data]. */
        @JvmStatic
        fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance(SHA_256).digest(data)
    }
}

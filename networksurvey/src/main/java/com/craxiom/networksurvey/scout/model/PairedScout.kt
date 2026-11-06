package com.craxiom.networksurvey.scout.model

import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.scout.link.ScoutAdvertisement

/**
 * A Scout this phone paired with, as the app stores it: enough to reconnect (the Android
 * address) and to describe it on screen before the link is up. Plain fields so the store can save
 * it as JSON.
 */
data class PairedScout(
    /** The [ScoutId] value. */
    val id: String,
    /** The Android Bluetooth address to connect to. */
    val address: String,
    /** "ABCD" from the advertised name "NS-ABCD". */
    val shortId: String,
    /** The `BoardModel` number from Info. */
    val boardModel: Int,
    /** `BoardInfo.model_name`, required for third-party boards, may be empty otherwise. */
    val modelName: String,
    val firmwareVersion: String,
    val protocolMajor: Int,
    /** The `Band` numbers the board receives on. */
    val bands: List<Int>,
    /** When this phone paired with the Scout, Unix epoch milliseconds. */
    val pairedAtMs: Long,
) {
    /** The Scout's ID. */
    val scoutId: ScoutId get() = ScoutId(id)

    /** The name the Scout advertises and that every screen shows, "NS-ABCD". */
    val name: String get() = ScoutAdvertisement.nameForShortId(shortId)

    /** The model as people know it ("ESP32-C5-DevKitC-1"), shown on screen. */
    val modelDisplayName: String get() = ScoutModelNames.displayName(boardModel, modelName)

    /** @return This Scout with the details from a fresh [info] read. */
    fun updatedWith(info: BoardInfo): PairedScout = copy(
        shortId = info.shortId.ifEmpty { shortId },
        boardModel = info.boardModelValue,
        modelName = info.modelName,
        firmwareVersion = info.firmwareVersion,
        protocolMajor = info.protocolMajor,
        bands = info.bandsValueList,
    )

    companion object {
        /** @return The paired Scout for a board at [address] whose [info] was just read. */
        @JvmStatic
        fun from(address: String, info: BoardInfo, nowMs: Long): PairedScout {
            val id = ScoutId.fromBtAddress(info.btAddress.toByteArray()) ?: ScoutId.fromAddress(address)
            return PairedScout(
                id = (id ?: ScoutId(ScoutId.PREFIX + ScoutId.shortIdFromAddress(address))).value,
                address = address,
                shortId = info.shortId.ifEmpty { ScoutId.shortIdFromAddress(address) },
                boardModel = info.boardModelValue,
                modelName = info.modelName,
                firmwareVersion = info.firmwareVersion,
                protocolMajor = info.protocolMajor,
                bands = info.bandsValueList,
                pairedAtMs = nowMs,
            )
        }
    }
}

package com.craxiom.networksurvey.scout.link

import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.scout.model.ScoutId

/**
 * The manufacturer specific data of a Scout advertisement (protocol.md section 1), as far as the
 * pairing sheet uses it. The company ID is never checked: Scouts are found by service UUID, so a
 * later change of company ID keeps working.
 */
data class ScoutAdvertisement(
    /** The board's protocol major version. */
    val protocolMajor: Int,
    /** No bonds yet, or an explicit pairing window is open. */
    val pairable: Boolean,
    /** The board holds at least one bond (with this phone or another). */
    val hasBond: Boolean,
    val model: BoardModel,
) {
    companion object {
        /** The payload after the company ID: protocol version, flags and board model. */
        const val PAYLOAD_BYTES = 3

        private const val PROTOCOL_OFFSET = 0
        private const val FLAGS_OFFSET = 1
        private const val MODEL_OFFSET = 2
        private const val FLAG_PAIRABLE = 0x01
        private const val FLAG_HAS_BOND = 0x02
        private const val BYTE_MASK = 0xFF
        private const val MAX_SHORT_ID_LENGTH = 7
        private const val HEX_RADIX = 16

        /**
         * Parses the manufacturer data payload as Android reports it (without the company ID).
         * Extra trailing bytes are ignored, the flag bits the app doesn't use too.
         *
         * @return The advertisement, or null when the payload is too short.
         */
        @JvmStatic
        fun parse(payload: ByteArray?): ScoutAdvertisement? {
            if (payload == null || payload.size < PAYLOAD_BYTES) return null
            val flags = payload[FLAGS_OFFSET].toInt() and BYTE_MASK
            return ScoutAdvertisement(
                protocolMajor = payload[PROTOCOL_OFFSET].toInt() and BYTE_MASK,
                pairable = flags and FLAG_PAIRABLE != 0,
                hasBond = flags and FLAG_HAS_BOND != 0,
                model = BoardModel.forNumber(payload[MODEL_OFFSET].toInt() and BYTE_MASK)
                    ?: BoardModel.BOARD_MODEL_UNSPECIFIED,
            )
        }

        /** @return "ABCD" from an advertised name "NS-ABCD" (uppercased), or null for any other name. */
        @JvmStatic
        fun shortIdFromName(name: String?): String? {
            if (name == null || !name.startsWith(ScoutId.PREFIX)) return null
            val id = name.substring(ScoutId.PREFIX.length)
            if (id.isEmpty() || id.length > MAX_SHORT_ID_LENGTH) return null
            if (!id.all { Character.digit(it, HEX_RADIX) >= 0 }) return null
            return id.uppercase()
        }

        /** @return The advertised name of a Scout with [shortId], "NS-" plus the short ID. */
        @JvmStatic
        fun nameForShortId(shortId: String): String = ScoutId.PREFIX + shortId
    }
}

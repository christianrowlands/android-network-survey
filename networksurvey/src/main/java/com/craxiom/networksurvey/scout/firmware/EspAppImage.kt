package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.board.v1.Chip
import java.nio.charset.StandardCharsets

/**
 * What the app reads from an ESP-IDF app image before sending it: the image header's chip ID and
 * the app description's version (esp_app_format.h, esp_app_desc.h). Used to refuse an image for
 * another chip early and to bind the image to the manifest's version. A port of the iOS app's
 * `EspAppImage`.
 */
class EspAppImage private constructor(
    val chipId: Int,
    val version: String?,
) {
    /** The board chip this image runs on, null for chips no Scout uses. */
    val chip: Chip?
        get() = when (chipId) {
            CHIP_ID_ESP32_C5 -> Chip.CHIP_ESP32_C5
            CHIP_ID_ESP32_C6 -> Chip.CHIP_ESP32_C6
            CHIP_ID_ESP32_S3 -> Chip.CHIP_ESP32_S3
            CHIP_ID_ESP32_C3 -> Chip.CHIP_ESP32_C3
            else -> null
        }

    companion object {
        /** The first byte of every ESP app image. */
        const val HEADER_MAGIC: Int = 0xE9
        private const val DESCRIPTION_MAGIC = 0xABCD5432L

        private const val HEADER_BYTES = 24
        private const val CHIP_ID_OFFSET = 12

        /** esp_app_desc_t follows the 24-byte image header and the first 8-byte segment header. */
        private const val DESCRIPTION_OFFSET = 32
        private const val VERSION_START = 48
        private const val VERSION_END = 80

        private const val CHIP_ID_ESP32_C5 = 0x0017
        private const val CHIP_ID_ESP32_C6 = 0x000D
        private const val CHIP_ID_ESP32_S3 = 0x0009
        private const val CHIP_ID_ESP32_C3 = 0x0005
        private const val BYTE_MASK = 0xFF
        private const val BITS_PER_BYTE = 8

        /** @return The image facts, or null when [data] does not start with an app image header. */
        @JvmStatic
        fun parse(data: ByteArray): EspAppImage? {
            if (data.size < HEADER_BYTES || (data[0].toInt() and BYTE_MASK) != HEADER_MAGIC) return null
            val chipId = uint(data, CHIP_ID_OFFSET, 2).toInt()
            if (data.size < VERSION_END || uint(data, DESCRIPTION_OFFSET, 4) != DESCRIPTION_MAGIC) return EspAppImage(chipId, null)
            return EspAppImage(chipId, cString(data, VERSION_START, VERSION_END))
        }

        private fun uint(data: ByteArray, offset: Int, bytes: Int): Long =
            (0 until bytes).fold(0L) { value, i -> value or ((data[offset + i].toLong() and BYTE_MASK.toLong()) shl (BITS_PER_BYTE * i)) }

        private fun cString(data: ByteArray, start: Int, end: Int): String? {
            var stop = start
            while (stop < end && data[stop].toInt() != 0) stop++
            return String(data, start, stop - start, StandardCharsets.UTF_8).takeIf { it.isNotEmpty() }
        }
    }
}

/** The manifest and display names of the board models and chips Scouts use. */
object FirmwareBoardNames {
    private const val MODEL_PREFIX = "BOARD_MODEL_"
    private const val CHIP_PREFIX = "CHIP_"

    /** @return The `board_model` name the release manifest uses, null for boards without official images. */
    @JvmStatic
    fun manifestModel(model: BoardModel): String? = when (model) {
        BoardModel.BOARD_MODEL_UNSPECIFIED,
        BoardModel.BOARD_MODEL_OTHER,
        BoardModel.UNRECOGNIZED -> null

        else -> model.name.removePrefix(MODEL_PREFIX)
    }

    /** @return The `chip` name the release manifest uses, null for chips no Scout uses. */
    @JvmStatic
    fun manifestChip(chip: Chip): String? = when (chip) {
        Chip.CHIP_UNSPECIFIED, Chip.CHIP_OTHER, Chip.UNRECOGNIZED -> null
        else -> chip.name.removePrefix(CHIP_PREFIX)
    }

    /** @return The chip as people write it ("ESP32-C5"), or null for an unknown chip. */
    @JvmStatic
    fun displayChip(chip: Chip?): String? = chip?.let(::manifestChip)?.replace('_', '-')
}

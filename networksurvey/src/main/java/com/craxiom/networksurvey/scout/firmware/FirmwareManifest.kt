package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.board.v1.Chip
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.nio.charset.StandardCharsets

/** Size limits for everything the firmware update downloads or reads (release-manifest.md). */
object FirmwareLimits {
    /** The largest image the app accepts. The biggest app partition is under 4 MB. */
    const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

    /** The largest manifest or signature file. */
    const val MAX_MANIFEST_BYTES = 256 * 1024

    /** Bytes in a SHA-256. */
    const val SHA256_BYTES = 32
}

/**
 * The signed release manifest (network-survey-esp32 docs/release-manifest.md, format 1). Unknown
 * fields are ignored. Parse it only after its signature checked out ([FirmwareCatalog]).
 */
data class FirmwareManifest(
    val format: Int,
    val version: String,
    /** The oldest version still considered safe; releases below the highest one seen are refused. */
    val minVersion: String?,
    /** When the manifest was signed, UTC, ISO 8601. */
    val released: String?,
    val images: List<Image>,
) {
    /** One board's OTA app image. */
    data class Image(
        /** The `BoardModel` enum name without the `BOARD_MODEL_` prefix. */
        val boardModel: String,
        /** The `Chip` enum name without the `CHIP_` prefix. */
        val chip: String,
        /** Absolute, or relative to the manifest URL. */
        val url: String,
        val size: Int,
        /** Lowercase hex. */
        val sha256: String,
        /** Ed25519 over the image bytes, base64. */
        val signature: String,
    )

    /** @return The image for a board, matched on both its model and its chip; null when there is none. */
    fun imageFor(model: BoardModel, chip: Chip): Image? {
        val modelName = FirmwareBoardNames.manifestModel(model) ?: return null
        val chipName = FirmwareBoardNames.manifestChip(chip) ?: return null
        return images.firstOrNull { it.boardModel == modelName && it.chip == chipName }
    }

    /** The JSON shape, with every field optional so a missing one is reported rather than crashing. */
    private class Json(
        val format: Int?,
        val version: String?,
        @SerializedName("min_version") val minVersion: String?,
        val released: String?,
        val images: List<ImageJson?>?,
    )

    private class ImageJson(
        @SerializedName("board_model") val boardModel: String?,
        val chip: String?,
        val url: String?,
        val size: Long?,
        val sha256: String?,
        val signature: String?,
    )

    companion object {
        /** The only manifest format this app reads. */
        const val SUPPORTED_FORMAT = 1

        private val gson = Gson()

        /**
         * Decodes and checks a manifest: its format, a version, a minimum not above the version, and
         * for each image a size within the limit, a 32-byte SHA-256 and a decodable signature.
         *
         * @throws FirmwareUpdateError.UnsupportedManifestFormat For any format but 1.
         * @throws FirmwareUpdateError.ManifestInvalid For anything else that is wrong.
         */
        @JvmStatic
        fun decode(data: ByteArray): FirmwareManifest {
            val text = String(data, StandardCharsets.UTF_8)
            val json = try {
                val tree = JsonParser.parseString(text)
                if (!tree.isJsonObject) throw FirmwareUpdateError.ManifestInvalid("not an object")
                val format = tree.asJsonObject.get("format")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
                    ?: throw FirmwareUpdateError.ManifestInvalid("no format field")
                if (format != SUPPORTED_FORMAT) throw FirmwareUpdateError.UnsupportedManifestFormat(format)
                gson.fromJson(tree, Json::class.java)
            } catch (e: JsonParseException) {
                throw FirmwareUpdateError.ManifestInvalid(e.message ?: "bad JSON")
            } catch (e: IllegalStateException) {
                throw FirmwareUpdateError.ManifestInvalid(e.message ?: "bad JSON")
            } catch (e: NumberFormatException) {
                throw FirmwareUpdateError.ManifestInvalid(e.message ?: "bad number")
            }
            val version = json.version?.takeIf { it.isNotEmpty() } ?: throw FirmwareUpdateError.ManifestInvalid("empty version")
            json.minVersion?.let { minimum ->
                val floor = FirmwareVersion.parse(minimum)
                val parsed = FirmwareVersion.parse(version)
                if (floor == null || parsed == null || floor > parsed) {
                    throw FirmwareUpdateError.ManifestInvalid("min_version $minimum above version $version")
                }
            }
            val images = (json.images ?: emptyList()).map { image -> checkedImage(image) }
            return FirmwareManifest(SUPPORTED_FORMAT, version, json.minVersion, json.released, images)
        }

        private fun checkedImage(image: ImageJson?): Image {
            val model = image?.boardModel
            val bad = { FirmwareUpdateError.ManifestInvalid("bad image entry for ${model ?: "an image"}") }
            if (image == null || model.isNullOrEmpty() || image.chip.isNullOrEmpty() || image.url.isNullOrEmpty()) throw bad()
            val size = image.size ?: throw bad()
            if (size <= 0 || size > FirmwareLimits.MAX_IMAGE_BYTES) throw bad()
            if (FirmwareHex.decode(image.sha256)?.size != FirmwareLimits.SHA256_BYTES) throw bad()
            if (image.signature == null || FirmwareTrust.decodeSignature(image.signature) == null) throw bad()
            return Image(model, image.chip, image.url, size.toInt(), image.sha256!!.lowercase(), image.signature)
        }
    }
}

/** Hex for SHA-256 values in the manifest. */
object FirmwareHex {
    private const val RADIX = 16
    private const val BYTE_MASK = 0xFF

    /** @return The bytes of [hex], or null when it is not an even number of hex digits. */
    @JvmStatic
    fun decode(hex: String?): ByteArray? {
        if (hex == null || hex.length % 2 != 0) return null
        return ByteArray(hex.length / 2) { i ->
            val high = Character.digit(hex[2 * i], RADIX)
            val low = Character.digit(hex[2 * i + 1], RADIX)
            if (high < 0 || low < 0) return null
            ((high shl 4) or low).toByte()
        }
    }

    /** @return [bytes] as lowercase hex. */
    @JvmStatic
    fun encode(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }
}

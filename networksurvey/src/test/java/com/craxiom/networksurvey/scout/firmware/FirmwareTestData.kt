package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.BoardInfo
import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.board.v1.Chip
import com.google.crypto.tink.subtle.Ed25519Sign
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.random.Random

/** Builders shared by the firmware tests: app images, board Info, keys and signed manifests. */
object FirmwareTestData {
    const val CHIP_ID_C5 = 0x0017
    const val CHIP_ID_C6 = 0x000D

    /** A test Ed25519 key pair, so tests can sign manifests and images. */
    val keyPair: Ed25519Sign.KeyPair = Ed25519Sign.KeyPair.newKeyPair()
    val trust = FirmwareTrust(listOf(FirmwareSigningKey(keyPair.publicKey)))

    /** @return An ESP app image of [size] bytes: the header with [chipId], the app description with [version]. */
    fun image(size: Int = 4_000, version: String? = "0.2.0", chipId: Int = CHIP_ID_C5, seed: Int = 1): ByteArray {
        val bytes = Random(seed).nextBytes(size)
        bytes[0] = 0xE9.toByte()
        bytes[12] = (chipId and 0xFF).toByte()
        bytes[13] = (chipId shr 8).toByte()
        if (version != null) {
            val magic = 0xABCD5432L
            for (i in 0 until 4) bytes[32 + i] = ((magic shr (8 * i)) and 0xFF).toByte()
            for (i in 48 until 112) bytes[i] = 0
            version.toByteArray(StandardCharsets.UTF_8).copyInto(bytes, 48)
            "network-survey-board".toByteArray(StandardCharsets.UTF_8).copyInto(bytes, 80)
        }
        return bytes
    }

    fun sign(data: ByteArray): ByteArray = Ed25519Sign(keyPair.privateKey).sign(data)

    fun signBase64(data: ByteArray): String = Base64.getEncoder().encodeToString(sign(data))

    fun info(
        version: String = "0.1.0",
        model: BoardModel = BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1,
        chip: Chip = Chip.CHIP_ESP32_C5,
        major: Int = 1,
    ): BoardInfo = BoardInfo.newBuilder()
        .setProtocolMajor(major)
        .setFirmwareVersion(version)
        .setBoardModel(model)
        .setChip(chip)
        .setShortId("A1B2")
        .setModelName("ESP32-C5-DevKitC-1")
        .build()

    /** @return A release package for [image], as the catalog makes one after its checks. */
    fun releasePackage(image: ByteArray, version: String = "0.2.0", boardModel: String = "ESP32_C5_DEVKITC_1"): FirmwarePackage = FirmwarePackage(
        image, FirmwarePackage.sha256(image), version, boardModel, EspAppImage.parse(image)!!,
    )

    /** @return A format 1 manifest with one C5 DevKit image entry for [image]. */
    fun manifestJson(
        image: ByteArray,
        version: String = "0.2.0",
        minVersion: String = "0.0.0",
        url: String = "https://example.com/fw/network-survey-board-esp32c5-devkitc1-$version.bin",
        size: Int = image.size,
        format: Int = 1,
        boardModel: String = "ESP32_C5_DEVKITC_1",
        chip: String = "ESP32_C5",
    ): String = """
        {
          "format": $format,
          "name": "NS Scout firmware",
          "version": "$version",
          "min_version": "$minVersion",
          "protocol_major": 1,
          "protocol_minor": 0,
          "released": "2026-10-24T11:19:30Z",
          "future_field": {"ignored": true},
          "images": [
            {
              "board": "esp32c5-devkitc1",
              "board_model": "$boardModel",
              "chip": "$chip",
              "file": "network-survey-board-esp32c5-devkitc1-$version.bin",
              "url": "$url",
              "size": $size,
              "sha256": "${FirmwareHex.encode(FirmwarePackage.sha256(image))}",
              "signature": "${signBase64(image)}"
            }
          ]
        }
    """.trimIndent()
}

/** [FirmwareReleaseHistory] in memory. */
class InMemoryFirmwareReleaseHistory(private var record: FirmwareReleaseRecord = FirmwareReleaseRecord()) : FirmwareReleaseHistory {
    override fun load(): FirmwareReleaseRecord = record
    override fun save(record: FirmwareReleaseRecord) {
        this.record = record
    }
}

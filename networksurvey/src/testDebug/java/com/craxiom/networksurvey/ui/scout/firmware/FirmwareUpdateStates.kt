package com.craxiom.networksurvey.ui.scout.firmware

import com.craxiom.networksurvey.scout.firmware.FirmwareComparison
import com.craxiom.networksurvey.scout.firmware.FirmwareInstallPhase
import com.craxiom.networksurvey.scout.firmware.FirmwareInstallResult
import com.craxiom.networksurvey.scout.firmware.FirmwareManifest
import com.craxiom.networksurvey.scout.firmware.FirmwareOffer
import com.craxiom.networksurvey.scout.firmware.FirmwarePackage
import com.craxiom.networksurvey.scout.firmware.FirmwareTestData
import com.craxiom.networksurvey.scout.firmware.FirmwareUpdateError
import com.craxiom.networksurvey.scout.firmware.VerifiedRelease
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Firmware update screen states shared by the content tests and the captures. */
object FirmwareUpdateStates {
    const val NAME = "NS-A1B2"
    private const val MODEL = "ESP32-C5-DevKitC-1"
    private val image = FirmwareTestData.image(size = 1_235_712, version = "0.2.0")

    fun offer(comparison: FirmwareComparison = FirmwareComparison.NEWER, newerSeen: String? = null): FirmwareOffer {
        val manifest = FirmwareManifest.decode(FirmwareTestData.manifestJson(image).toByteArray())
        val installed = when (comparison) {
            FirmwareComparison.SAME -> "0.2.0"
            FirmwareComparison.OLDER -> "0.3.0"
            else -> "0.1.0"
        }
        return FirmwareOffer(
            VerifiedRelease(manifest, newerSeen), manifest.images[0],
            manifest.images[0].url.toHttpUrl(), installed, comparison,
        )
    }

    fun ui(stage: FirmwareStage, installed: String = "0.1.0", blocker: FirmwareBlocker? = null) =
        FirmwareUpdateUi(
            scoutName = NAME, modelName = MODEL, installedVersion = installed, stage = stage, blocker = blocker,
            consents = (stage as? FirmwareStage.Offer)?.let { offerConsents(it.offer) } ?: emptyList(),
        )

    val releasePackage: FirmwarePackage get() = FirmwareTestData.releasePackage(image)

    fun installing(phase: FirmwareInstallPhase) =
        FirmwareStage.Installing(releasePackage, phase, cancelable = phase is FirmwareInstallPhase.Transferring)

    val finished = FirmwareStage.Finished(FirmwareInstallResult("0.1.0", "0.2.0", imageConfirmed = true, compatible = true))

    val rolledBack = FirmwareStage.Failed(FirmwareUpdateError.RolledBack("0.1.0"), resumable = false)

    val connectionLost = FirmwareStage.Failed(FirmwareUpdateError.ConnectionLost(), resumable = true)
}

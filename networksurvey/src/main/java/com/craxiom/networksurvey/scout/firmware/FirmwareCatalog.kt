package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.BoardInfo
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A release manifest whose signature checked out.
 *
 * @property newerVersionSeen A higher release was verified on this phone before: this one may be stale.
 */
class VerifiedRelease(
    val manifest: FirmwareManifest,
    val newerVersionSeen: String?,
)

/** A release image for one Scout, with how it compares to the Scout's firmware. */
class FirmwareOffer(
    val release: VerifiedRelease,
    val image: FirmwareManifest.Image,
    val imageUrl: HttpUrl,
    val installedVersion: String,
    val comparison: FirmwareComparison,
) {
    val version: String get() = release.manifest.version
}

/**
 * Finds, downloads and verifies official NS Scout firmware, with every app-side check of
 * release-manifest.md: https only and size limits on every download, the manifest's Ed25519
 * signature against the built-in keys, the highest `min_version` seen, an image for the board's
 * model and chip, and after the download its size, SHA-256, signature, header (chip and version)
 * and that the board still is the model the image is for. Whether it is newer than the Scout is reported in [FirmwareOffer.comparison] and turned
 * into a consent by [FirmwarePackage.requiredConsents]. A port of the iOS app's `FirmwareCatalog`.
 *
 * Limitation: a manifest has no expiry, so a host serving an old but validly signed manifest can
 * hold boards on an older release; `min_version` and [history] narrow this.
 */
class FirmwareCatalog(
    private val trust: FirmwareTrust,
    private val fetcher: FirmwareFetching,
    private val history: FirmwareReleaseHistory?,
    private val manifestUrl: String = FirmwareConstants.MANIFEST_URL,
    /** Plain http, for a local test server in unit tests only. */
    private val allowInsecureHttp: Boolean = false,
) {
    /**
     * Fetches the manifest and its signature and checks them.
     *
     * @return The verified release and the raw bytes (for the background check's cache).
     * @throws FirmwareUpdateError When anything is wrong.
     */
    suspend fun fetchLatest(): Pair<VerifiedRelease, SignedManifest> {
        val url = allowedUrl(manifestUrl) ?: throw FirmwareUpdateError.ManifestUnavailable("the release address must use https")
        val signatureUrl = url.newBuilder().encodedPath(url.encodedPath + FirmwareConstants.SIGNATURE_SUFFIX).build()
        val body: ByteArray
        val signature: ByteArray
        try {
            body = fetcher.fetch(url, FirmwareLimits.MAX_MANIFEST_BYTES)
            signature = fetcher.fetch(signatureUrl, FirmwareLimits.MAX_MANIFEST_BYTES)
        } catch (e: FirmwareFetchException) {
            throw FirmwareUpdateError.ManifestUnavailable(e.message ?: e.kind.name)
        }
        val signed = SignedManifest(body, signature)
        return verify(signed, recordHistory = true) to signed
    }

    /**
     * Checks a manifest's signature and contents, then the release history. With [recordHistory]
     * its version and minimum are remembered (a fresh download); a cached copy is only checked.
     *
     * @throws FirmwareUpdateError When anything is wrong.
     */
    fun verify(signed: SignedManifest, recordHistory: Boolean): VerifiedRelease {
        val raw = FirmwareTrust.decodeSignature(signed.signature) ?: throw FirmwareUpdateError.ManifestSignatureInvalid()
        if (!trust.isTrusted(signed.body, raw)) throw FirmwareUpdateError.ManifestSignatureInvalid()
        val manifest = FirmwareManifest.decode(signed.body)

        val record = history?.load() ?: FirmwareReleaseRecord()
        val version = FirmwareVersion.parse(manifest.version)
        FirmwareVersion.higher(record.minimumVersion, manifest.minVersion)?.let { floor ->
            val minimum = FirmwareVersion.parse(floor)
            if (version != null && minimum != null && version < minimum) throw FirmwareUpdateError.StaleRelease(manifest.version, floor)
        }
        val newer = record.highestVersion?.takeIf { highest ->
            val seen = FirmwareVersion.parse(highest)
            seen != null && version != null && seen > version
        }
        if (recordHistory) history?.save(record.merging(manifest.version, manifest.minVersion))
        return VerifiedRelease(manifest, newer)
    }

    /**
     * @return The image for the board described by [info] in [release].
     * @throws FirmwareUpdateError.NoImageForBoard When the release has none for its model and chip.
     */
    fun offer(release: VerifiedRelease, info: BoardInfo, modelName: String): FirmwareOffer {
        val image = release.manifest.imageFor(info.boardModel, info.chip) ?: throw FirmwareUpdateError.NoImageForBoard(modelName)
        val base = manifestUrl.toHttpUrlOrNull() ?: throw FirmwareUpdateError.ManifestInvalid("bad manifest URL")
        val url = base.resolve(image.url)?.takeIf { it.isHttps || allowInsecureHttp }
            ?: throw FirmwareUpdateError.ManifestInvalid("image URL is not https")
        return FirmwareOffer(release, image, url, info.firmwareVersion, FirmwareComparison.of(release.manifest.version, info.firmwareVersion))
    }

    /** @return The offer for [info] from a fresh manifest. */
    suspend fun check(info: BoardInfo, modelName: String): FirmwareOffer = offer(fetchLatest().first, info, modelName)

    /**
     * Downloads the offered image and checks its size, SHA-256, signature, header version and chip.
     *
     * @throws FirmwareUpdateError When the download or any check fails.
     */
    suspend fun download(offer: FirmwareOffer, info: BoardInfo): FirmwarePackage {
        val data = try {
            fetcher.fetch(offer.imageUrl, minOf(offer.image.size, FirmwareLimits.MAX_IMAGE_BYTES))
        } catch (e: FirmwareFetchException) {
            if (e.kind == FirmwareFetchException.Kind.TOO_LARGE) throw FirmwareUpdateError.ImageCorrupt("larger than the release says")
            throw FirmwareUpdateError.DownloadFailed(e.message ?: e.kind.name)
        }
        return verifyImage(data, offer, info)
    }

    /**
     * Checks a downloaded release image against its offer.
     *
     * @throws FirmwareUpdateError When any check fails.
     */
    fun verifyImage(data: ByteArray, offer: FirmwareOffer, info: BoardInfo): FirmwarePackage {
        if (data.size != offer.image.size) throw FirmwareUpdateError.ImageCorrupt("size ${data.size}, expected ${offer.image.size}")
        val digest = FirmwarePackage.sha256(data)
        if (FirmwareHex.encode(digest) != offer.image.sha256) throw FirmwareUpdateError.ImageCorrupt("SHA-256 differs")
        val signature = FirmwareTrust.decodeSignature(offer.image.signature) ?: throw FirmwareUpdateError.SignatureInvalid()
        if (!trust.isTrusted(data, signature)) throw FirmwareUpdateError.SignatureInvalid()
        val header = EspAppImage.parse(data) ?: throw FirmwareUpdateError.ImageCorrupt("not an app image")
        // sign-release.sh checks this too: it binds the manifest's version to the image
        if (header.version != null && header.version != offer.version) {
            throw FirmwareUpdateError.ImageCorrupt("the image is version ${header.version}, the release says ${offer.version}")
        }
        return FirmwarePackage(data, digest, offer.version, offer.image.boardModel, header, offer.release.newerVersionSeen)
            .also { it.checkCompatible(info) }
    }

    private fun allowedUrl(text: String): HttpUrl? = text.toHttpUrlOrNull()?.takeIf { it.isHttps || allowInsecureHttp }
}

/** A manifest exactly as downloaded, with its signature file. */
class SignedManifest(val body: ByteArray, val signature: ByteArray)

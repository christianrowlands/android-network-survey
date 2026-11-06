package com.craxiom.networksurvey.scout.firmware

import com.craxiom.networksurvey.board.v1.BoardModel
import com.craxiom.networksurvey.board.v1.Chip
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Every app-side check of release-manifest.md: https only, size limits, the manifest's signature,
 * the format, min_version and the release history, the image for the board's model and chip, and
 * the image's size, SHA-256, signature, header chip and version. Also the real v0.1.0 release
 * manifest against the release key built into the app.
 */
class FirmwareCatalogTest {
    private val image = FirmwareTestData.image(size = 4_000, version = "0.2.0")
    private val info = FirmwareTestData.info(version = "0.1.0")

    /** Serves fixed bodies by URL and records what was asked for. */
    private class FakeFetcher(val bodies: MutableMap<String, ByteArray> = mutableMapOf()) : FirmwareFetching {
        val fetched = mutableListOf<String>()
        override suspend fun fetch(url: HttpUrl, maxBytes: Int): ByteArray {
            fetched += url.toString()
            val body = bodies[url.toString()] ?: throw FirmwareFetchException(FirmwareFetchException.Kind.HTTP, "HTTP 404")
            if (body.size > maxBytes) throw FirmwareFetchException(FirmwareFetchException.Kind.TOO_LARGE, "too large")
            return body
        }
    }

    private fun fetcherFor(manifest: String, signature: ByteArray = FirmwareTestData.sign(manifest.toByteArray()), imageBytes: ByteArray = image) =
        FakeFetcher(
            mutableMapOf(
                MANIFEST to manifest.toByteArray(StandardCharsets.UTF_8),
                "$MANIFEST.sig" to java.util.Base64.getEncoder().encode(signature),
                IMAGE to imageBytes,
            ),
        )

    private fun catalog(
        fetcher: FirmwareFetching,
        history: FirmwareReleaseHistory = InMemoryFirmwareReleaseHistory(),
        url: String = MANIFEST,
        trust: FirmwareTrust = FirmwareTestData.trust,
    ) = FirmwareCatalog(trust, fetcher, history, url)

    private suspend fun fails(block: suspend () -> Unit): FirmwareUpdateError = try {
        block()
        fail("Expected a FirmwareUpdateError")
        throw IllegalStateException()
    } catch (e: FirmwareUpdateError) {
        e
    }

    @Test
    fun aGoodRelease_isOffered_andItsImagePassesEveryCheck() = runTest {
        val catalog = catalog(fetcherFor(FirmwareTestData.manifestJson(image, url = IMAGE)))

        val offer = catalog.check(info, MODEL)
        val pkg = catalog.download(offer, info)

        assertEquals("0.2.0", offer.version)
        assertEquals(FirmwareComparison.NEWER, offer.comparison)
        assertEquals(IMAGE, offer.imageUrl.toString())
        assertArrayEquals(image, pkg.image)
        assertEquals("0.2.0", pkg.version)
        assertEquals("ESP32_C5_DEVKITC_1", pkg.boardModel)
        assertTrue(pkg.requiredConsents(info).isEmpty())
    }

    @Test
    fun theRealV010Manifest_verifiesWithTheReleaseKeyBuiltIntoTheApp() = runTest {
        val body = resource("firmware-manifest-v0.1.0.json")
        val signature = resource("firmware-manifest-v0.1.0.json.sig")
        val catalog = catalog(FakeFetcher(), trust = FirmwareTrust.RELEASE)

        val release = catalog.verify(SignedManifest(body, signature), recordHistory = false)
        val c5 = release.manifest.imageFor(BoardModel.BOARD_MODEL_ESP32_C5_DEVKITC_1, Chip.CHIP_ESP32_C5)

        assertEquals("0.1.0", release.manifest.version)
        assertNotNull(c5)
        assertEquals(1_235_712, c5!!.size)
        // One changed byte breaks the signature
        val tampered = body.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertTrue(fails { catalog.verify(SignedManifest(tampered, signature), recordHistory = false) } is FirmwareUpdateError.ManifestSignatureInvalid)
    }

    @Test
    fun aBadManifestSignature_isRefused() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = IMAGE)
        val wrong = FirmwareTestData.sign("something else".toByteArray())

        assertTrue(fails { catalog(fetcherFor(manifest, wrong)).check(info, MODEL) } is FirmwareUpdateError.ManifestSignatureInvalid)
    }

    @Test
    fun aManifestSignedByAnUntrustedKey_isRefused() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = IMAGE)

        val error = fails { catalog(fetcherFor(manifest), trust = FirmwareTrust.RELEASE).check(info, MODEL) }

        assertTrue(error is FirmwareUpdateError.ManifestSignatureInvalid)
    }

    @Test
    fun anHttpManifestUrl_isRefused_beforeAnythingIsFetched() = runTest {
        val fetcher = fetcherFor(FirmwareTestData.manifestJson(image, url = IMAGE))

        val error = fails { catalog(fetcher, url = "http://example.com/fw/firmware-manifest.json").check(info, MODEL) }

        assertTrue(error is FirmwareUpdateError.ManifestUnavailable)
        assertTrue(fetcher.fetched.isEmpty())
    }

    @Test
    fun anHttpImageUrl_isRefused() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = "http://example.com/fw/image.bin")

        assertTrue(fails { catalog(fetcherFor(manifest)).check(info, MODEL) } is FirmwareUpdateError.ManifestInvalid)
    }

    @Test
    fun aRelativeImageUrl_resolvesAgainstTheManifest() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = "image.bin")

        assertEquals(IMAGE, catalog(fetcherFor(manifest)).check(info, MODEL).imageUrl.toString())
    }

    @Test
    fun anOversizedManifest_isRefused() = runTest {
        val huge = FirmwareTestData.manifestJson(image, url = IMAGE).padEnd(FirmwareLimits.MAX_MANIFEST_BYTES + 1, ' ')

        assertTrue(fails { catalog(fetcherFor(huge)).check(info, MODEL) } is FirmwareUpdateError.ManifestUnavailable)
    }

    @Test
    fun anImageListedOverTheLimit_isRefused() {
        val manifest = FirmwareTestData.manifestJson(image, url = IMAGE, size = FirmwareLimits.MAX_IMAGE_BYTES + 1)

        assertTrue(fails2 { FirmwareManifest.decode(manifest.toByteArray()) } is FirmwareUpdateError.ManifestInvalid)
    }

    @Test
    fun anImageLargerThanTheReleaseSays_isCorrupt() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = IMAGE, size = image.size - 10)
        val catalog = catalog(fetcherFor(manifest))

        val error = fails { catalog.download(catalog.check(info, MODEL), info) }

        assertTrue(error is FirmwareUpdateError.ImageCorrupt)
    }

    @Test
    fun anImageWithAnotherSha_isCorrupt() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = IMAGE)
        val other = image.copyOf().also { it[200] = (it[200] + 1).toByte() }
        val catalog = catalog(fetcherFor(manifest, imageBytes = other))

        assertTrue(fails { catalog.download(catalog.check(info, MODEL), info) } is FirmwareUpdateError.ImageCorrupt)
    }

    @Test
    fun anImageWithABadSignature_isRefused() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = IMAGE)
            .replace(FirmwareTestData.signBase64(image), FirmwareTestData.signBase64("not the image".toByteArray()))
        val catalog = catalog(fetcherFor(manifest))

        assertTrue(fails { catalog.download(catalog.check(info, MODEL), info) } is FirmwareUpdateError.SignatureInvalid)
    }

    @Test
    fun anImageWhoseHeaderNamesAnotherVersion_isRefused() = runTest {
        val older = FirmwareTestData.image(size = 4_000, version = "0.1.5")
        val manifest = FirmwareTestData.manifestJson(older, version = "0.2.0", url = IMAGE)
        val catalog = catalog(fetcherFor(manifest, imageBytes = older))

        assertTrue(fails { catalog.download(catalog.check(info, MODEL), info) } is FirmwareUpdateError.ImageCorrupt)
    }

    @Test
    fun anImageForAnotherChip_isRefused() = runTest {
        val c6 = FirmwareTestData.image(size = 4_000, chipId = FirmwareTestData.CHIP_ID_C6)
        val manifest = FirmwareTestData.manifestJson(c6, url = IMAGE)
        val catalog = catalog(fetcherFor(manifest, imageBytes = c6))

        assertTrue(fails { catalog.download(catalog.check(info, MODEL), info) } is FirmwareUpdateError.WrongChip)
    }

    @Test
    fun anOfferMadeForAnotherModel_isRefused_evenOnTheSameChip() = runTest {
        val catalog = catalog(fetcherFor(FirmwareTestData.manifestJson(image, url = IMAGE)))
        val offer = catalog.check(info, MODEL)
        val xiao = FirmwareTestData.info(model = BoardModel.BOARD_MODEL_XIAO_ESP32_C5)

        assertTrue(fails { catalog.download(offer, xiao) } is FirmwareUpdateError.WrongBoard)
    }

    @Test
    fun aSignedFileThatIsNotAnAppImage_isCorrupt() = runTest {
        val notAnImage = ByteArray(4_000)
        val catalog = catalog(fetcherFor(FirmwareTestData.manifestJson(notAnImage, url = IMAGE), imageBytes = notAnImage))

        assertTrue(fails { catalog.download(catalog.check(info, MODEL), info) } is FirmwareUpdateError.ImageCorrupt)
    }

    @Test
    fun aReleaseWithoutAnImageForTheBoardsModel_isNoImageForBoard() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = IMAGE, boardModel = "XIAO_ESP32_C5")

        val error = fails { catalog(fetcherFor(manifest)).check(info, MODEL) }

        assertTrue(error is FirmwareUpdateError.NoImageForBoard)
        assertEquals(MODEL, (error as FirmwareUpdateError.NoImageForBoard).model)
    }

    @Test
    fun aMatchingModelOnAnotherChip_isNotOffered() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = IMAGE, chip = "ESP32_C6")

        assertTrue(fails { catalog(fetcherFor(manifest)).check(info, MODEL) } is FirmwareUpdateError.NoImageForBoard)
    }

    @Test
    fun aThirdPartyBoard_hasNoOfficialImage() = runTest {
        val other = FirmwareTestData.info(model = BoardModel.BOARD_MODEL_OTHER)

        assertTrue(fails { catalog(fetcherFor(FirmwareTestData.manifestJson(image, url = IMAGE))).check(other, MODEL) } is FirmwareUpdateError.NoImageForBoard)
    }

    @Test
    fun anOlderOrSameVersion_isOffered_butNeedsTheUsersConsent() = runTest {
        val manifest = FirmwareTestData.manifestJson(image, url = IMAGE)
        val catalog = catalog(fetcherFor(manifest))

        val same = catalog.check(FirmwareTestData.info(version = "0.2.0"), MODEL)
        val older = catalog.check(FirmwareTestData.info(version = "0.3.0"), MODEL)
        val pkg = catalog.download(older, FirmwareTestData.info(version = "0.3.0"))

        assertEquals(FirmwareComparison.SAME, same.comparison)
        assertEquals(FirmwareComparison.OLDER, older.comparison)
        assertEquals(
            listOf(FirmwareConsent.NotNewer(FirmwareComparison.OLDER)),
            pkg.requiredConsents(FirmwareTestData.info(version = "0.3.0")),
        )
    }

    @Test
    fun aReleaseBelowTheHighestMinVersionSeen_isRefusedAsStale() = runTest {
        val history = InMemoryFirmwareReleaseHistory(FirmwareReleaseRecord(highestVersion = "0.3.0", minimumVersion = "0.3.0"))

        val error = fails { catalog(fetcherFor(FirmwareTestData.manifestJson(image, url = IMAGE)), history).check(info, MODEL) }

        assertTrue(error is FirmwareUpdateError.StaleRelease)
    }

    @Test
    fun aReleaseOlderThanOneSeenBefore_isOffered_withAStaleWarningConsent() = runTest {
        val history = InMemoryFirmwareReleaseHistory(FirmwareReleaseRecord(highestVersion = "0.4.0"))
        val catalog = catalog(fetcherFor(FirmwareTestData.manifestJson(image, url = IMAGE)), history)

        val offer = catalog.check(info, MODEL)
        val pkg = catalog.download(offer, info)

        assertEquals("0.4.0", offer.release.newerVersionSeen)
        assertEquals(listOf(FirmwareConsent.StaleRelease("0.4.0")), pkg.requiredConsents(info))
        assertEquals("0.4.0", history.load().highestVersion)
    }

    @Test
    fun aVerifiedRelease_isRemembered_soAReplayedOlderOneIsCaught() = runTest {
        val history = InMemoryFirmwareReleaseHistory()
        catalog(fetcherFor(FirmwareTestData.manifestJson(image, version = "0.2.0", minVersion = "0.2.0", url = IMAGE)), history).check(info, MODEL)
        val old = FirmwareTestData.image(size = 4_000, version = "0.1.5")

        val error = fails {
            catalog(fetcherFor(FirmwareTestData.manifestJson(old, version = "0.1.5", url = IMAGE), imageBytes = old), history).check(info, MODEL)
        }

        assertEquals(FirmwareReleaseRecord("0.2.0", "0.2.0"), history.load())
        assertTrue(error is FirmwareUpdateError.StaleRelease)
    }

    @Test
    fun anUnreachableManifest_isManifestUnavailable() = runTest {
        assertTrue(fails { catalog(FakeFetcher()).check(info, MODEL) } is FirmwareUpdateError.ManifestUnavailable)
    }

    @Test
    fun theReleaseStatus_comparesTheBoardWithTheRelease() {
        val release = catalog(FakeFetcher()).verify(
            FirmwareTestData.manifestJson(image, url = IMAGE).toByteArray().let { SignedManifest(it, FirmwareTestData.sign(it)) },
            recordHistory = false,
        )

        assertEquals(FirmwareReleaseStatus.UpdateAvailable("0.2.0"), FirmwareReleaseCache.status(release, info))
        assertEquals(FirmwareReleaseStatus.UpToDate, FirmwareReleaseCache.status(release, FirmwareTestData.info(version = "0.2.0")))
        assertEquals(FirmwareReleaseStatus.NewerThanRelease, FirmwareReleaseCache.status(release, FirmwareTestData.info(version = "0.9.0")))
        assertEquals(FirmwareReleaseStatus.Unknown, FirmwareReleaseCache.status(release, FirmwareTestData.info(model = BoardModel.BOARD_MODEL_XIAO_ESP32_C5)))
        assertEquals(FirmwareReleaseStatus.Unknown, FirmwareReleaseCache.status(null, info))
        assertNull(FirmwareTrust.decodeSignature("not base64!".toByteArray()))
    }

    private fun fails2(block: () -> Unit): FirmwareUpdateError = try {
        block()
        fail("Expected a FirmwareUpdateError")
        throw IllegalStateException()
    } catch (e: FirmwareUpdateError) {
        e
    }

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("scout-firmware/$name")!!.use { it.readBytes() }

    private companion object {
        const val MANIFEST = "https://example.com/fw/firmware-manifest.json"
        const val IMAGE = "https://example.com/fw/image.bin"
        const val MODEL = "ESP32-C5-DevKitC-1"
    }
}

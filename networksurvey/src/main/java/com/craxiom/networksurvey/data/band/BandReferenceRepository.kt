package com.craxiom.networksurvey.data.band

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads the generated cellular band reference from the app assets and keeps it in memory.
 *
 * The asset is small, offline, and changes only when a 3GPP release does, which is the same shape
 * as the Bluetooth company identifier assets and is handled the same way: parse once, lazily, and
 * hold the result for the life of the process.
 *
 * This backs the browsable band reference only. Labelling a live cell still goes through
 * `CellularUtils`, which runs on a display path with no `Context` and must not wait on an asset
 * load.
 */
object BandReferenceRepository {

    private const val ASSET_NAME = "cellular_bands.json"

    private val cached = AtomicReference<List<CellularBand>?>(null)

    /**
     * Returns every known band, LTE first and then NR, each ascending by band number. Parses the
     * asset on first call and returns the cached list afterwards. Returns an empty list when the
     * asset cannot be read, so the browser shows an empty state rather than crashing.
     */
    suspend fun bands(context: Context): List<CellularBand> {
        cached.get()?.let { return it }
        return withContext(Dispatchers.IO) {
            cached.get() ?: parse(context).also { cached.set(it) }
        }
    }

    private fun parse(context: Context): List<CellularBand> {
        val started = System.currentTimeMillis()
        return try {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val bands = readArray(root.optJSONArray("lte"), BandTechnology.LTE) +
                    readArray(root.optJSONArray("nr"), BandTechnology.NR)
            Timber.d("Parsed %d cellular bands in %d ms", bands.size,
                System.currentTimeMillis() - started)
            bands
        } catch (e: Exception) {
            Timber.e(e, "Could not read the cellular band reference asset")
            emptyList()
        }
    }

    private fun readArray(array: JSONArray?, technology: BandTechnology): List<CellularBand> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { readBand(it, technology) }
        }
    }

    private fun readBand(json: JSONObject, technology: BandTechnology) = CellularBand(
        technology = technology,
        number = json.getInt("band"),
        name = json.optStringOrNull("name"),
        duplex = json.optStringOrNull("duplex")?.let { value ->
            DuplexMode.entries.firstOrNull { it.name == value }
        },
        downlinkMhz = json.optDoubleRange("dlMhz"),
        uplinkMhz = json.optDoubleRange("ulMhz"),
        downlinkChannels = json.optIntRange("dlChannels"),
        uplinkChannels = json.optIntRange("ulChannels"),
        bandwidthsMhz = json.optDoubleList("bandwidthsMhz"),
        subcarrierSpacingsKhz = json.optIntList("scsKhz"),
        isFr2 = json.optInt("fr", 1) == 2,
        resolvesLiveCells = json.optBoolean("resolves", false),
        referenceOnlyReason = json.optStringOrNull("referenceOnlyReason"),
        shadowedBy = json.optIntList("shadowedBy"),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optDoubleRange(key: String): ClosedFloatingPointRange<Double>? {
        val array = optJSONArray(key) ?: return null
        if (array.length() < 2) return null
        return array.getDouble(0)..array.getDouble(1)
    }

    private fun JSONObject.optIntRange(key: String): IntRange? {
        val array = optJSONArray(key) ?: return null
        if (array.length() < 2) return null
        return array.getInt(0)..array.getInt(1)
    }

    private fun JSONObject.optDoubleList(key: String): List<Double> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getDouble(it) }
    }

    private fun JSONObject.optIntList(key: String): List<Int> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getInt(it) }
    }
}

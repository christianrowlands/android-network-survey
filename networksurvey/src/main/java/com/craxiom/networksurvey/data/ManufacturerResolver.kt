package com.craxiom.networksurvey.data

import android.content.Context
import com.craxiom.networksurvey.data.oui.OuiRepository
import com.craxiom.networksurvey.data.oui.OuiResult
import com.craxiom.networksurvey.data.oui.OuiStatus

/**
 * Unified resolver for Bluetooth device manufacturer information.
 *
 * Calls the two synchronous YAML-backed resolvers ([BluetoothCompanyResolver],
 * [BluetoothUuidResolver]) in parallel with the asynchronous OUI repository so the UI can surface
 * the fast paths immediately and fill in the chipset row when the network call returns.
 *
 * **UI-only.** The returned [ManufacturerSources] must not be written to any proto record, CSV
 * logger, or MQTT/gRPC serializer.
 */
class ManufacturerResolver(
    private val companyResolver: BluetoothCompanyResolver,
    private val uuidResolver: BluetoothUuidResolver,
    private val ouiRepository: OuiRepository
) {

    /**
     * Resolves all three sources for a Bluetooth device.
     *
     * The YAML lookups are synchronous; the OUI lookup suspends on the network. When the OUI
     * lookup fails or is disabled, the YAML portions still populate. Callers get a
     * [ManufacturerSources] with a null [ManufacturerSources.ouiVendor] and the appropriate
     * [OuiStatus].
     */
    suspend fun resolve(
        mac: String?,
        serviceUuids: List<String>?,
        companyId: String?
    ): ManufacturerSources {
        val companyIdVendor = resolveCompanyId(companyId)
        val uuidVendor = resolveUuidVendor(serviceUuids)

        if (mac.isNullOrBlank()) {
            return ManufacturerSources.fromYamlOnly(companyIdVendor, uuidVendor, OuiStatus.UNKNOWN)
        }

        val ouiResult = runCatching { ouiRepository.lookup(mac) }
            .getOrElse { OuiResult.TRANSIENT_FAILURE }

        return ManufacturerSources.from(companyIdVendor, uuidVendor, ouiResult)
    }

    /**
     * Resolves only the YAML-backed sources (Company ID + service UUID). Used for the initial
     * synchronous paint in the UI before the OUI network call completes.
     */
    fun resolveSynchronousSources(
        serviceUuids: List<String>?,
        companyId: String?
    ): ManufacturerSources {
        return ManufacturerSources.fromYamlOnly(
            companyIdVendor = resolveCompanyId(companyId),
            uuidVendor = resolveUuidVendor(serviceUuids),
            ouiStatus = OuiStatus.LOADING
        )
    }

    private fun resolveCompanyId(companyId: String?): String? {
        if (companyId.isNullOrBlank()) return null
        return try {
            companyResolver.getCompanyName(companyId)?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            null
        }
    }

    private fun resolveUuidVendor(serviceUuids: List<String>?): String? {
        if (serviceUuids.isNullOrEmpty()) return null
        val fullUuid = serviceUuids.firstOrNull() ?: return null
        if (fullUuid.length < 8) return null
        val companyIdHex = fullUuid.substring(4, 8)
        return try {
            uuidResolver.getNameForUuid(companyIdHex)?.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            null
        }
    }

    companion object {

        @Volatile
        private var INSTANCE: ManufacturerResolver? = null

        /**
         * Returns the process-wide [ManufacturerResolver] singleton. Reuses the shared YAML
         * resolver instances from [BluetoothCompanyNameProvider] (so the Bluetooth-SIG
         * company / UUID data is parsed once) and the [OuiRepository] singleton.
         */
        @JvmStatic
        fun getInstance(context: Context): ManufacturerResolver {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(app: Context): ManufacturerResolver {
            val combined = BluetoothCompanyNameProvider.getInstance(app)
            return ManufacturerResolver(
                companyResolver = combined.companyResolver,
                uuidResolver = combined.uuidResolver,
                ouiRepository = OuiRepository.getInstance(app)
            )
        }
    }
}

package com.craxiom.networksurvey.data

import com.craxiom.networksurvey.data.oui.OuiResult
import com.craxiom.networksurvey.data.oui.OuiStatus

/**
 * Aggregated manufacturer identification for a Bluetooth device from three independent sources.
 *
 * **UI-only.** Do not set these fields on proto records, CSV loggers, or MQTT/gRPC streams.
 * Manufacturer information must never leave the UI layer.
 *
 *  - [companyIdVendor] - from the Bluetooth Company Identifier in the advertisement header
 *    (usually the device brand, e.g. Apple, Samsung).
 *  - [uuidVendor] - from the 16-bit vendor slot in an advertised service UUID (often a feature
 *    provider like Google Nearby, not the device maker).
 *  - [ouiVendor] + [ouiStatus] - from the OUI lookup of the MAC prefix (radio chipset maker).
 *
 * The `primary()` priority used by the collapsed UI label is `CompanyID > UUID > OUI`.
 * CompanyID most often reflects the device brand; OUI is a fallback chipset indicator.
 */
data class ManufacturerSources(
    val companyIdVendor: String?,
    val uuidVendor: String?,
    val ouiVendor: String?,
    val ouiStatus: OuiStatus
) {

    /**
     * The single label to render on the collapsed Bluetooth manufacturer line. Priority:
     * CompanyID → UUID → OUI. Returns null when no source resolved.
     */
    fun primary(): String? {
        if (!companyIdVendor.isNullOrBlank()) return companyIdVendor
        if (!uuidVendor.isNullOrBlank()) return uuidVendor
        if (!ouiVendor.isNullOrBlank()) return ouiVendor
        return null
    }

    /**
     * True when at least two resolved sources agree on the vendor name. Used to decide the
     * "(N sources agree)" affordance copy.
     */
    fun allAgree(): Boolean {
        val resolved = listOfNotNull(
            companyIdVendor?.takeIf { it.isNotBlank() },
            uuidVendor?.takeIf { it.isNotBlank() },
            ouiVendor?.takeIf { it.isNotBlank() }
        )
        if (resolved.size < 2) return resolved.isNotEmpty()
        val first = resolved.first()
        return resolved.all { it.equals(first, ignoreCase = true) }
    }

    /** True when no source yielded a usable vendor string. */
    fun allUnknown(): Boolean =
        companyIdVendor.isNullOrBlank() && uuidVendor.isNullOrBlank() && ouiVendor.isNullOrBlank()

    /** Count of sources that resolved to a non-blank vendor string. */
    fun resolvedCount(): Int {
        var n = 0
        if (!companyIdVendor.isNullOrBlank()) n++
        if (!uuidVendor.isNullOrBlank()) n++
        if (!ouiVendor.isNullOrBlank()) n++
        return n
    }

    companion object {
        fun fromYamlOnly(
            companyIdVendor: String?,
            uuidVendor: String?,
            ouiStatus: OuiStatus
        ): ManufacturerSources {
            return ManufacturerSources(
                companyIdVendor = companyIdVendor?.takeIf { it.isNotBlank() },
                uuidVendor = uuidVendor?.takeIf { it.isNotBlank() },
                ouiVendor = null,
                ouiStatus = ouiStatus
            )
        }

        fun from(
            companyIdVendor: String?,
            uuidVendor: String?,
            oui: OuiResult
        ): ManufacturerSources {
            val vendorFromOui = when (oui.status) {
                OuiStatus.RESOLVED -> oui.vendor
                OuiStatus.PRIVATE -> oui.vendor
                else -> null
            }?.takeIf { it.isNotBlank() }
            return ManufacturerSources(
                companyIdVendor = companyIdVendor?.takeIf { it.isNotBlank() },
                uuidVendor = uuidVendor?.takeIf { it.isNotBlank() },
                ouiVendor = vendorFromOui,
                ouiStatus = oui.status
            )
        }
    }
}

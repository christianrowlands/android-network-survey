package com.craxiom.networksurvey.data

import timber.log.Timber

class BluetoothCompanyNameResolver(
    private val companyResolver: BluetoothCompanyResolver,
    private val uuidResolver: BluetoothUuidResolver
) {

    /**
     * Takes in the service UUIDs and companyId and tries to resolve the associated company name.
     * Priority is given to the service UUID vendor ID, then falls back to the companyId.
     */
    fun resolveCompanyName(serviceUuids: List<String>?, companyId: String?): String {
        val uuidCompanyName = resolveFromServiceUuids(serviceUuids)
        if (!uuidCompanyName.isNullOrEmpty()) return uuidCompanyName

        return try {
            if (companyId.isNullOrEmpty()) "" else companyResolver.getCompanyName(companyId) ?: ""
        } catch (e: Exception) {
            Timber.w(
                e,
                "Unable to parse the company ID %s to an int. Returning the company ID as the name.",
                companyId
            )
            companyId ?: ""
        }
    }

    /**
     * Attempts to resolve a company name from the first UUID in the list.
     */
    fun resolveFromServiceUuids(serviceUuids: List<String>?): String? {
        if (serviceUuids.isNullOrEmpty()) return ""

        val fullUuid = serviceUuids[0]
        if (fullUuid.length < 8) return ""

        val companyIdHex = fullUuid.substring(4, 8)
        return uuidResolver.getNameForUuid(companyIdHex)
    }
}

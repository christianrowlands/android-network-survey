package com.craxiom.networksurvey.data.oui

import com.craxiom.messaging.BluetoothRecordData
import com.craxiom.messaging.WifiBeaconRecordData
import com.google.protobuf.Descriptors
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards the "OUI lookup is UI-only" invariant at compile-time + test-time.
 *
 * The tower-service OUI lookup feature resolves manufacturer names from MAC prefixes, but this
 * information must **never** land in CSV files, MQTT JSON payloads, gRPC streams, or anything
 * else serialized from a protobuf record. This test checks that no field with a vendor-ish name
 * exists on `WifiBeaconRecordData` or `BluetoothRecordData`. If someone accidentally adds one,
 * the fix is to revert the proto change not to update this test.
 */
class EnforceProtoNoManufacturerTest {

    @Test
    fun `WifiBeaconRecordData has no manufacturer-adjacent fields`() {
        assertNoSuspiciousFields(WifiBeaconRecordData.getDescriptor())
    }

    @Test
    fun `BluetoothRecordData has no manufacturer-adjacent fields`() {
        assertNoSuspiciousFields(BluetoothRecordData.getDescriptor())
    }

    private fun assertNoSuspiciousFields(descriptor: Descriptors.Descriptor) {
        val matches = descriptor.fields.filter { field ->
            val name = field.name.lowercase()
            BAD_TOKENS.any { token -> name.contains(token) } && !ALLOWED_EXCEPTIONS.contains(name)
        }
        if (matches.isNotEmpty()) {
            fail(
                "Proto ${descriptor.fullName} gained a vendor/manufacturer/oui-adjacent field: " +
                        "${matches.joinToString { it.name }}. OUI lookup is UI-only, this info " +
                        "must not leak into CSV / MQTT / gRPC outputs."
            )
        }
    }

    companion object {
        private val BAD_TOKENS = listOf("manufacturer", "vendor", "oui")

        /**
         * Fields with one of the banned substrings that predate the feature. `companyId` is fine
         * on Bluetooth, it's the raw BLE Company Identifier integer, not a resolved vendor
         * string. Add here only with a justification comment.
         */
        private val ALLOWED_EXCEPTIONS: Set<String> = emptySet()
    }
}

package com.craxiom.networksurvey.scout.firmware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64

/**
 * The backup firmware signing key is a real key of its own, and building the trust list (which
 * parses it) doesn't fail.
 */
class FirmwareTrustBackupKeyTest {
    @Test
    fun `the backup key is a 32 byte key that differs from the release key`() {
        assertEquals(32, Base64.getDecoder().decode(FirmwareTrust.BACKUP_KEY_BASE64).size)
        assertNotEquals(FirmwareTrust.RELEASE_KEY_BASE64, FirmwareTrust.BACKUP_KEY_BASE64)
        FirmwareTrust.RELEASE
    }
}

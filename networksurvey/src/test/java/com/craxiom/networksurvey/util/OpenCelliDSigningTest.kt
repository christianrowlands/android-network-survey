package com.craxiom.networksurvey.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one rule for how OpenCelliD uploads are signed: anonymous without a personal key, the
 * personal key without the shared token, and otherwise the user's choice.
 */
class OpenCelliDSigningTest {

    @Test
    fun `without a personal key uploads are anonymous, whatever was chosen`() {
        assertTrue(OpenCelliDSigning.isAnonymous(hasPersonalKey = false, hasSharedToken = true, anonymousChosen = false))
        assertTrue(OpenCelliDSigning.isAnonymous(hasPersonalKey = false, hasSharedToken = true, anonymousChosen = true))
    }

    @Test
    fun `with a personal key uploads use it unless anonymous was chosen`() {
        assertFalse(OpenCelliDSigning.isAnonymous(hasPersonalKey = true, hasSharedToken = true, anonymousChosen = false))
        assertTrue(OpenCelliDSigning.isAnonymous(hasPersonalKey = true, hasSharedToken = true, anonymousChosen = true))
    }

    @Test
    fun `a build without the shared token always uses the personal key`() {
        assertFalse(OpenCelliDSigning.isAnonymous(hasPersonalKey = true, hasSharedToken = false, anonymousChosen = true))
    }
}

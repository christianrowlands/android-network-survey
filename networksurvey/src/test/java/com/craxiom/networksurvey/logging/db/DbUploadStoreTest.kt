package com.craxiom.networksurvey.logging.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DbUploadStoreTest {

    @Test
    fun `test first record always allows movement`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, null)
        assertTrue("First record should always be allowed.", result)
    }

    @Test
    fun `test small movement does not trigger`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0001, -102.0001))
        assertFalse("Small movement should not trigger.", result)
    }

    @Test
    fun `test movement beyond threshold triggers`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0005, -102.0005))
        assertTrue("Movement beyond threshold should trigger.", result)
    }

    @Test
    fun `test exactly at threshold`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0003, -102.0003))
        assertTrue("Movement at threshold should trigger.", result)
    }

    @Test
    fun `test staying in same location`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0000, -102.0000))
        assertFalse("No movement should not trigger.", result)
    }

    @Test
    fun `test large movement definitely triggers`() {
        val result = DbUploadStore.hasMovedEnough(23.0000, -102.0000, Pair(23.0500, -102.0500))
        assertTrue("Large movement should trigger.", result)
    }
}



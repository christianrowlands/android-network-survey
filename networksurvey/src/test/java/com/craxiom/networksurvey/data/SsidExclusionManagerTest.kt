package com.craxiom.networksurvey.data

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

/**
 * Unit tests for SsidExclusionManager.
 *
 * Tests the functionality of managing SSID exclusion lists including adding,
 * removing, checking exclusions, and enforcing size limits.
 */
class SsidExclusionManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var ssidExclusionManager: SsidExclusionManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        // Setup SharedPreferences mocks
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockSharedPreferences)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putStringSet(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).then { }

        // Initialize the manager
        ssidExclusionManager = SsidExclusionManager(mockContext)
    }

    @Test
    fun `addExcludedSsid_emptyList_returnsTrue`() {
        // Given an empty exclusion list
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(emptySet())

        // When adding a new SSID
        val result = ssidExclusionManager.addExcludedSsid("TestNetwork")

        // Then it should be added successfully
        assertTrue(result)
        verify(mockEditor).putStringSet(eq("excluded_ssids"), argThat { contains("TestNetwork") })
        verify(mockEditor).apply()
    }

    @Test
    fun `addExcludedSsid_duplicateSsid_returnsFalse`() {
        // Given an exclusion list with an existing SSID
        val existingSet = setOf("TestNetwork")
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(existingSet)

        // When trying to add the same SSID again
        val result = ssidExclusionManager.addExcludedSsid("TestNetwork")

        // Then it should not be added
        assertFalse(result)
        verify(mockEditor, never()).putStringSet(any(), any())
    }

    @Test
    fun `addExcludedSsid_atMaxCapacity_returnsFalse`() {
        // Given a full exclusion list (30 items)
        val fullSet = (1..30).map { "Network$it" }.toSet()
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(fullSet)

        // When trying to add another SSID
        val result = ssidExclusionManager.addExcludedSsid("Network31")

        // Then it should not be added
        assertFalse(result)
        verify(mockEditor, never()).putStringSet(any(), any())
    }

    @Test
    fun `removeExcludedSsid_existingSsid_removesSuccessfully`() {
        // Given an exclusion list with some SSIDs
        val existingSet = setOf("Network1", "Network2", "Network3")
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(existingSet)

        // When removing an existing SSID
        val result = ssidExclusionManager.removeExcludedSsid("Network2")

        // Then it should be removed and return true
        assertTrue(result)
        verify(mockEditor).putStringSet(
            eq("excluded_ssids"),
            argThat { contains("Network1") && contains("Network3") && !contains("Network2") }
        )
        verify(mockEditor).apply()
    }

    @Test
    fun `removeExcludedSsid_nonExistentSsid_doesNothing`() {
        // Given an exclusion list
        val existingSet = setOf("Network1", "Network2")
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(existingSet)

        // When removing a non-existent SSID
        val result = ssidExclusionManager.removeExcludedSsid("Network3")

        // Then it should return false and not save
        assertFalse(result)
        verify(mockEditor, never()).putStringSet(any(), any())
        verify(mockEditor, never()).apply()
    }

    @Test
    fun `isExcluded_existingSsid_returnsTrue`() {
        // Given an exclusion list with an SSID
        val existingSet = setOf("Network1", "Network2")
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(existingSet)

        // When checking if an SSID is excluded
        val result = ssidExclusionManager.isExcluded("Network1")

        // Then it should return true
        assertTrue(result)
    }

    @Test
    fun `isExcluded_nonExistentSsid_returnsFalse`() {
        // Given an exclusion list
        val existingSet = setOf("Network1", "Network2")
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(existingSet)

        // When checking if a non-existent SSID is excluded
        val result = ssidExclusionManager.isExcluded("Network3")

        // Then it should return false
        assertFalse(result)
    }

    @Test
    fun `isExcluded_emptyList_returnsFalse`() {
        // Given an empty exclusion list
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(emptySet())

        // When checking any SSID
        val result = ssidExclusionManager.isExcluded("AnyNetwork")

        // Then it should return false
        assertFalse(result)
    }

    @Test
    fun `getExcludedSsids_returnsCurrentList`() {
        // Given an exclusion list
        val expectedSet = setOf("Network1", "Network2", "Network3")
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(expectedSet)

        // When getting the excluded SSIDs
        val result = ssidExclusionManager.getExcludedSsids()

        // Then it should return the correct set
        assertEquals(expectedSet, result)
    }

    @Test
    fun `getExcludedSsids_emptyList_returnsEmptySet`() {
        // Given no stored exclusions
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(null)

        // When getting the excluded SSIDs
        val result = ssidExclusionManager.getExcludedSsids()

        // Then it should return an empty set
        assertTrue(result.isEmpty())
    }

    @Test
    fun `clearExcludedSsids_clearsAllEntries`() {
        // Given any state
        // When clearing all excluded SSIDs
        ssidExclusionManager.clearExcludedSsids()

        // Then it should clear the preferences
        verify(mockEditor).putStringSet(eq("excluded_ssids"), argThat { isEmpty() })
        verify(mockEditor).apply()
    }

    @Test
    fun `isAtMaxCapacity_withFullList_returnsTrue`() {
        // Given a full exclusion list (30 items)
        val fullSet = (1..30).map { "Network$it" }.toSet()
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(fullSet)

        // When checking if at max capacity
        val result = ssidExclusionManager.isAtMaxCapacity()

        // Then it should return true
        assertTrue(result)
    }

    @Test
    fun `isAtMaxCapacity_withPartialList_returnsFalse`() {
        // Given a partial exclusion list
        val partialSet = setOf("Network1", "Network2", "Network3")
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(partialSet)

        // When checking if at max capacity
        val result = ssidExclusionManager.isAtMaxCapacity()

        // Then it should return false
        assertFalse(result)
    }

    @Test
    fun `isAtMaxCapacity_withEmptyList_returnsFalse`() {
        // Given an empty exclusion list
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(emptySet())

        // When checking if at max capacity
        val result = ssidExclusionManager.isAtMaxCapacity()

        // Then it should return false
        assertFalse(result)
    }

    @Test
    fun `persistence_dataPersistedAcrossInstances`() {
        // Given an exclusion list is saved
        val savedSet = setOf("PersistentNetwork1", "PersistentNetwork2")
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenReturn(savedSet)

        // When creating a new instance of the manager
        val newManager = SsidExclusionManager(mockContext)

        // Then it should have access to the same data
        val result = newManager.getExcludedSsids()
        assertEquals(savedSet, result)
    }

    @Test
    fun `addExcludedSsid_multipleSsids_addsAllSuccessfully`() {
        // Given an empty list initially
        var currentSet = emptySet<String>()
        whenever(mockSharedPreferences.getStringSet(any(), any())).thenAnswer { currentSet }
        whenever(mockEditor.putStringSet(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            currentSet = invocation.arguments[1] as Set<String>
            mockEditor
        }

        // When adding multiple SSIDs
        val ssidsToAdd = listOf("Network1", "Network2", "Network3")
        ssidsToAdd.forEach { ssid ->
            val result = ssidExclusionManager.addExcludedSsid(ssid)
            assertTrue("Failed to add $ssid", result)
        }

        // Then all should be in the list
        verify(mockEditor, times(3)).putStringSet(any(), any())
        verify(mockEditor, times(3)).apply()
    }

    @Test
    fun `maximumSizeConstant_isThirty`() {
        // Verify the maximum size constant is set to 30
        assertEquals(30, SsidExclusionManager.MAX_EXCLUSION_LIST_SIZE)
    }
}
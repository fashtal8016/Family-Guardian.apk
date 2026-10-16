package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FamilyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createChildLink_authenticatedParent_createsDocumentSuccessfully() = runBlocking {
        signInTestUser(PARENT_EMAIL)
        val repository = FamilyRepository(firestore)

        val uniqueCode = "FG-" + UUID.randomUUID().toString().take(4)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createChildLink("Ali", uniqueCode)
        }
        assertTrue(result.isSuccess)
        val linkId = result.getOrThrow()
        assertNotNull(linkId)
    }

    @Test
    fun observeChildrenLinks_authenticatedParent_emitsLinkedChildren() = runBlocking {
        val parentUid = signInTestUser(PARENT_EMAIL)
        val repository = FamilyRepository(firestore)

        val uniqueCode = "FG-" + UUID.randomUUID().toString().take(4)
        val linkId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createChildLink("Sara", uniqueCode).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeChildrenLinks(parentUid).first { list -> list.any { it.id == linkId } }
        }
        assertTrue(emitted.any { it.id == linkId && it.childName == "Sara" })
    }

    @Test
    fun reportLocation_childAndParentFlow_succeeds() = runBlocking {
        val parentUid = signInTestUser(PARENT_EMAIL)
        val repository = FamilyRepository(firestore)

        val childId = "child_dev_" + UUID.randomUUID().toString().take(6)
        val locResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.reportLocation(
                parentId = parentUid,
                childId = childId,
                latitude = 35.7219,
                longitude = 51.3347,
                accuracy = 8.5,
                provider = "fused"
            )
        }
        assertTrue(locResult.isSuccess)
        val locId = locResult.getOrThrow()
        assertNotNull(locId)

        val locs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeLocations(parentUid, childId).first { it.isNotEmpty() }
        }
        assertEquals(1, locs.size)
        assertEquals(35.7219, locs.first().latitude, 0.0001)
    }

    @Test
    fun requireUserId_unauthenticatedUser_throwsIllegalStateException() = runBlocking {
        auth.signOut()
        val repository = FamilyRepository(firestore)

        var caught = false
        try {
            repository.requireUserId()
        } catch (e: IllegalStateException) {
            caught = true
        }
        assertTrue(caught)
    }

    private companion object {
        const val PARENT_EMAIL = "parent@familyguardian.com"
        const val BOB_EMAIL = "stranger@familyguardian.com"
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}

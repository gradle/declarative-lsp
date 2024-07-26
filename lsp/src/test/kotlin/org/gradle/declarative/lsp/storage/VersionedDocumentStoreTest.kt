package org.gradle.declarative.lsp.storage

import io.mockk.mockk
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VersionedDocumentStoreTest {

    private lateinit var store: VersionedDocumentStore

    @BeforeEach
    fun setup() {
        store = VersionedDocumentStore()
    }

    @Test
    fun `null to initial works`() {
        store.storeInitial(TEST_URI, NEW_DOM)

        store[TEST_URI].let {
            assertNotNull(it)
            assertEquals(it, NEW_DOM)
        }
    }

    @Test
    fun `initialized to initialized is stored`() {
        store.storeInitial(TEST_URI, STORED_DOM)
        store.storeInitial(TEST_URI, NEW_DOM)

        store[TEST_URI].let {
            assertNotNull(it)
            assertEquals(it, NEW_DOM)
        }
    }

    @Test
    fun `null to versioned is stored`() {
        store.storeVersioned(TEST_URI, 1, NEW_DOM)

        store[TEST_URI].let {
            assertNotNull(it)
            assertEquals(it, NEW_DOM)
        }
    }

    @Test
    fun `initialized to versioned is stored`() {
        store.storeInitial(TEST_URI, STORED_DOM)
        store.storeVersioned(TEST_URI, 1, NEW_DOM)

        store[TEST_URI].let {
            assertNotNull(it)
            assertEquals(it, NEW_DOM)
        }
    }

    @Test
    fun `higher versions are stored`() {
        store.storeVersioned(TEST_URI, 1, STORED_DOM)
        store.storeVersioned(TEST_URI, 2, NEW_DOM)

        store[TEST_URI].let {
            assertNotNull(it)
            assertEquals(it, NEW_DOM)
        }
    }

    @Test
    fun `version is not replaced if lower`() {
        store.storeVersioned(TEST_URI, 2, STORED_DOM)
        store.storeVersioned(TEST_URI, 1, NEW_DOM)

        store[TEST_URI].let {
            assertNotNull(it)
            assertEquals(it, STORED_DOM)
        }
    }

    companion object {
        val TEST_URI = java.net.URI.create("file:///test")

        private val STORED_DOM = mockk<DocumentOverlayResult>()
        private val NEW_DOM = mockk<DocumentOverlayResult>()
    }

}
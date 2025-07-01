/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.declarative.lsp

import io.mockk.mockk
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertSame

class VersionedDocumentStoreTest {

    private lateinit var store: VersionedDocumentStore

    @BeforeEach
    fun setup() {
        store = VersionedDocumentStore()
    }

    @Test
    fun `null to initial works`() {
        store.storeInitial(TEST_URI, "v0", NEW_DOM, NEW_SCHEMAS)

        store[TEST_URI].let {
            assertNotNull(it)
            assertEquals(it!!.document, "v0")
            assertEquals(it.dom, NEW_DOM)
            assertSame(it.analysisSchemas, NEW_SCHEMAS)
        }
    }

    @Test
    fun `initialized to initialized is stored`() {
        store.storeInitial(TEST_URI, "v0", STORED_DOM, STORED_SCHEMAS)
        store.storeInitial(TEST_URI, "v0", NEW_DOM, NEW_SCHEMAS)

        store[TEST_URI].let {
            it!!
            assertEquals(it.document, "v0")
            assertEquals(it.dom, NEW_DOM)
            assertSame(it.analysisSchemas, NEW_SCHEMAS)
        }
    }

    @Test
    fun `null to versioned is stored`() {
        store.storeVersioned(TEST_URI, 1, "v1", NEW_DOM, NEW_SCHEMAS)

        store[TEST_URI].let {
            it!!
            assertEquals(it.document, "v1")
        }
    }

    @Test
    fun `initialized to versioned is stored`() {
        store.storeInitial(TEST_URI, "v0", STORED_DOM, STORED_SCHEMAS)
        store.storeVersioned(TEST_URI, 1, "v1", NEW_DOM, NEW_SCHEMAS)

        store[TEST_URI].let {
            it!!
            assertEquals(it.document, "v1")
            assertEquals(it.dom, NEW_DOM)
            assertSame(it.analysisSchemas, NEW_SCHEMAS)
        }
    }

    @Test
    fun `higher versions are stored`() {
        store.storeVersioned(TEST_URI, 1, "v2", STORED_DOM, STORED_SCHEMAS)
        store.storeVersioned(TEST_URI, 2, "v1", NEW_DOM, NEW_SCHEMAS)

        store[TEST_URI].let {
            it!!
            assertEquals(it.document, "v1")
            assertEquals(it.dom, NEW_DOM)
            assertSame(it.analysisSchemas, NEW_SCHEMAS)
        }
    }

    @Test
    fun `version is not replaced if lower`() {
        store.storeVersioned(TEST_URI, 2, "v2", STORED_DOM, STORED_SCHEMAS)
        store.storeVersioned(TEST_URI, 1, "v1", NEW_DOM, NEW_SCHEMAS)

        store[TEST_URI].let {
            it!!
            assertEquals(it.document, "v2")
            assertEquals(it.dom, STORED_DOM)
            assertSame(it.analysisSchemas, STORED_SCHEMAS)
        }
    }

    companion object {
        val TEST_URI = URI.create("file:///test")

        private val STORED_DOM = mockk<DocumentOverlayResult>()
        private val NEW_DOM = mockk<DocumentOverlayResult>()
        private val STORED_SCHEMAS = mockk<List<AnalysisSchema>>()
        private val NEW_SCHEMAS = mockk<List<AnalysisSchema>>()
    }

}

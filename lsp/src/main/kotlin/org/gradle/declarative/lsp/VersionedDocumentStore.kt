/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Class holding versioned documents.
 *
 * The LSP protocol allows for versioned documents, whereas the document is changed, a new, higher version is sent.
 * This class helps storing and retrieving these versioned documents.
 */
class VersionedDocumentStore {

    private val store = mutableMapOf<URI, DocumentStoreEntry>()

    operator fun get(uri: URI): DocumentOverlayResult? {
        return store[uri]?.document
    }

    fun storeInitial(uri: URI, document: DocumentOverlayResult) {
        store(uri, DocumentStoreEntry.Initial(document))
    }

    fun storeVersioned(uri: URI, version: Int, document: DocumentOverlayResult) {
        store(uri, DocumentStoreEntry.Versioned(version, document))
    }

    /**
     * Stores a versioned document.
     * If the document is already stored, the version must be greater than the stored version.
     *
     * @return `true` if the document was stored, `false` otherwise.
     */
    private fun store(uri: URI, entry: DocumentStoreEntry): Boolean {
        val storeEntry = store[uri]
        if (storeEntry is DocumentStoreEntry.Versioned && entry is DocumentStoreEntry.Versioned) {
            // If both are versioned, we only store if the new version is greater
            if (entry.version > storeEntry.version) {
                store[uri] = entry
                return true
            }
        } else {
            // In any other case (e.g. not stored value, value is re-initialized), we store the new value
            store[uri] = entry
            return true
        }

        // If we reach this point, the document was not stored
        return false
    }

    fun remove(uri: URI) {
        store.remove(uri)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeclarativeTextDocumentService::class.java)
    }

    sealed class DocumentStoreEntry {
        abstract val document: DocumentOverlayResult

        data class Initial(override val document: DocumentOverlayResult) : DocumentStoreEntry()
        data class Versioned(val version: Int, override val document: DocumentOverlayResult) : DocumentStoreEntry()
    }
}
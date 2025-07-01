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

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.lsp.extension.unionAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import java.net.URI

/**
 * Class holding versioned documents.
 *
 * The LSP protocol allows for versioned documents, whereas the document is changed, a new, higher version is sent.
 * This class helps storing and retrieving these versioned documents.
 */
class VersionedDocumentStore {

    private val store = mutableMapOf<URI, DocumentStoreEntry>()

    operator fun get(uri: URI): DocumentStoreEntry? {
        return store[uri]
    }

    fun storeInitial(uri: URI, document: String, dom: DocumentOverlayResult, analysisSchemas: List<AnalysisSchema>): DocumentStoreEntry? =
        store(uri, DocumentStoreEntry.Initial(document, dom, analysisSchemas))

    fun storeVersioned(
        uri: URI,
        version: Int,
        document: String,
        dom: DocumentOverlayResult,
        analysisSchemas: List<AnalysisSchema>
    ): DocumentStoreEntry? = store(uri, DocumentStoreEntry.Versioned(version, document, dom, analysisSchemas))

    /**
     * Stores a versioned document.
     * If the document is already stored, the version must be greater than the stored version.
     *
     * @return `true` if the document was stored, `false` otherwise.
     */
    private fun store(uri: URI, entry: DocumentStoreEntry): DocumentStoreEntry? {
        val storeEntry = store[uri]
        if (storeEntry is DocumentStoreEntry.Versioned && entry is DocumentStoreEntry.Versioned) {
            // If both are versioned, we only store if the new version is greater
            if (entry.version > storeEntry.version) {
                store[uri] = entry
                return entry
            }
        } else {
            // In any other case (e.g. not stored value, value is re-initialized), we store the new value
            store[uri] = entry
            return entry
        }

        // If we reach this point, the document was not stored
        return null
    }

    fun remove(uri: URI) {
        store.remove(uri)
    }

    sealed class DocumentStoreEntry {

        abstract val document: String
        abstract val dom: DocumentOverlayResult
        abstract val analysisSchemas: List<AnalysisSchema>

        val unionSchema by lazy { 
            unionAnalysisSchema(analysisSchemas)
        }

        val typeRefContext by lazy {
            SchemaTypeRefContext(unionSchema)
        }

        // Component 1
        operator fun component1(): String = document
        operator fun component2(): DocumentOverlayResult = dom

        class Initial(
            override val document: String,
            override val dom: DocumentOverlayResult,
            override val analysisSchemas: List<AnalysisSchema>
        ) : DocumentStoreEntry()

        class Versioned(
            val version: Int,
            override val document: String,
            override val dom: DocumentOverlayResult,
            override val analysisSchemas: List<AnalysisSchema>
        ) : DocumentStoreEntry()
    }
}
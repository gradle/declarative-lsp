package org.gradle.declarative.lsp.storage

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import java.net.URI

class VersionedDocumentStore {

    private val store = mutableMapOf<URI, DocumentStoreEntry>()

    operator fun get(uri: URI): DeclarativeDocument? {
        return store[uri]?.document
    }

    fun storeInitial(uri: URI, document: DeclarativeDocument) {
        store(uri, DocumentStoreEntry.Initial(document))
    }

    fun storeVersioned(uri: URI, version: Int, document: DeclarativeDocument) {
        store(uri, DocumentStoreEntry.Versioned(version, document))
    }

    /**
     * Stores a versioned document.
     * If the document is already stored, the version must be greater than the stored version.
     *
     * @return `true` if the document was stored, `false` otherwise.
     */
    private fun store(uri: URI, entry: DocumentStoreEntry) {
        return when (val storedEntry = store[uri]) {
            null -> store[uri] = entry
            is DocumentStoreEntry.Initial -> {
                when (entry) {
                    is DocumentStoreEntry.Initial -> throw IllegalArgumentException("Cannot store an initial document when an initial document is already stored.")
                    is DocumentStoreEntry.Versioned -> store[uri] = entry
                }
            }

            is DocumentStoreEntry.Versioned -> {
                when (entry) {
                    is DocumentStoreEntry.Initial -> throw IllegalArgumentException("Cannot store an initial document when a versioned document is already stored.")
                    is DocumentStoreEntry.Versioned -> {
                        if (storedEntry.version >= entry.version) {
                            store[uri] = entry
                        } else {
                            throw IllegalArgumentException("Cannot store a versioned document with a version less than the stored version.")
                        }
                    }
                }
            }
        }
    }
}

sealed class DocumentStoreEntry {
    abstract val document: DeclarativeDocument

    data class Initial(override val document: DeclarativeDocument) : DocumentStoreEntry()
    data class Versioned(val version: Int, override val document: DeclarativeDocument) : DocumentStoreEntry()
}
package org.gradle.declarative.lsp.storage

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
}

sealed class DocumentStoreEntry {
    abstract val document: DocumentOverlayResult

    data class Initial(override val document: DocumentOverlayResult) : DocumentStoreEntry()
    data class Versioned(val version: Int, override val document: DocumentOverlayResult) : DocumentStoreEntry()
}
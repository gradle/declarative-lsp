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

package org.gradle.declarative.lsp.extension

import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.UnresolvedBase
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlay.overlayResolvedDocuments
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.dom.resolution.DocumentWithResolution
import org.gradle.internal.declarativedsl.language.SourceData
import org.gradle.internal.declarativedsl.language.SyntheticallyProduced

/**
 * Utilities copied from `gradle-client`. 
 * TODO: expose some of them, or their replacements, in the Gradle DCL libs.
 */

/**
 * Packs multiple instances of the same document with different resolution results into a [DocumentOverlayResult] in a
 * way that they appear as a single document (all in the overlay, no underlay part). The resolution results get merged
 * so that if any of the resolution results container has [UnresolvedBase] for a part of the document, it is checked
 * against the other resolution result containers.
 */
internal fun indexBasedOverlayResultFromDocuments(docs: List<DocumentWithResolution>): DocumentOverlayResult {
    val emptyDoc = DocumentWithResolution(
        object : DeclarativeDocument {
            override val content: List<DeclarativeDocument.DocumentNode> = emptyList()
            override val sourceData: SourceData = SyntheticallyProduced
        },
        indexBasedMultiResolutionContainer(emptyList())
    )

    val lastDocWithAllResolutionResults = DocumentWithResolution(
        docs.last().document,
        indexBasedMultiResolutionContainer(docs)
    )

    /**
     * NB: No real overlay origin data is going to be present, as we are overlaying the doc with all the resolution
     * results collected over the empty document.
     */
    return overlayResolvedDocuments(emptyDoc, lastDocWithAllResolutionResults)
}

/**
 * A resolution results container collected from multiple resolved instances of the same document (or multiple
 * different instances of the same document, no referential equality required).
 *
 * The document parts are matched based on indices.
 *
 * If any of the [docs] is different from the others, the result is undefined (likely to be a broken container).
 */
internal fun indexBasedMultiResolutionContainer(docs: List<DocumentWithResolution>): DocumentResolutionContainer {
    val indicesMaps: Map<DocumentWithResolution, Map<IntRange, DeclarativeDocument.Node>> = docs.associateWith {
        buildMap {
            fun visitValue(valueNode: DeclarativeDocument.ValueNode) {
                put(valueNode.sourceData.indexRange, valueNode)
                when (valueNode) {
                    is DeclarativeDocument.ValueNode.ValueFactoryNode -> valueNode.values.forEach(::visitValue)
                    is DeclarativeDocument.ValueNode.LiteralValueNode,
                    is DeclarativeDocument.ValueNode.NamedReferenceNode -> Unit
                }
            }

            fun visitDocumentNode(documentNode: DeclarativeDocument.DocumentNode) {
                put(documentNode.sourceData.indexRange, documentNode)
                when (documentNode) {
                    is DeclarativeDocument.DocumentNode.ElementNode -> {
                        documentNode.elementValues.forEach(::visitValue)
                        documentNode.content.forEach(::visitDocumentNode)
                    }

                    is DeclarativeDocument.DocumentNode.PropertyNode -> visitValue(documentNode.value)
                    is DeclarativeDocument.DocumentNode.ErrorNode -> Unit
                }
            }

            it.document.content.forEach(::visitDocumentNode)
        }
    }

    /**
     * The resolution containers work with node identities.
     * Querying resolution results using nodes from a different document is prohibited.
     * Given that all documents are the same, we can map the node indices and use them to find matching nodes in
     * the documents that we are merging.
     */
    return object : DocumentResolutionContainer {
        inline fun <reified N : DeclarativeDocument.Node, reified T> retryOverContainers(
            node: N,
            noinline get: DocumentResolutionContainer.(N) -> T
        ) = docs.map { doc ->
            val matchingNode = indicesMaps.getValue(doc)[node.sourceData.indexRange]
                ?: error("index not found in index map")
            get(doc.resolutionContainer, matchingNode as N)
        }.let { results ->
            results.firstOrNull {
                it !is DocumentResolution.UnsuccessfulResolution || !it.reasons.contains(UnresolvedBase)
            } ?: results.first()
        }

        override fun data(node: DeclarativeDocument.DocumentNode.ElementNode) = retryOverContainers(node) { data(it) }
        override fun data(node: DeclarativeDocument.DocumentNode.ErrorNode) = retryOverContainers(node) { data(it) }
        override fun data(node: DeclarativeDocument.DocumentNode.PropertyNode) = retryOverContainers(node) { data(it) }
        override fun data(node: DeclarativeDocument.ValueNode.LiteralValueNode) = retryOverContainers(node) { data(it) }
        override fun data(node: DeclarativeDocument.ValueNode.NamedReferenceNode) =
            retryOverContainers(node) { data(it) }

        override fun data(node: DeclarativeDocument.ValueNode.ValueFactoryNode) = retryOverContainers(node) { data(it) }
    }
}

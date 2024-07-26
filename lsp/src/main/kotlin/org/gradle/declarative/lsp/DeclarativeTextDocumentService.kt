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

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.gradle.declarative.lsp.build.model.ResolvedDeclarativeResourcesModel
import org.gradle.declarative.lsp.storage.VersionedDocumentStore
import org.gradle.declarative.lsp.visitor.LocationMatchingVisitor
import org.gradle.declarative.lsp.visitor.visit
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.mutation.MutationApplicability
import org.gradle.internal.declarativedsl.dom.mutation.MutationApplicabilityChecker
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

class DeclarativeTextDocumentService : TextDocumentService, LanguageClientAware {
    private val documentStore = VersionedDocumentStore()
    private val availableMutations = listOf(
        AddDependency()
    )

    private lateinit var client: LanguageClient
    private lateinit var resources: ResolvedDeclarativeResourcesModel
    private lateinit var schemaEvaluator: SimpleAnalysisEvaluator
    private var applicableMutations: List<MutationApplicability> = emptyList()

    override fun connect(client: LanguageClient?) {
        this.client = client!!
    }

    // LSP Functions ----------------------------------------------------------

    fun setResources(resources: ResolvedDeclarativeResourcesModel) {
        this.resources = resources
        schemaEvaluator = SimpleAnalysisEvaluator.withSchema(
            resources.settingsInterpretationSequence, resources.projectInterpretationSequence
        )
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        params?.let {
            LOGGER.trace("Opened document: {}", it.textDocument.uri)

            val uri = URI(it.textDocument.uri)
            val text = File(uri).readText()
            documentStore.storeInitial(uri, parse(uri, text))
            processDocument(uri)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        params?.let {
            LOGGER.trace("Changed document: ${params.textDocument.uri}")
            val uri = URI(it.textDocument.uri)
            it.contentChanges.forEach { change ->
                documentStore.storeVersioned(
                    uri,
                    it.textDocument.version,
                    parse(uri, change.text)
                )
                processDocument(uri)
            }
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        params?.let {
            val uri = URI(it.textDocument.uri)
            documentStore.remove(uri)
        }
        LOGGER.trace("Closed document: ${params?.textDocument?.uri}")
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
        LOGGER.trace("Saved document: ${params?.textDocument?.uri}")
    }

    override fun hover(params: HoverParams?): CompletableFuture<Hover> {
        val hover = params?.let {
            val uri = URI(it.textDocument.uri)
            withDom(uri) { dom ->
                val position = it.position

                // LSPs are supplying 0-based line and column numbers, while the DSL model is 1-based
                val visitor = LocationMatchingVisitor(position.line + 1, position.character + 1)
                dom.document.visit(visitor)

                visitor.matchingNode?.let { node ->
                    when (node) {
                        is DeclarativeDocument.DocumentNode.PropertyNode -> node.name
                        is DeclarativeDocument.DocumentNode.ElementNode -> node.name
                        else -> null
                    }?.let { name ->
                        // We need to convert back the 1-based locations to 0-based ones
                        val startPosition = Position(
                            node.sourceData.lineRange.first - 1,
                            node.sourceData.startColumn - 1
                        )
                        // End position is tricky, as...
                        val endPosition = Position(
                            node.sourceData.lineRange.last - 1,
                            // ... the end column is exclusive, so we DON'T need to subtract 1
                            node.sourceData.endColumn
                        )

                        Hover(
                            MarkupContent("plaintext", name),
                            Range(startPosition, endPosition)
                        )
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(hover)
    }

    override fun codeAction(params: CodeActionParams?): CompletableFuture<MutableList<Either<Command, CodeAction>>> {
        params?.let {
            // TODO: finish this
        }

        return CompletableFuture.completedFuture(null)
    }

    // Common processing -----------------------------------------------------------------------------------------------

    private fun processDocument(uri: URI) = withDom(uri) { dom ->
        applicableMutations = processMutationApplications(dom)
    }

    private fun processMutationApplications(dom: DocumentOverlayResult): List<MutationApplicability> {
        val mac = MutationApplicabilityChecker(resources.analysisSchema, dom.result)
        return availableMutations.flatMap { mutation ->
            mac.checkApplicability(mutation)
        }
    }

    // Utility and other member functions ------------------------------------------------------------------------------

    private fun parse(uri: URI, text: String): DocumentOverlayResult {
        val fileName = uri.path.substringAfterLast('/')
        val fileSchema = schemaEvaluator.evaluate(fileName, text)
        val settingsSchema = schemaEvaluator.evaluate(
            resources.settingsFile.name, resources.settingsFile.readText()
        )

        LOGGER.trace("Parsed declarative model for document: {}", uri)
        val document = AnalysisDocumentUtils.documentWithModelDefaults(settingsSchema, fileSchema)
        return document!!
    }

    private fun <T> withDom(uri: URI, work: (DocumentOverlayResult) -> T): T? {
        return documentStore[uri]?.let { document ->
            work(document)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeclarativeTextDocumentService::class.java)
    }
}

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

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.gradle.declarative.lsp.build.model.ResolvedDeclarativeResourcesModel
import org.gradle.declarative.lsp.visitor.ErrorToDiagnosticVisitor
import org.gradle.declarative.lsp.visitor.LocationMatchingVisitor
import org.gradle.declarative.lsp.visitor.visit
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

class DeclarativeTextDocumentService : TextDocumentService, LanguageClientAware {
    private val documentStore = VersionedDocumentStore()

    private lateinit var client: LanguageClient
    private lateinit var resources: ResolvedDeclarativeResourcesModel
    private lateinit var schemaEvaluator: SimpleAnalysisEvaluator

    override fun connect(client: LanguageClient?) {
        this.client = client!!
    }

    // LSP Functions ---------------------------------------------------------------------------------------------------

    fun setResources(resources: ResolvedDeclarativeResourcesModel) {
        this.resources = resources
        schemaEvaluator = SimpleAnalysisEvaluator.withSchema(
            resources.settingsInterpretationSequence, resources.projectInterpretationSequence
        )
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        LOGGER.trace("Opened document: {}", params)
        params?.let {
            val uri = URI(it.textDocument.uri)
            val text = File(uri).readText()
            val document = parse(uri, text)
            documentStore.storeInitial(uri, document)

            processDocument(uri)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        LOGGER.trace("Changed document: {}", params)
        params?.let {
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
        LOGGER.trace("Closed document: {}", params)
        params?.let {
            val uri = URI(it.textDocument.uri)
            documentStore.remove(uri)
        }
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
        LOGGER.trace("Saved document: {}", params)
    }

    override fun hover(params: HoverParams?): CompletableFuture<Hover> {
        LOGGER.trace("Hover requested for position: {}", params)
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
                        Hover(
                            MarkupContent("plaintext", name),
                            node.sourceData.toLspRange()
                        )
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(hover)
    }


    // Common processing -----------------------------------------------------------------------------------------------

    private fun processDocument(uri: URI) = withDom(uri) { dom ->
        processErrors(uri, dom)
    }

    private fun processErrors(uri: URI, dom: DocumentOverlayResult) {
        val diagnostics = dom.document.visit(ErrorToDiagnosticVisitor()).diagnostics
        LOGGER.trace("Publishing diagnostics for document: {}", uri)
        client.publishDiagnostics(
            PublishDiagnosticsParams(
                uri.toString(),
                // Can be empty, which means that we have no errors
                diagnostics
            )
        )
    }

    // Utility and other member functions ------------------------------------------------------------------------------

    private fun parse(uri: URI, text: String): DocumentOverlayResult {
        val fileName = uri.path.substringAfterLast('/')
        val fileSchema = schemaEvaluator.evaluate(fileName, text)
        val settingsSchema = schemaEvaluator.evaluate(
            resources.settingsFile.name, resources.settingsFile.readText()
        )

        val document = AnalysisDocumentUtils.documentWithModelDefaults(settingsSchema, fileSchema)
        when (document != null) {
            true -> LOGGER.trace("Parsed declarative model for document: {}", uri)
            false -> LOGGER.error("Failed to parse declarative model for document: {}", uri)
        }

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

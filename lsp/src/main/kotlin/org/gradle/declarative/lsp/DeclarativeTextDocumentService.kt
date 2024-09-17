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

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemLabelDetails
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.lsp.build.model.ResolvedDeclarativeResourcesModel
import org.gradle.declarative.lsp.visitor.BestFittingNodeVisitor
import org.gradle.declarative.lsp.visitor.SemanticErrorToDiagnosticVisitor
import org.gradle.declarative.lsp.visitor.SyntaxErrorToDiagnosticVisitor
import org.gradle.declarative.lsp.visitor.visit
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
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
                val visitor = BestFittingNodeVisitor(
                    params.position,
                    DeclarativeDocument.DocumentNode::class
                )
                dom.document.visit(visitor).matchingNode?.let { node ->
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

    override fun completion(params: CompletionParams?): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        // TODO: Look at SchemaFunction.parameters

        LOGGER.trace("Completion requested for position: {}", params)
        val completions = params?.let {
            val uri = URI(it.textDocument.uri)
            withDom(uri) { dom ->
                val schemaTypeRefContext = SchemaTypeRefContext(resources.analysisSchema)
                dom.document.visit(
                    BestFittingNodeVisitor(
                        params.position,
                        DeclarativeDocument.DocumentNode.ElementNode::class
                    )
                ).matchingNode?.withDataClass(dom.overlayResolutionContainer) { dataClass ->
                    computePropertyCompletions(dataClass) + computeFunctionCompletions(dataClass)
                }.orEmpty()
            }
        }.orEmpty().toMutableList()
        return CompletableFuture.completedFuture(Either.forLeft(completions))
    }

    private fun computePropertyCompletions(
        dataClass: DataClass
    ): List<CompletionItem> =
        dataClass.properties.map { property ->
            CompletionItem(property.name).apply {
                kind = CompletionItemKind.Field
                insertTextFormat = InsertTextFormat.Snippet
                insertText = "${property.name} = $0"
            }
        }

    private fun computeFunctionCompletions(dataClass: DataClass): List<CompletionItem> =
        dataClass.memberFunctions.map { function ->
            // Anatomy of a DCL completion item:
            // When the completion is requested, the following will (probably, based on the editor) show up:
            //
            // foo(bar: Baz) { } (optional configuration)
            // |-| CompletionItem.label
            //    |-------------------------------------| CompletionItem.labelDetails.detail
            CompletionItem(function.simpleName).apply {
                kind = CompletionItemKind.Method
                insertTextFormat = InsertTextFormat.Snippet
                insertText = function.toInsertText()
                labelDetails = CompletionItemLabelDetails().apply {
                    this.detail = listOfNotNull(
                        function.toParameterLabel(),
                        function.toConfigurabilityLabel()
                    ).joinToString(" ")
                }
            }
        }

    // Utility and other member functions ------------------------------------------------------------------------------

    private fun processDocument(uri: URI) = withDom(uri) { dom ->
        reportSyntaxErrors(uri, dom)
        reportSemanticErrors(uri, dom)
    }

    /**
     * Publishes the syntax errors (or the lack thereof) for the given document as LSP diagnostics.
     */
    private fun reportSyntaxErrors(uri: URI, dom: DocumentOverlayResult) {
        val diagnostics = dom.document.visit(SyntaxErrorToDiagnosticVisitor()).diagnostics
        if (diagnostics.isNotEmpty()) {
            LOGGER.trace("Found syntax errors in document {}: {}", uri, diagnostics)
        }
        client.publishDiagnostics(
            PublishDiagnosticsParams(
                uri.toString(),
                // Can be empty, which means to the client that there are no diagnostics (i.e. errors, warnings, etc.)
                // Otherwise, the client won't clear the previously sent diagnostic
                diagnostics
            )
        )
    }

    /**
     * Publishes the semantic errors (or the lack thereof) for the given document as LSP diagnostics.
     */
    private fun reportSemanticErrors(uri: URI, dom: DocumentOverlayResult) {
        val diagnostics =
            dom.document.visit(SemanticErrorToDiagnosticVisitor(dom.overlayResolutionContainer)).diagnostics
        if (diagnostics.isNotEmpty()) {
            LOGGER.trace("Found semantic errors in document {}: {}", uri, diagnostics)
        }
        client.publishDiagnostics(
            PublishDiagnosticsParams(
                uri.toString(),
                // Can be empty, which means to the client that there are no diagnostics (i.e. errors, warnings, etc.)
                // Otherwise, the client won't clear the previously sent diagnostic
                diagnostics
            )
        )
    }

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

private fun SchemaFunction.toInsertText(): String = when (val semantics = this.semantics) {
    is FunctionSemantics.ConfigureSemantics -> when (semantics.configureBlockRequirement) {
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Required -> "${this.simpleName} {\n\t$0\n}"
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Optional -> "${this.simpleName} {\n\t$0\n}"
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.NotAllowed -> "${this.simpleName}(${this.toParameterSnippet()})$0"
    }

    else -> "${this.simpleName}()"
}

private fun SchemaFunction.toParameterSnippet(): String =
    this.parameters.mapIndexed() { index, parameter ->
        // Snippet parameter placeholders are 1-based
        "\${${index + 1}:${parameter.name}}"
    }.joinToString(", ")

private fun SchemaFunction.toParameterLabel(): String? {
    val parameterNames = this.parameters.joinToString(", ") { parameter ->
        "${parameter.name}: ${parameter.type}"
    }
    return if (parameterNames.isNotEmpty()) "($parameterNames)" else null
}

private fun SchemaFunction.toConfigurabilityLabel(): String? = when (val semantics = this.semantics) {
    is FunctionSemantics.ConfigureSemantics -> when (semantics.configureBlockRequirement) {
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Required -> " { }"
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Optional -> " { } (optional configuration)"
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.NotAllowed -> null
    }

    else -> null
}

/**
 * Calls the specified function block if the element resolution was successful, and the element type is a data class.
 */
private fun <N : DeclarativeDocument.DocumentNode, R> N.withDataClass(
    resolutionContainer: DocumentResolutionContainer,
    function: (DataClass) -> R
): R? = when (val nodeType = resolutionContainer.data(this)) {
    is DocumentResolution.ElementResolution.SuccessfulElementResolution -> {
        when (val elementType = nodeType.elementType) {
            is DataClass -> function(elementType)
            else -> null
        }
    }

    else -> null
}

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
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.InsertTextMode
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel
import org.gradle.declarative.lsp.extension.toLspRange
import org.gradle.declarative.lsp.service.MutationRegistry
import org.gradle.declarative.lsp.service.VersionedDocumentStore
import org.gradle.declarative.lsp.visitor.BestFittingNodeVisitor
import org.gradle.declarative.lsp.visitor.SemanticErrorToDiagnosticVisitor
import org.gradle.declarative.lsp.visitor.SyntaxErrorToDiagnosticVisitor
import org.gradle.declarative.lsp.visitor.visit
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameterKind
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.CompletableFuture

private val LOGGER = LoggerFactory.getLogger(DeclarativeTextDocumentService::class.java)

class DeclarativeTextDocumentService : TextDocumentService {

    private lateinit var client: LanguageClient
    private lateinit var documentStore: VersionedDocumentStore
    private lateinit var mutationRegistry: MutationRegistry
    private lateinit var declarativeResources: DeclarativeResourcesModel
    private lateinit var analysisSchema: AnalysisSchema
    private lateinit var schemaAnalysisEvaluator: SimpleAnalysisEvaluator

    fun initialize(
        client: LanguageClient,
        documentStore: VersionedDocumentStore,
        mutationRegistry: MutationRegistry,
        declarativeResources: DeclarativeResourcesModel
    ) {
        this.client = client
        this.documentStore = documentStore
        this.mutationRegistry = mutationRegistry
        this.declarativeResources = declarativeResources

        this.analysisSchema = declarativeResources.analysisSchema
        this.schemaAnalysisEvaluator = SimpleAnalysisEvaluator.withSchema(
            declarativeResources.settingsInterpretationSequence,
            declarativeResources.projectInterpretationSequence
        )
    }

    // LSP Functions ---------------------------------------------------------------------------------------------------

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        LOGGER.trace("Opened document: {}", params)
        params?.let {
            val uri = URI(it.textDocument.uri)
            val text = it.textDocument.text
            val dom = parse(uri, text)
            if (dom != null) {
                documentStore.storeInitial(uri, text, dom)
                processDocument(uri)
            } else {
                documentStore.remove(uri)
            }
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        LOGGER.trace("Changed document: {}", params)
        params?.let {
            val uri = URI(it.textDocument.uri)
            it.contentChanges.forEach { change ->
                val version = it.textDocument.version
                val text = change.text
                val dom = parse(uri, change.text)
                if (dom != null) {
                    documentStore.storeVersioned(uri, version, text, dom)
                    processDocument(uri)
                } else {
                    documentStore.remove(uri)
                }
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
            withDom(uri) { dom, _ ->
                // LSPs are supplying 0-based line and column numbers, while the DSL model is 1-based
                val visitor = BestFittingNodeVisitor(
                    params.position,
                    DeclarativeDocument.DocumentNode::class
                )
                dom.document.visit(visitor).bestFittingNode?.let { node ->
                    when (node) {
                        is DeclarativeDocument.DocumentNode.PropertyNode -> node.name + " (property)"
                        is DeclarativeDocument.DocumentNode.ElementNode -> node.name + " (element)"
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
        LOGGER.trace("Completion requested for position: {}", params)
        val completions = params?.let {
            val uri = URI(it.textDocument.uri)
            withDom(uri) { dom, _ ->
                dom.document.visit(
                    BestFittingNodeVisitor(
                        params.position,
                        DeclarativeDocument.DocumentNode.ElementNode::class
                    )
                ).bestFittingNode
                    ?.getDataClass(dom.overlayResolutionContainer)
                    ?.let { dataClass ->
                        computePropertyCompletions(dataClass, analysisSchema) +
                                computeFunctionCompletions(dataClass, analysisSchema)
                    }
            }
        }.orEmpty().toMutableList()
        return CompletableFuture.completedFuture(Either.forLeft(completions))
    }

    override fun signatureHelp(params: SignatureHelpParams?): CompletableFuture<SignatureHelp> {
        LOGGER.trace("Signature help requested for position: {}", params)

        val signatureInformationList = params?.let {
            val uri = URI(it.textDocument.uri)
            withDom(uri) { dom, _ ->
                val position = it.position
                val matchingNodes = dom.document.visit(
                    BestFittingNodeVisitor(
                        position,
                        DeclarativeDocument.DocumentNode.ElementNode::class
                    )
                ).containers

                val targetNode = matchingNodes[matchingNodes.size - 1]
                val containerNode = matchingNodes[matchingNodes.size - 2]

                containerNode.getDataClass(dom.overlayResolutionContainer)?.memberFunctions?.filter { function ->
                    function.simpleName == targetNode.name
                }?.map { function ->
                    SignatureInformation(function.toSignatureLabel()).apply {
                        parameters = function.parameters.map { parameter ->
                            ParameterInformation(parameter.toSignatureLabel())
                        }
                    }
                }
            }
        }.orEmpty()

        return CompletableFuture.completedFuture(
            SignatureHelp(signatureInformationList, null, null)
        )
    }

    override fun codeAction(params: CodeActionParams?): CompletableFuture<MutableList<Either<Command, CodeAction>>> {
        LOGGER.trace("Code action requested: {}", params)
        val mutations: List<Either<Command, CodeAction>> = params?.let {
            val uri = URI(it.textDocument.uri)
            mutationRegistry.getApplicableMutations(uri, params.range).map { mutation ->
                Command(
                    mutation.name,
                    "gradle-dcl.applyMutation",
                    listOf(
                        mapOf(
                            "documentUri" to it.textDocument.uri,
                            "mutationId" to mutation.id,
                            "mutationParameters" to mutation.parameters.map { parameter ->
                                mapOf(
                                    "name" to parameter.name,
                                    "description" to parameter.description,
                                    "type" to when (parameter.kind) {
                                        MutationParameterKind.BooleanParameter -> "boolean"
                                        MutationParameterKind.IntParameter -> "int"
                                        MutationParameterKind.StringParameter -> "string"
                                    }
                                )
                            }
                        )
                    )
                )
            }
        }.orEmpty().map {
            Either.forLeft(it)
        }

        return CompletableFuture.completedFuture(mutations.toMutableList())
    }

    // Utility and other member functions ------------------------------------------------------------------------------

    private fun processDocument(uri: URI) = withDom(uri) { dom, _ ->
        reportSyntaxErrors(uri, dom)
        reportSemanticErrors(uri, dom)
        mutationRegistry.registerDocument(uri, dom.result)
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

    private fun parse(uri: URI, text: String): DocumentOverlayResult? {
        val fileName = uri.path.substringAfterLast('/')
        val fileSchema = schemaAnalysisEvaluator.evaluate(fileName, text)
        val settingsSchema = schemaAnalysisEvaluator.evaluate(
            declarativeResources.settingsFile.name, declarativeResources.settingsFile.readText()
        )

        val document = AnalysisDocumentUtils.documentWithModelDefaults(settingsSchema, fileSchema)
        when (document != null) {
            true -> LOGGER.trace("Parsed declarative model for document: {}", uri)
            false -> LOGGER.error("Failed to parse declarative model for document: {}", uri)
        }

        return document!!
    }

    private fun <T> withDom(uri: URI, work: (DocumentOverlayResult, String) -> T): T? {
        return documentStore[uri]?.let { entry ->
            work(entry.dom, entry.document)
        }
    }

}

// Pure functions supporting LSP functions -----------------------------------------------------------------------------

private fun computePropertyCompletions(
    dataClass: DataClass,
    analysisSchema: AnalysisSchema
): List<CompletionItem> {
    return dataClass.properties.map { property ->
        val propertyName = property.name
        val targetType = property.valueType.toSimpleName()

        CompletionItem("$propertyName = $targetType").apply {
            kind = CompletionItemKind.Field
            insertTextFormat = InsertTextFormat.Snippet
            insertText = "${property.name} = ${computeTypedPlaceholder(1, property.valueType, analysisSchema)}"
        }
    }
}

private fun computeFunctionCompletions(
    dataClass: DataClass,
    analysisSchema: AnalysisSchema
): List<CompletionItem> =
    dataClass.memberFunctions.map { function ->
        val functionName = function.simpleName
        val parameterSignature = when (function.parameters.isEmpty()) {
            true -> ""
            false -> function.parameters.joinToString(",", "(", ")") { it.toSignatureLabel() }
        }
        val configureBlockLabel = function.semantics.toBlockConfigurabilityLabel().orEmpty()

        CompletionItem("$functionName$parameterSignature$configureBlockLabel").apply {
            kind = CompletionItemKind.Method
            insertTextFormat = InsertTextFormat.Snippet
            insertTextMode = InsertTextMode.AdjustIndentation
            insertText = computeCompletionInsertText(function, analysisSchema)
        }
    }

private fun computeCompletionInsertText(
    function: SchemaFunction,
    analysisSchema: AnalysisSchema
): String {
    val parameterSnippet = function.parameters.mapIndexed { index, parameter ->
        // Additional placeholders are indexed from 1
        computeTypedPlaceholder(index + 1, parameter.type, analysisSchema)
    }.joinToString(", ", "(", ")")

    return when (val semantics = function.semantics) {
        is FunctionSemantics.ConfigureSemantics -> {
            when (semantics.configureBlockRequirement) {
                is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Required -> {
                    if (function.parameters.isEmpty()) {
                        // Mandatory configuration without function parameters can omit the parentheses
                        "${function.simpleName} {\n\t$0\n}"
                    } else {
                        // Otherwise, we need the parentheses with the parameter snippets
                        "${function.simpleName}${parameterSnippet} {\n\t$0\n}"
                    }
                }

                else -> {
                    // Optional configuration can be omitted
                    "${function.simpleName}${parameterSnippet}$0"
                }
            }
        }

        else -> "${function.simpleName}${parameterSnippet}$0"
    }
}

/**
 * Computes a [placeholder](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#placeholders) based on the given data type.
 *
 * If there is a specific placeholder for the given data type, it will be used.
 * Otherwise, a simple indexed will be used
 */
private fun computeTypedPlaceholder(
    index: Int,
    type: DataTypeRef,
    analysisSchema: AnalysisSchema
): String {
    val resolvedType = SchemaTypeRefContext(analysisSchema).resolveRef(type)
    return when (resolvedType) {
        is DataType.BooleanDataType -> "\${$index|true,false|}"
        is DataType.IntDataType -> "\${$index:0}"
        is DataType.LongDataType -> "\${$index:0L}"
        is DataType.StringDataType -> "\"\${$index}\""
        else -> "\$$index"
    }
}

// Extension functions -------------------------------------------------------------------------------------------------

// TODO: this might not be the best way to resolve the type name, but it works for now
private fun DataTypeRef.toSimpleName(): String = this.toString()

private fun DataParameter.toSignatureLabel() = "${this.name}: ${this.type.toSimpleName()}"

private fun FunctionSemantics.toBlockConfigurabilityLabel(): String? = when (this) {
    is FunctionSemantics.ConfigureSemantics -> when (this.configureBlockRequirement) {
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Required -> " { this: ${this.configuredType.toSimpleName()} }"
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Optional -> " { this: ${this.configuredType.toSimpleName()} } (optional configuration)"
        is FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.NotAllowed -> null
    }

    else -> null
}

private fun SchemaFunction.toSignatureLabel(): String {
    val parameterSignatures = this.parameters.joinToString(", ") { parameter ->
        parameter.toSignatureLabel()
    }

    return "${this.simpleName}(${parameterSignatures})"
}

/**
 * Tries to resolve the data class of the given node. If the resolution fails, returns `null`.
 */
private fun <N : DeclarativeDocument.DocumentNode> N.getDataClass(
    resolutionContainer: DocumentResolutionContainer
): DataClass? = when (val nodeType = resolutionContainer.data(this)) {
    is DocumentResolution.ElementResolution.SuccessfulElementResolution -> {
        when (val elementType = nodeType.elementType) {
            is DataClass -> elementType
            else -> null
        }
    }

    else -> null
}
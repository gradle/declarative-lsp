/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.gradle.declarative.dsl.schema.AssignmentAugmentationKind
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.lsp.VersionedDocumentStore.DocumentStoreEntry
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel
import org.gradle.declarative.lsp.extension.indexBasedOverlayResultFromDocuments
import org.gradle.declarative.lsp.extension.schemaAnalysisEvaluator
import org.gradle.declarative.lsp.extension.toLspRange
import org.gradle.declarative.lsp.mutation.MutationRegistry
import org.gradle.declarative.lsp.visitor.BestFittingNodeVisitor
import org.gradle.declarative.lsp.visitor.SemanticErrorToDiagnosticVisitor
import org.gradle.declarative.lsp.visitor.SyntaxErrorToDiagnosticVisitor
import org.gradle.declarative.lsp.visitor.visit
import org.gradle.internal.declarativedsl.analysis.TypeRefContext
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DocumentResolution
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameterKind
import org.gradle.internal.declarativedsl.dom.operations.overlay.DocumentOverlayResult
import org.gradle.internal.declarativedsl.dom.resolution.DocumentResolutionContainer
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils.resolvedDocument
import org.gradle.internal.declarativedsl.evaluator.runner.stepResultOrPartialResult
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.CompletableFuture

private val LOGGER = LoggerFactory.getLogger(DeclarativeTextDocumentService::class.java)

class DeclarativeTextDocumentService : TextDocumentService {

    private lateinit var client: LanguageClient
    private lateinit var documentStore: VersionedDocumentStore
    private lateinit var valueFactoryIndexStore: ValueFactoryIndexStore
    private lateinit var mutationRegistry: MutationRegistry
    private lateinit var declarativeFeatures: DeclarativeFeatures
    private lateinit var declarativeResources: DeclarativeModelStore

    fun initialize(
        client: LanguageClient,
        documentStore: VersionedDocumentStore,
        mutationRegistry: MutationRegistry,
        declarativeFeatures: DeclarativeFeatures,
        declarativeResources: DeclarativeModelStore
    ) {
        this.client = client
        this.declarativeFeatures = declarativeFeatures
        this.documentStore = documentStore
        this.valueFactoryIndexStore = ValueFactoryIndexStore()
        this.mutationRegistry = mutationRegistry
        this.declarativeResources = declarativeResources
    }

    // LSP Functions ---------------------------------------------------------------------------------------------------

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        declarativeResources.ifAvailable { resources ->
            LOGGER.trace("Opened document: {}", params)
            params?.let {
                val uri = URI(it.textDocument.uri)
                val text = it.textDocument.text
                val parsed = parse(uri, text, resources)
                run {
                    documentStore.storeInitial(uri, text, parsed.documentOverlayResult, parsed.analysisSchemas)
                        ?.also { storeEntry -> valueFactoryIndexStore.store(uri, storeEntry) }
                    processDocument(uri)
                }
            }
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        LOGGER.trace("Changed document: {}", params)
        declarativeResources.ifAvailable { resources ->
            params?.let {
                val uri = URI(it.textDocument.uri)
                it.contentChanges.forEach { change ->
                    val version = it.textDocument.version
                    val text = change.text
                    val parsed = parse(uri, change.text, resources)
                    documentStore.storeVersioned(uri, version, text, parsed.documentOverlayResult, parsed.analysisSchemas)
                        ?.also { storeEntry -> valueFactoryIndexStore.store(uri, storeEntry) }
                    processDocument(uri)
                }
            }
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        LOGGER.trace("Closed document: {}", params)
        params?.let {
            val uri = URI(it.textDocument.uri)
            documentStore.remove(uri)
            valueFactoryIndexStore.remove(uri)
        }
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
        LOGGER.trace("Saved document: {}", params)

        params?.let {
            val uri = URI(it.textDocument.uri)
            // If the document is a settings file, we make an attempt updating the model
            if (uri.path.endsWith("settings.gradle") || uri.path.endsWith("settings.gradle.kts")) {
                LOGGER.info("Settings file changed, reloading declarative model")
                declarativeResources.updateModel()
            }
        }
    }

    override fun hover(params: HoverParams?): CompletableFuture<Hover> {
        LOGGER.trace("Hover requested for position: {}", params)
        val hover = params?.let {
            val uri = URI(it.textDocument.uri)
            withDom(uri) { dom, _, _, _, _ ->
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
        val completions = params?.let { param ->
            val uri = URI(param.textDocument.uri)
            withDom(uri) { dom, schema, typeRefContext, valueFactoryIndex, _ ->
                val bestFittingNode = dom.document.visit(
                    BestFittingNodeVisitor(
                        params.position,
                        DeclarativeDocument.DocumentNode.ElementNode::class
                    )
                ).bestFittingNode
                bestFittingNode
                    ?.getDataClass(dom.overlayResolutionContainer)
                    .let { it ?: schema.topLevelReceiverType }
                    .let { dataClass ->
                        computePropertyCompletions(dataClass, typeRefContext) +
                            computePropertyByValueFactoryCompletions(
                                dataClass, schema, typeRefContext, valueFactoryIndex
                            ) +
                            computeFunctionCompletions(dataClass, typeRefContext)
                    }
            }
        }.orEmpty().toMutableList()
        return CompletableFuture.completedFuture(Either.forLeft(completions))
    }

    override fun signatureHelp(params: SignatureHelpParams?): CompletableFuture<SignatureHelp> {
        LOGGER.trace("Signature help requested for position: {}", params)

        val signatureInformationList = params?.let {
            val uri = URI(it.textDocument.uri)
            withDom(uri) { dom, _, typeRefContext, _, _ ->
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
                    SignatureInformation(function.toSignatureLabel(typeRefContext)).apply {
                        parameters = function.parameters.map { parameter ->
                            ParameterInformation(parameter.toSignatureLabel(typeRefContext))
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

        // If the clients do not support mutations, we don't need to provide any code actions
        if (!declarativeFeatures.mutations) {
            return CompletableFuture.completedFuture(mutableListOf())
        }

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

    private fun processDocument(uri: URI) = withDom(uri) { dom, schema, _, _, _ ->
        reportSyntaxErrors(uri, dom)
        reportSemanticErrors(uri, dom)
        mutationRegistry.registerDocument(uri, schema, dom.result)
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

    data class ParsedDocument(
        val documentOverlayResult: DocumentOverlayResult,
        val analysisSchemas: List<AnalysisSchema>
    )

    private fun parse(uri: URI, text: String, resourcesModel: DeclarativeResourcesModel): ParsedDocument {
        val fileName = uri.path.substringAfterLast('/')
        val analysisResult = resourcesModel.schemaAnalysisEvaluator().evaluate(fileName, text)

        // Workaround: for now, the mutation utilities cannot handle mutations that touch the underlay document content.
        // To avoid that, use the utility that produces an overlay result with no real underlay content.
        // This utility also takes care of multi-step resolution results and merges them, presenting .
        // TODO: carry both the real overlay and the document produced from just the current file, run the mutations
        //       against the latter for now.
        // TODO: once the mutation utilities start handling mutations across the overlay, pass them the right overlay.
        val overlay = indexBasedOverlayResultFromDocuments(
            analysisResult.stepResults.map { it.value.stepResultOrPartialResult.resolvedDocument() }
        )

        LOGGER.trace("Parsed declarative model for document: {}", uri)

        return ParsedDocument(
            overlay,
            analysisResult.stepResults.map { it.key.evaluationSchemaForStep.analysisSchema }
        )
    }

    private fun <T> withDom(
        uri: URI,
        work: (DocumentOverlayResult, AnalysisSchema, TypeRefContext, ValueFactoryIndex, String) -> T
    ): T? {
        return documentStore[uri]?.let { entry ->
            work(entry.dom, entry.unionSchema, entry.typeRefContext, valueFactoryIndexStore[uri]!!, entry.document)
        }
    }

}

// Pure functions supporting LSP functions -----------------------------------------------------------------------------

private fun computePropertyCompletions(
    dataClass: DataClass,
    typeRefContext: TypeRefContext
): List<CompletionItem> {
    return dataClass.properties.mapNotNull { property ->
        when (val resolvedType = typeRefContext.resolveRef(property.valueType)) {
            is EnumClass -> completionItem(property, resolvedType)
            is DataType.BooleanDataType -> completionItem(property, resolvedType)
            is DataType.IntDataType -> completionItem(property, resolvedType)
            is DataType.LongDataType -> completionItem(property, resolvedType)
            is DataType.StringDataType -> completionItem(property, resolvedType)
            else -> null
        }
    }
}

private fun completionItem(property: DataProperty, resolvedType: DataType) =
    CompletionItem("${property.name} = ${property.valueType.toSimpleName()}").apply {
        kind = CompletionItemKind.Field
        insertTextFormat = InsertTextFormat.Snippet
        insertText = "${property.name} = ${computeTypedPlaceholder(1, resolvedType)}"
    }

private fun computePropertyByValueFactoryCompletions(
    dataClass: DataClass,
    schema: AnalysisSchema,
    typeRefContext: TypeRefContext,
    valueFactoryIndex: ValueFactoryIndex,
): List<CompletionItem> {
    return dataClass.properties.flatMap { property ->
        val propertiesType = typeRefContext.resolveRef(property.valueType)
        if (propertiesType is DataType.ClassDataType) {
            val factoriesForProperty = valueFactoryIndex.factoriesForProperty(propertiesType.name)
            factoriesForProperty
                ?.flatMap { indexEntry ->
                    val functionReturnType = typeRefContext.resolveRef(indexEntry.function.returnValueType)
                    val genericTypeSubstitution =
                        typeRefContext.computeGenericTypeSubstitutionIfAssignable(propertiesType, functionReturnType)
                    genericTypeSubstitution?.let { typeSubstitution ->
                        val completionLabel =
                            indexEntry.function.computeCompletionLabel(typeRefContext, typeSubstitution)
                        val insertionText = indexEntry.function.computeCompletionInsertText(typeRefContext)

                        fun propertyCompletionItem(withPlus: Boolean): CompletionItem {
                            val operator = if (withPlus) "+=" else "="

                            return CompletionItem(
                                "${property.name} $operator ${indexEntry.namePrefix}$completionLabel"
                            ).apply {
                                kind = if (withPlus) CompletionItemKind.Method else CompletionItemKind.Field
                                insertTextFormat = InsertTextFormat.Snippet
                                insertText = "${property.name} $operator ${indexEntry.namePrefix}$insertionText"
                            }
                        }

                        val hasPlusEqualsAugmentation = schema.assignmentAugmentationsByTypeName[propertiesType.name]
                            .orEmpty()
                            .any { it.kind is AssignmentAugmentationKind.Plus }

                        listOfNotNull(
                            propertyCompletionItem(false),
                            if (hasPlusEqualsAugmentation) {
                                propertyCompletionItem(true)
                            } else null
                        )
                    }.orEmpty()
                } ?: emptyList()
        } else {
            emptyList()
        }
    }
}

private fun computeFunctionCompletions(
    dataClass: DataClass,
    typeRefContext: TypeRefContext
): List<CompletionItem> =
    dataClass.memberFunctions.map { function ->
        val label = function.computeCompletionLabel(typeRefContext)
        val text = function.computeCompletionInsertText(typeRefContext)
        CompletionItem(label).apply {
            kind = CompletionItemKind.Method
            insertTextFormat = InsertTextFormat.Snippet
            insertTextMode = InsertTextMode.AdjustIndentation
            insertText = text
        }
    }

private class ValueFactoryIndexStore {

    private val store = mutableMapOf<URI, ValueFactoryIndex>()

    operator fun get(uri: URI): ValueFactoryIndex? = store[uri]

    fun store(uri: URI, storeEntry: DocumentStoreEntry) {
        store[uri] = ValueFactoryIndex(storeEntry)
    }

    fun remove(uri: URI) {
        store.remove(uri)
    }
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
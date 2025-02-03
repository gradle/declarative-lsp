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
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel
import org.gradle.declarative.lsp.extension.indexBasedOverlayResultFromDocuments
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
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils.resolvedDocument
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.gradle.internal.declarativedsl.evaluator.runner.stepResultOrPartialResult
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.CompletableFuture

private val LOGGER = LoggerFactory.getLogger(DeclarativeTextDocumentService::class.java)

class DeclarativeTextDocumentService : TextDocumentService {

    private lateinit var client: LanguageClient
    private lateinit var documentStore: VersionedDocumentStore
    private lateinit var mutationRegistry: MutationRegistry
    private lateinit var declarativeFeatures: DeclarativeFeatures
    private lateinit var declarativeResources: DeclarativeResourcesModel
    private lateinit var schemaAnalysisEvaluator: SimpleAnalysisEvaluator

    fun initialize(
        client: LanguageClient,
        documentStore: VersionedDocumentStore,
        mutationRegistry: MutationRegistry,
        declarativeFeatures: DeclarativeFeatures,
        declarativeResources: DeclarativeResourcesModel
    ) {
        this.client = client
        this.declarativeFeatures = declarativeFeatures
        this.documentStore = documentStore
        this.mutationRegistry = mutationRegistry
        this.declarativeResources = declarativeResources

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
            val parsed = parse(uri, text)
            run {
                documentStore.storeInitial(uri, text, parsed.documentOverlayResult, parsed.analysisSchemas)
                processDocument(uri)
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
                val parsed = parse(uri, change.text)
                documentStore.storeVersioned(uri, version, text, parsed.documentOverlayResult, parsed.analysisSchemas)
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
            withDom(uri) { dom, _, _ ->
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
            withDom(uri) { dom, schema, _ ->
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
                        computePropertyCompletions(dataClass, schema) + computePropertyByValueFactoryCompletions(
                            dataClass,
                            schema
                        ) +
                                computeFunctionCompletions(dataClass, schema)
                    }
            }
        }.orEmpty().toMutableList()
        return CompletableFuture.completedFuture(Either.forLeft(completions))
    }

    override fun signatureHelp(params: SignatureHelpParams?): CompletableFuture<SignatureHelp> {
        LOGGER.trace("Signature help requested for position: {}", params)

        val signatureInformationList = params?.let {
            val uri = URI(it.textDocument.uri)
            withDom(uri) { dom, _, _ ->
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

    private fun processDocument(uri: URI) = withDom(uri) { dom, schema, _ ->
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

    private fun parse(uri: URI, text: String): ParsedDocument {
        val fileName = uri.path.substringAfterLast('/')
        val analysisResult = schemaAnalysisEvaluator.evaluate(fileName, text)

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

    private fun <T> withDom(uri: URI, work: (DocumentOverlayResult, AnalysisSchema, String) -> T): T? {
        return documentStore[uri]?.let { entry ->
            work(entry.dom, entry.unionSchema, entry.document)
        }
    }

}

// Pure functions supporting LSP functions -----------------------------------------------------------------------------

private fun computePropertyCompletions(
    dataClass: DataClass,
    analysisSchema: AnalysisSchema
): List<CompletionItem> {
    return dataClass.properties.mapNotNull { property ->
        when (val resolvedType = SchemaTypeRefContext(analysisSchema).resolveRef(property.valueType)) {
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

private typealias LabelAndInsertText = Pair<String, String>

private fun computePropertyByValueFactoryCompletions(
    dataClass: DataClass,
    analysisSchema: AnalysisSchema
): List<CompletionItem> {
    fun indexValueFactories(analysisSchema: AnalysisSchema, type: DataClass, namePrefix: String): Map<FqName, List<LabelAndInsertText>> {
        val factoryIndex = mutableMapOf<FqName, List<LabelAndInsertText>>()
        type.memberFunctions
            .filter { it.semantics is FunctionSemantics.Pure && it.returnValueType is DataTypeRef.Name }
            .forEach {
                val indexKey = (it.returnValueType as DataTypeRef.Name).fqName
                val labelAndInsertText = "$namePrefix${computeCompletionLabel(it)}" to "$namePrefix${computeCompletionInsertText(it, analysisSchema)}"
                factoryIndex.merge(indexKey, listOf(labelAndInsertText)) { oldVal, newVal -> oldVal + newVal }
            }
        type.properties.filter { it.valueType is DataTypeRef.Name }.forEach {
            when (val propType = analysisSchema.dataClassTypesByFqName[(it.valueType as DataTypeRef.Name).fqName]) {
                is DataClass -> {
                    val propName = it.name
                    val propIndex = indexValueFactories(analysisSchema, propType, "$namePrefix${propName}.")
                    propIndex.forEach { (key, value) -> factoryIndex.merge(key, value) { oldVal, newVal -> oldVal + newVal } }
                }

                is EnumClass -> Unit
                null -> Unit
            }
        }
        return factoryIndex
    }

    val factories = indexValueFactories(analysisSchema, analysisSchema.topLevelReceiverType, "")
    return dataClass.properties.flatMap { property ->
        val resolvedType = SchemaTypeRefContext(analysisSchema).resolveRef(property.valueType)
        if (resolvedType is DataType.ClassDataType) {
            val factoriesForProperty = factories[resolvedType.name]
            factoriesForProperty
                ?.map {
                    CompletionItem("${property.name} = ${it.first}").apply {
                        kind = CompletionItemKind.Field
                        insertTextFormat = InsertTextFormat.Snippet
                        insertText = "${property.name} = ${it.second}"
                    }
                } ?: emptyList()
        } else {
            emptyList()
        }
    }
}

private fun computeFunctionCompletions(
    dataClass: DataClass,
    analysisSchema: AnalysisSchema
): List<CompletionItem> =
    dataClass.memberFunctions.map { function ->
        val label = computeCompletionLabel(function)
        val text = computeCompletionInsertText(function, analysisSchema)
        CompletionItem(label).apply {
            kind = CompletionItemKind.Method
            insertTextFormat = InsertTextFormat.Snippet
            insertTextMode = InsertTextMode.AdjustIndentation
            insertText = text
        }
    }

/**
 * Computes a [placeholder](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#placeholders)
 * based on the given data type.
 *
 * If there is a specific placeholder for the given data type, it will be used.
 * Otherwise, a simple indexed will be used
 */
private fun computeTypedPlaceholder(
    index: Int,
    resolvedType: DataType
): String {
    return when (resolvedType) {
        is DataType.BooleanDataType -> "\${$index|true,false|}"
        is EnumClass -> "\${$index|${resolvedType.entryNames.joinToString(",")}|}"
        is DataType.IntDataType -> "\${$index:0}"
        is DataType.LongDataType -> "\${$index:0L}"
        is DataType.StringDataType -> "\"\${$index}\""
        else -> "\$$index"
    }
}

private fun computeCompletionLabel(function: SchemaMemberFunction): String {
    val functionName = function.simpleName
    val parameterSignature = when (function.parameters.isEmpty()) {
        true -> ""
        false -> function.parameters.joinToString(",", "(", ")") { it.toSignatureLabel() }
    }
    val configureBlockLabel = function.semantics.toBlockConfigurabilityLabel().orEmpty()

    return "$functionName$parameterSignature$configureBlockLabel"
}

private fun computeCompletionInsertText(
    function: SchemaFunction,
    analysisSchema: AnalysisSchema,
): String {
    val parameterSnippet = function.parameters.mapIndexed { index, parameter ->
        // Additional placeholders are indexed from 1
        val resolvedType = SchemaTypeRefContext(analysisSchema).resolveRef(parameter.type)
        computeTypedPlaceholder(index + 1, resolvedType)
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
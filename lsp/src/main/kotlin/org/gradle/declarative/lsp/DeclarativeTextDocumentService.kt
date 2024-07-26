package org.gradle.declarative.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.gradle.declarative.lsp.build.model.ResolvedDeclarativeResourcesModel
import org.gradle.declarative.lsp.visitor.FoldingRangeVisitor
import org.gradle.declarative.lsp.visitor.LocationMatchingVisitor
import org.gradle.declarative.lsp.visitor.visit
import org.gradle.declarative.lsp.storage.VersionedDocumentStore
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
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

    // LSP Functions ----------------------------------------------------------

    fun setResources(resources: ResolvedDeclarativeResourcesModel) {
        this.resources = resources
        schemaEvaluator = SimpleAnalysisEvaluator.withSchema(
            resources.settingsInterpretationSequence, resources.projectInterpretationSequence
        )
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        params?.textDocument?.uri?.let { uri ->
            URI(uri)
        }?.let { uri ->
            System.err.println("Opened document: $uri")
            val text = File(uri).readText()
            documentStore.storeInitial(uri, parse(uri, text))
            System.err.println("Stored declarative model for document: $uri")
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        params?.let { nonNullParams ->
            val uri = URI(nonNullParams.textDocument.uri)
            nonNullParams.contentChanges.forEach { change ->
                documentStore.storeVersioned(
                    uri,
                    nonNullParams.textDocument.version,
                    parse(uri, change.text)
                )
            }
            System.err.println("Changed document: ${params.textDocument?.uri}")
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        System.err.println("Closed document: ${params?.textDocument?.uri}")
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
        System.err.println("Saved document: ${params?.textDocument?.uri}")
    }

    override fun hover(params: HoverParams?): CompletableFuture<Hover> {
        val hover = params?.let { nonNullParams ->
            val uri = URI(nonNullParams.textDocument.uri)
            withDom(uri) { dom ->
                val position = nonNullParams.position

                // LSPs are supplying 0-based line and column numbers, while the DSL model is 1-based
                val visitor = LocationMatchingVisitor(position.line + 1, position.character + 1)
                dom.visit(visitor)

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

    // Utility and other member functions -------------------------------------

    private fun parse(uri: URI, text: String): DeclarativeDocument {
        val fileName = uri.path.substringAfterLast('/')
        val fileSchema = schemaEvaluator.evaluate(fileName, text)
        val settingsSchema = schemaEvaluator.evaluate(
            resources.settingsFile.name, resources.settingsFile.readText()
        )

        System.err.println("Parsed declarative model for document: $uri")
        val document = AnalysisDocumentUtils.documentWithModelDefaults(settingsSchema, fileSchema)
        return document!!.document
    }

    private fun <T> withDom(uri: URI, work: (DeclarativeDocument) -> T): T? {
        return documentStore[uri]?.let { document ->
            work(document)
        }
    }
}
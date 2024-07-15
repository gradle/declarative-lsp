package org.gradle.declarative.lsp.server

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.gradle.declarative.lsp.build.model.ResolvedDeclarativeResourcesModel
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.ElementNode
import org.gradle.internal.declarativedsl.dom.DeclarativeDocument.DocumentNode.PropertyNode
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisDocumentUtils
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.gradle.internal.declarativedsl.language.SourceData
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

class DeclarativeTextDocumentService : TextDocumentService, LanguageClientAware {

    private val domStore = mutableMapOf<String, DeclarativeDocument>()

    private lateinit var client: LanguageClient
    private lateinit var resources: ResolvedDeclarativeResourcesModel
    private lateinit var schemaEvaluator: SimpleAnalysisEvaluator

    override fun connect(client: LanguageClient?) {
        this.client = client!!
    }

    fun setResources(resources: ResolvedDeclarativeResourcesModel) {
        this.resources = resources
        schemaEvaluator = SimpleAnalysisEvaluator.withSchema(
            resources.settingsInterpretationSequence, resources.projectInterpretationSequence
        )
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        params?.textDocument?.uri?.let { uri ->
            System.err.println("Opened document: $uri")
            System.err.println("Parsing declarative model for document: $uri")

            val file = File(URI.create(uri))
            val fileSchema = schemaEvaluator.evaluate(file.name, file.readText())
            val settingsSchema = schemaEvaluator.evaluate(
                resources.settingsFile.name, resources.settingsFile.readText()
            )

            System.err.println("Parsed declarative model for document: $uri")
            AnalysisDocumentUtils.documentWithConventions(settingsSchema, fileSchema)?.let {
                domStore[uri] = it.document
            }
            System.err.println("Stored declarative model for document: $uri")
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams?) {
        System.err.println("Changed document: ${params?.textDocument?.uri}")
    }

    override fun didClose(params: DidCloseTextDocumentParams?) {
        System.err.println("Closed document: ${params?.textDocument?.uri}")
    }

    override fun didSave(params: DidSaveTextDocumentParams?) {
        System.err.println("Saved document: ${params?.textDocument?.uri}")
    }

    override fun hover(params: HoverParams?): CompletableFuture<Hover> {
        params?.textDocument?.uri?.let { uri ->
            val position = params.position
            System.err.println("Hovering over document: $uri at line ${position.line} column ${position.character}")

            domStore[uri]?.let {
                val closest = findBestFitting(it.content, position)
                System.err.println("Closest node: $closest")
            }
        }

        return CompletableFuture()
    }

    companion object {
        fun findBestFitting(
            nodes: List<DeclarativeDocument.DocumentNode>, cursorPosition: Position
        ): DeclarativeDocument.DocumentNode? {
            return nodes.firstNotNullOf { node ->
                when (node) {
                    is ElementNode -> findBestFitting(node.content, cursorPosition)
                    is PropertyNode -> if (cursorIsInside(node.sourceData, cursorPosition)) node else null
                    else -> null
                }
            }
        }

        private fun cursorIsInside(candidatePosition: SourceData, cursorPosition: Position): Boolean {
            return candidatePosition.lineRange.contains(cursorPosition.line) && candidatePosition.startColumn <= cursorPosition.character && candidatePosition.endColumn >= cursorPosition.character
        }
    }
}
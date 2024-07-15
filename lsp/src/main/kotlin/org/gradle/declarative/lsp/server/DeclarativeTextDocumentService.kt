package org.gradle.declarative.lsp.server

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import org.gradle.internal.declarativedsl.evaluator.main.AnalysisSequenceResult
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

class DeclarativeTextDocumentService(): TextDocumentService, LanguageClientAware {

    lateinit var schemaEvaluator: SimpleAnalysisEvaluator
    lateinit var client: LanguageClient

    val schemaStore = mutableMapOf<String, AnalysisSequenceResult>()

    override fun connect(client: LanguageClient?) {
        this.client = client!!
    }

    fun setDeclarativeModel(declarativeModel: DeclarativeSchemaModel) {
        schemaEvaluator = SimpleAnalysisEvaluator.withSchema(declarativeModel.settingsSequence, declarativeModel.projectSequence)
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        params?.textDocument?.uri?.let { uri ->
            System.err.println("Opened document: $uri")
            System.err.println("Parsing declarative model for document: $uri")

            val file = File(URI.create(uri))
            val schema = schemaEvaluator.evaluate(file.name, file.readText())

            schemaStore[uri] = schema
            System.err.println("Parsed declarative model for document: $uri")
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
        }

        return CompletableFuture.completedFuture(null)
    }
}
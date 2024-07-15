package org.gradle.declarative.lsp.server

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.*
import org.gradle.tooling.GradleConnector
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.system.exitProcess

class DeclarativeLanguageServer : LanguageServer, LanguageClientAware {
    val LOG = Logger.getLogger(DeclarativeLanguageServer::class.java.name)

    lateinit var client: LanguageClient
    val connectorMap = mutableMapOf<String, GradleConnector>()

    val textDocumentService = DeclarativeTextDocumentService()
    val workspaceService = DeclarativeWorkspaceService()

    var initialized = false
    var tracingLevel = TraceValue.Off;

    private fun checkInitialized() {
        if (!initialized) {
            throw IllegalStateException("Language server not initialized")
        }
    }

    override fun connect(client: LanguageClient?) {
        this.client = client!!
        textDocumentService.connect(this.client)
        workspaceService.connect(this.client)
    }

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        val serverCapabilities = ServerCapabilities()
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Full)

        initialized = true
        System.err.println("Gradle Declartive Language Server: initialized")
        return CompletableFuture.completedFuture(InitializeResult(serverCapabilities))
    }

    override fun shutdown(): CompletableFuture<Any> {
        checkInitialized()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        exitProcess(0)
    }

    override fun getTextDocumentService(): TextDocumentService {
        System.err.println("Gradle Declartive Language Server: getTextDocumentService")
        return textDocumentService
    }

    override fun getWorkspaceService(): WorkspaceService {
        System.err.println("Gradle Declartive Language Server: getWorkspaceService")
        return workspaceService
    }

    override fun setTrace(params: SetTraceParams?) {
        checkInitialized()
        tracingLevel = params?.value ?: TraceValue.Off
    }

}

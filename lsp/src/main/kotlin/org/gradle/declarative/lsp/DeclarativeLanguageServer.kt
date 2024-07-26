package org.gradle.declarative.lsp

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TraceValue
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.gradle.declarative.lsp.tapi.ConnectionHandler
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

class DeclarativeLanguageServer : LanguageServer, LanguageClientAware {
    private lateinit var client: LanguageClient

    private val textDocumentService = DeclarativeTextDocumentService()
    private val workspaceService = DeclarativeWorkspaceService()

    private var initialized = false
    private var tracingLevel = TraceValue.Off;

    private fun checkInitialized() {
        require(initialized) {
            "Language server is not initialized"
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
        serverCapabilities.setHoverProvider(true)
        serverCapabilities.setCodeActionProvider(true)

        val workspaceFolder = params!!.workspaceFolders[0]
        val workspaceFolderFile = File(URI.create(workspaceFolder.uri))
        LOGGER.info("Fetching declarative model for workspace folder: $workspaceFolderFile")
        ConnectionHandler(workspaceFolderFile).let {
            val declarativeBuildModel = it.getDomPrequisites()
            textDocumentService.setResources(declarativeBuildModel)
        }

        initialized = true
        LOGGER.info("Gradle Declartive Language Server: initialized")
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
        LOGGER.info("Gradle Declartive Language Server: getTextDocumentService")
        return textDocumentService
    }

    override fun getWorkspaceService(): WorkspaceService {
        LOGGER.info("Gradle Declartive Language Server: getWorkspaceService")
        return workspaceService
    }

    override fun setTrace(params: SetTraceParams?) {
        checkInitialized()
        tracingLevel = params?.value ?: TraceValue.Off
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeclarativeLanguageServer::class.java)
    }

}

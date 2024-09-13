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

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.*
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
        // Here we set the capabilities we support
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
        serverCapabilities.setHoverProvider(true)
        serverCapabilities.completionProvider = CompletionOptions(true, listOf())

        val workspaceFolder = params!!.workspaceFolders[0]
        val workspaceFolderFile = File(URI.create(workspaceFolder.uri))
        LOGGER.info("Fetching declarative model for workspace folder: $workspaceFolderFile")
        TapiConnectionHandler(workspaceFolderFile).let {
            val declarativeBuildModel = it.getDomPrequisites()
            textDocumentService.setResources(declarativeBuildModel)
        }

        initialized = true
        LOGGER.info("Gradle Declarative Language Server: initialized")
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
        LOGGER.info("Gradle Declarative Language Server: getTextDocumentService")
        return textDocumentService
    }

    override fun getWorkspaceService(): WorkspaceService {
        LOGGER.info("Gradle Declarative Language Server: getWorkspaceService")
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

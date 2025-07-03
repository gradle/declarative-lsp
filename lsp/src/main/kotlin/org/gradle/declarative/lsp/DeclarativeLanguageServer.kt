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

import com.google.gson.JsonObject
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.SignatureHelpOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TraceValue
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.gradle.declarative.lsp.mutation.MutationRegistry
import org.gradle.declarative.lsp.mutation.definition.AddLibraryDependency
import org.gradle.declarative.lsp.mutation.definition.SetJavaVersion
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

/**
 * Top-level class implementing the language server.
 *
 * This class is responsible for holding onto centralized state holders,
 * and distributing them to specific LSP services like [DeclarativeTextDocumentService] and [DeclarativeWorkspaceService].
 */
class DeclarativeLanguageServer : LanguageServer, LanguageClientAware {

    // LSP state holders
    private var declarativeModelStore: DeclarativeModelStore? = null
    private val documentStore = VersionedDocumentStore()
    private var initialized = false
    private var tracingLevel = TraceValue.Off
    private val mutationRegistry = MutationRegistry(
        listOf(
            SetJavaVersion(),
            AddLibraryDependency()
        )
    )

    // LSP services
    private lateinit var client: LanguageClient
    private val textDocumentService = DeclarativeTextDocumentService()
    private val workspaceService = DeclarativeWorkspaceService()

    private fun checkInitialized() {
        require(initialized) {
            "Language server is not initialized"
        }
    }

    override fun connect(client: LanguageClient?) {
        this.client = client!!
    }

    override fun initialize(params: InitializeParams?): CompletableFuture<InitializeResult> {
        requireNotNull(params) {
            "Initialization parameters must not be null"
        }

        val declarativeFeatures = params.initializationOptions?.let {
            if (it is JsonObject) it else null
        }?.let {
            it.get("declarativeFeatures")?.asJsonObject
        }?.let {
            DeclarativeFeatures(
                mutations = it.get("mutations")?.asBoolean ?: false
            )
        } ?: DeclarativeFeatures()

        val serverCapabilities = ServerCapabilities()
        // Here we set the capabilities we support
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
        serverCapabilities.setHoverProvider(true)
        serverCapabilities.setCodeActionProvider(true)
        serverCapabilities.completionProvider = CompletionOptions(false, listOf())
        serverCapabilities.signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))

        val workspaceFolder = params.workspaceFolders[0]
        val workspaceFolderFile = File(URI.create(workspaceFolder.uri))

        // Progress reporting under initialization only works with clients providing a `workDoneToken`
        params.workDoneToken?.let {
            client.notifyProgress(
                ProgressParams(
                    params.workDoneToken,
                    Either.forLeft(
                        WorkDoneProgressBegin().apply {
                            title = "Fetching Declarative Gradle model"
                        }
                    )
                )
            )
        }

        declarativeModelStore = DeclarativeModelStore(workspaceFolderFile).apply {
            // We immediately try to sync the declarative model.
            // The synchronization might be unsuccessful if the project is broken, but it won't crash the server.
            this.updateModel()
            // Initialize the core LSP services
            textDocumentService.initialize(
                client,
                documentStore,
                mutationRegistry,
                declarativeFeatures,
                this
            )
            workspaceService.initialize(
                client,
                documentStore,
                mutationRegistry,
                this
            )
        }

        initialized = true
        LOGGER.info("Gradle Declarative Language Server: initialized")
        return CompletableFuture.completedFuture(
            InitializeResult(serverCapabilities)
        )
    }

    override fun shutdown(): CompletableFuture<Any> {
        checkInitialized()
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        exitProcess(0)
    }

    override fun getTextDocumentService(): TextDocumentService {
        return textDocumentService
    }

    override fun getWorkspaceService(): WorkspaceService {
        return workspaceService
    }

    override fun setTrace(params: SetTraceParams?) {
        checkInitialized()
        tracingLevel = params?.value ?: TraceValue.Off
    }

    /**
     * Checks if the language server is initialized and the declarative model store is available.
     * Used mainly by tests to check on the langauge server's internal state.
     */
    fun isModelAvailable(): Boolean {
        return declarativeModelStore?.isAvailable() ?: false;
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeclarativeLanguageServer::class.java)
        private const val MODEL_FETCH_PROGRESS_TOKEN = "modelFetchProgress"
    }
}

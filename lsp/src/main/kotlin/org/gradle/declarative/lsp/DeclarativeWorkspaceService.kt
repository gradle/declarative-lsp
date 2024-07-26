package org.gradle.declarative.lsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService

class DeclarativeWorkspaceService(): WorkspaceService, LanguageClientAware {

    private lateinit var client: LanguageClient

    override fun connect(client: LanguageClient?) {
        this.client = client!!
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        System.err.println("Changed configuration: ${params?.settings}")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        System.err.println("Changed watched files: ${params?.changes}")
    }
}
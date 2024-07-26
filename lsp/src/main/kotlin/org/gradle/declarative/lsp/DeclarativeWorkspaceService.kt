package org.gradle.declarative.lsp

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService
import org.slf4j.LoggerFactory

class DeclarativeWorkspaceService : WorkspaceService, LanguageClientAware {

    private lateinit var client: LanguageClient

    override fun connect(client: LanguageClient?) {
        this.client = client!!
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        LOGGER.info("Changed configuration: ${params?.settings}")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        LOGGER.info("Changed watched files: ${params?.changes}")
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeclarativeWorkspaceService::class.java)
    }
}

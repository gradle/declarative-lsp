package org.gradle.declarative.lsp.server

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService

class DeclarativeTextDocumentService(): TextDocumentService, LanguageClientAware {

    lateinit var client: LanguageClient

    override fun connect(client: LanguageClient?) {
        this.client = client!!
    }

    override fun didOpen(params: DidOpenTextDocumentParams?) {
        System.err.println("Opened document: ${params?.textDocument?.uri}")
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

}
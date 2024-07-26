package org.gradle.declarative.lsp.logging

import org.eclipse.lsp4j.services.LanguageClient
import org.jetbrains.kotlin.backend.common.LoggingContext
import org.slf4j.LoggerFactory

object LoggingUtils {

    /**
     * This utility method can replace the `stderr` logging used by default with the logging features supported by the LSP
     */
    fun useLspLogger(client: LanguageClient) {
//        val context = LoggerFactory.getILoggerFactory()
//        val rootLogger = LoggerFactory.getLogger("ROOT")
//
//        rootLogger.
    }


}
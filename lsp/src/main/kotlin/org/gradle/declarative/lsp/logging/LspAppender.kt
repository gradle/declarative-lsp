package org.gradle.declarative.lsp.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient

class LspAppender(private val client: LanguageClient): AppenderBase<ILoggingEvent>() {

    override fun append(eventObject: ILoggingEvent?) {
        eventObject?.let {
            val message = it.formattedMessage
            val type = when(it.level.toInt()) {
                10000 -> MessageType.Error
                20000 -> MessageType.Warning
                30000 -> MessageType.Info
                else -> MessageType.Log
            }

            val messageParams = MessageParams(type, message)
            client.logMessage(messageParams)
        }
    }

}
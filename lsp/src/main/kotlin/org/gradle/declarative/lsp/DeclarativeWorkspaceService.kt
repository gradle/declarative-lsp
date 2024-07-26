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

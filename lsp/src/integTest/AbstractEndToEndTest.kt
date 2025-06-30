/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.declarative.lsp.e2e

import io.mockk.mockk
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.gradle.declarative.lsp.DeclarativeLanguageServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.util.concurrent.CompletableFuture

abstract class AbstractEndToEndTest {

    protected lateinit var languageClient: LanguageClient
    protected lateinit var languageServer: DeclarativeLanguageServer

    protected val workspaceServices get() = languageServer.workspaceService
    protected val textDocumentService get() = languageServer.textDocumentService

    @BeforeEach
    fun setup() {
        languageServer = DeclarativeLanguageServer()
        languageClient = mockk()
        languageServer.connect(languageClient)
    }

    @AfterEach
    fun tearDown() {
        // Clean up resources if needed
        languageServer.shutdown()
    }

    fun initializeStandardProject(projectDir: File) {
        // Make the project wrapper
        projectDir.resolve("gradle/wrapper/gradle-wrapper.properties").apply {
            parentFile.mkdirs()
            writeText(
                """
                distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.2-bin.zip
                """.trimIndent()
            )
        }
    }

    // Utility functions to help testing
    fun initializeWithProjectDir(
        projectDir: File,
        additionalConfiguration: ((InitializeParams) -> Unit)? = null
    ): CompletableFuture<InitializeResult> {
        val projectUri = projectDir.toURI().toString()
        val params = InitializeParams().apply {
            workspaceFolders = listOf(
                WorkspaceFolder(
                    projectUri,
                    "projectDir"
                )
            )
        }
        // Apply any additional customizations required by the test
        if (additionalConfiguration != null) {
            additionalConfiguration(params)
        }

        return languageServer.initialize(params)
    }

}
/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.declarative.lsp.e2e

import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse

class InvalidGradleProjectTests : AbstractEndToEndTest() {

    @Test
    fun `when settings file is broken the server won't crash`(@TempDir projectDir: File) {
        // Create a settings.gradle file with invalid content
        val settingsFile = projectDir.resolve("settings.gradle")
        settingsFile.writeText("INVALID SETTINGS FILE")

        // Initialize the project
        val initializeResult = initializeWithProjectDir(projectDir)

        // Assert that the server starts without crashing
        // This does not mean that the server can respond to requests, as it would need a valid project model
        assertNotNull(initializeResult.get())
    }

    @Test
    fun `if settings file is fixed the server will recover`(@TempDir projectDir: File) {
        // Create a settings.gradle file with invalid content
        val settingsFile = projectDir.resolve("settings.gradle").apply {
            writeText("INVALID SETTINGS FILE")
        }

        // Initialize the project
        initializeWithProjectDir(projectDir)
        // Project sync should be broken, as the settings file is invalid
        assertFalse(languageServer.isInitialized(), "Server should not be initialized with invalid settings file")

        // Now fix the settings file
        settingsFile.writeText("rootProject.name = 'valid-project'")
        // Send a save notification to the server to initiate a resync
        textDocumentService.didSave(
            DidSaveTextDocumentParams(
                TextDocumentIdentifier(
                    projectDir.resolve("settings.gradle").toURI().toString()
                )
            )
        )
        assertTrue(languageServer.isInitialized(), "Server should be initialized after fixing the settings file")
    }
}

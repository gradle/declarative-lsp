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

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.gradle.declarative.lsp.SyncState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class InvalidGradleProjectTests : AbstractEndToEndTest() {

    @Test
    fun `when settings file is broken at initialization time the server won't crash and can recover`(@TempDir projectDir: File) {
        // Create a settings.gradle file with invalid content
        val settingsFile = projectDir.resolve("settings.gradle")
        settingsFile.writeText("INVALID SETTINGS FILE")
        // Initialize the project
        initializeWithProjectDir(projectDir)
        assertEquals(SyncState.FAILED_SYNC, languageServer.syncState(), "Server should not be initialized with a broken settings file")
        verify {
            languageClient.showMessage(match {
                it.type == MessageType.Error && it.message.isNotBlank()
            })
        }
        confirmVerified(languageClient)

        // Fix the settings file
        settingsFile.writeText("rootProject.name = 'valid-project'")
        // When the synchronization happens, the server sends a showMessageRequest to the client
        // We mock the client to always respond with the first action
        every {
            languageClient.showMessageRequest(any())
        } answers {
            val params = it.invocation.args[0] as ShowMessageRequestParams
            val response = params.actions.first()
            CompletableFuture.completedFuture(response)
        }
        // Send a save notification to the server to initiate a resync
        textDocumentService.didSave(
            DidSaveTextDocumentParams(
                TextDocumentIdentifier(
                    projectDir.resolve("settings.gradle").toURI().toString()
                )
            )
        )
        assertEquals(SyncState.SYNCED, languageServer.syncState(), "Server should be initialized after fixing the settings file")
    }

    @Test
    fun `when settings file is broken at saving time the server won't crash and can recover`(@TempDir projectDir: File) {
        val settingsFile =  projectDir.resolve("settings.gradle").apply {
            writeText("rootProject.name = 'valid-project'")
        }
        // Initialize the project
        initializeWithProjectDir(projectDir)
        assertEquals(SyncState.SYNCED, languageServer.syncState(), "Server should be initialized with a valid settings file")

        // We break the project
        settingsFile.writeText("INVALID SETTINGS FILE")
        // When the synchronization happens, the server sends a showMessageRequest to the client
        // We mock the client to always respond with the first action
        every {
            languageClient.showMessageRequest(any())
        } answers {
            val params = it.invocation.args[0] as ShowMessageRequestParams
            val response = params.actions.first()
            CompletableFuture.completedFuture(response)
        }
        // Send a didSave notification to the server to initiate a resync
        textDocumentService.didSave(
            DidSaveTextDocumentParams(
                TextDocumentIdentifier(
                    projectDir.resolve("settings.gradle").toURI().toString()
                )
            )
        )
        assertEquals(SyncState.FAILED_SYNC, languageServer.syncState(), "Server should not be initialized with a broken settings file")

        // Fix the settings file
        settingsFile.writeText("rootProject.name = 'valid-project'")
        // Send a save notification to the server to initiate a resync
        textDocumentService.didSave(
            DidSaveTextDocumentParams(
                TextDocumentIdentifier(
                    projectDir.resolve("settings.gradle").toURI().toString()
                )
            )
        )
        assertEquals(SyncState.SYNCED, languageServer.syncState(), "Server should be initialized after fixing the settings file")
    }
}

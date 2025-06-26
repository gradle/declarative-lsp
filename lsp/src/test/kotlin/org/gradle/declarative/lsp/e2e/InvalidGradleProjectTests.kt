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

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class InvalidGradleProjectTests: AbstractEndToEndTest() {

    @Test
    fun `when settings file is broken the server won't crash`(@TempDir projectDir: File) {
        // Create a settings.gradle file with invalid content
        val settingsFile = projectDir.resolve("settings.gradle")
        settingsFile.writeText("INVALID SETTINGS FILE")

        // Start the server with the project directory
        testLspServerStart()
        // Initialize the language server
        testLspServerInitialize()
        // Read a line from the server output to ensure it started
        val serverOutput = serverToClientStream.bufferedReader().readLine()
        println(serverOutput)
    }

}


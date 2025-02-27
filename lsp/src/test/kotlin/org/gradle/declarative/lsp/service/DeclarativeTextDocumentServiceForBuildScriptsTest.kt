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

package org.gradle.declarative.lsp.service

import org.junit.jupiter.api.Test
import kotlin.io.path.readLines
import kotlin.test.assertEquals

class DeclarativeTextDocumentServiceForBuildScriptsTest: AbstractDeclarativeTextDocumentServiceTest() {

    override fun script() = buildFile

    @Test
    fun `code completion inside block with properties`() {
        openFile(script())

        assertEquals(
            listOf(
                "androidLibrary {",
                "    secrets {         }",
                "}",
            ),
            script().readLines().slice(3..5)
        )

        assertCompletion(
            script(), 4, 16, listOf(
                """defaultPropertiesFile = layout.projectDirectory.file(path: String), defaultPropertiesFile = layout.projectDirectory.file("${'$'}{1}")${'$'}0""",
                """defaultPropertiesFile = layout.settingsDirectory.file(path: String), defaultPropertiesFile = layout.settingsDirectory.file("${'$'}{1}")${'$'}0""",
                """enabled = Boolean, enabled = ${'$'}{1|true,false|}"""
            )
        )
    }

}
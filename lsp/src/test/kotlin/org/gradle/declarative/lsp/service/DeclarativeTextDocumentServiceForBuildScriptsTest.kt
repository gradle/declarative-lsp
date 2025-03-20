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

class DeclarativeTextDocumentServiceForBuildScriptsTest : AbstractDeclarativeTextDocumentServiceTest() {

    override fun script() = buildFile

    @Test
    fun `code completion inside block with file properties`() {
        openFile(script())

        assertEquals(
            "    secrets {         }",
            script().readLines()[3]
        )

        assertCompletion(
            script(), 3, 16, """
                |defaultPropertiesFile = layout.projectDirectory.file(path: String) --> defaultPropertiesFile = layout.projectDirectory.file("${'$'}{1}")${'$'}0
                |defaultPropertiesFile = layout.settingsDirectory.file(path: String) --> defaultPropertiesFile = layout.settingsDirectory.file("${'$'}{1}")${'$'}0
                |enabled = Boolean --> enabled = ${'$'}{1|true,false|}
            """.trimMargin()
        )
    }

    @Test
    fun `code completion inside block with list properties`() {
        openFile(script())

        assertEquals(
            "        release {         }",
            script().readLines()[6]
        )

        assertCompletion(
            script(), 6, 21, """
                |baselineProfile { this: BaselineProfile } --> baselineProfile {${'\n'}|${'\t'}${'$'}0${'\n'}|}
                |defaultProguardFiles = listOf(vararg elements: ProguardFile) --> defaultProguardFiles = listOf(${'$'}1)${'$'}0
                |dependencies { this: AndroidLibraryDependencies } --> dependencies {${'\n'}|${'\t'}${'$'}0${'\n'}|}
                |minify { this: Minify } --> minify {${'\n'}|${'\t'}${'$'}0${'\n'}|}
                |proguardFile(name: String) --> proguardFile("${'$'}{1}")${'$'}0
                |proguardFiles = listOf(vararg elements: ProguardFile) --> proguardFiles = listOf(${'$'}1)${'$'}0
            """.trimMargin()
        )
    }

}
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

package org.gradle.declarative.lsp

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeclarativeModelStoreTest {

    /* ───────────────────────────── simple project ───────────────────────────── */

    @ParameterizedTest
    @ValueSource(strings = ["build.gradle", "build.gradle.kts", "build.gradle.dcl"])
    fun `can synchronize a simple project`(
        buildFileName: String,
        @TempDir projectDir: File,
    ) {
        // <projectDir>/<buildFileName>
        val buildFile = projectDir.resolve(buildFileName).apply { writeText("") }
        // <projectDir>/settings.gradle  (content irrelevant for single-project)
        projectDir.resolve("settings.gradle").writeText("")

        val modelStore = DeclarativeModelStore(projectDir)
        assertEquals(modelStore.syncState, SyncState.UNSYNCED, "Model should not be available before initialization")
        modelStore.updateModel()
        assertEquals(modelStore.syncState, SyncState.SYNCED, "Model should be available after updating")

        modelStore.ifAvailable { model ->
            assertNotNull(model.projectInterpretationSequence)
            assertNotNull(model.settingsInterpretationSequence)
            assertNotNull(model.buildScriptFiles)

            val canonicalScripts = model.buildScriptFiles.map { it.canonicalFile.absolutePath }
            assertTrue(
                canonicalScripts.contains(buildFile.canonicalFile.absolutePath),
                "Build file '$buildFileName' should be included in the model"
            )
        }
    }

    /* ──────────────────────────── multi-project build ───────────────────────── */

    @ParameterizedTest
    @ValueSource(strings = ["build.gradle", "build.gradle.kts", "build.gradle.dcl"])
    fun `can synchronize a multi-project build`(
        buildFileName: String,
        @TempDir projectDir: File,
    ) {
        // root project
        val rootBuildFile = projectDir.resolve(buildFileName).apply { writeText("") }
        projectDir.resolve("settings.gradle").writeText("""include("sub")""")

        // sub-project
        val subProjectDir = projectDir.resolve("sub").apply { mkdirs() }
        val subBuildFile  = subProjectDir.resolve(buildFileName).apply { writeText("") }

        val modelStore = DeclarativeModelStore(projectDir)
        assertEquals(modelStore.syncState, SyncState.UNSYNCED, "Model should not be available before initialization")
        modelStore.updateModel()
        assertEquals(modelStore.syncState, SyncState.SYNCED, "Model should be available after updating")

        modelStore.ifAvailable { model ->
            assertNotNull(model.projectInterpretationSequence)
            assertNotNull(model.settingsInterpretationSequence)
            assertNotNull(model.buildScriptFiles)

            val canonicalScripts = model.buildScriptFiles.map { it.canonicalFile.absolutePath }

            assertTrue(
                canonicalScripts.contains(rootBuildFile.canonicalFile.absolutePath),
                "Root build file '$buildFileName' must be in the model"
            )
            assertTrue(
                canonicalScripts.contains(subBuildFile.canonicalFile.absolutePath),
                "Sub-project build file '$buildFileName' must be in the model"
            )
        }
    }
}

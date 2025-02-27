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

import io.mockk.mockk
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel
import org.gradle.declarative.lsp.service.MutationRegistry
import org.gradle.declarative.lsp.service.VersionedDocumentStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals

@Suppress("MaxLineLength")
class DeclarativeTextDocumentServiceTest {

    @field:TempDir
    lateinit var buildFolder: File

    private lateinit var service: DeclarativeTextDocumentService

    private lateinit var settingsFile: Path
    private lateinit var buildFile: Path

    @BeforeEach
    fun setup() {
        settingsFile = Path("$buildFolder/settings.gradle.dcl")
        buildFile = Path("$buildFolder/app/build.gradle.dcl")

        val declarativeResources = setupGradleBuild(buildFolder)

        service = DeclarativeTextDocumentService()
        service.initialize(
            mockk<LanguageClient>(relaxed = true),
            VersionedDocumentStore(),
            MutationRegistry(declarativeResources, emptyList()),
            DeclarativeFeatures(),
            declarativeResources
        )
    }

    @Test
    fun `code completion inside dependencies block`() {
        val script = settingsFile
        openFile(script)

        assertEquals(
            listOf(
                "            dependencies {",
                "                implementation(\"org.junit.jupiter:junit-jupiter:5.10.2\")",
                "                runtimeOnly(\"org.junit.platform:junit-platform-launcher\")",
                "            }",
            ),
            script.readLines().slice(26..29)
        )

        assertCompletion(
            script, 27, 15, listOf(
                """androidImplementation(dependency: Dependency), androidImplementation(${'$'}1)${'$'}0""",
                """androidImplementation(dependency: String), androidImplementation("${'$'}{1}")${'$'}0""",
                """compileOnly(dependency: Dependency), compileOnly(${'$'}1)${'$'}0""",
                """compileOnly(dependency: String), compileOnly("${'$'}{1}")${'$'}0""",
                """implementation(dependency: Dependency), implementation(${'$'}1)${'$'}0""",
                """implementation(dependency: String), implementation("${'$'}{1}")${'$'}0""",
                """platform(dependency: Dependency), platform(${'$'}1)${'$'}0""",
                """platform(dependency: String), platform("${'$'}{1}")${'$'}0""",
                """project(projectPath: String), project("${'$'}{1}")${'$'}0""",
                """runtimeOnly(dependency: Dependency), runtimeOnly(${'$'}1)${'$'}0""",
                """runtimeOnly(dependency: String), runtimeOnly("${'$'}{1}")${'$'}0""",
            )
        )
    }

    @Test
    fun `code completion inside block with properties`() {
        val script = buildFile
        openFile(script)

        assertEquals(
            listOf(
                "androidLibrary {",
                "    secrets {         }",
                "}",
            ),
            script.readLines().slice(3..5)
        )

        assertCompletion(
            script, 4, 16, listOf(
                """defaultPropertiesFile = layout.projectDirectory.file(path: String), defaultPropertiesFile = layout.projectDirectory.file("${'$'}{1}")${'$'}0""",
                """defaultPropertiesFile = layout.settingsDirectory.file(path: String), defaultPropertiesFile = layout.settingsDirectory.file("${'$'}{1}")${'$'}0""",
                """enabled = Boolean, enabled = ${'$'}{1|true,false|}"""
            )
        )
    }

    private fun assertCompletion(script: Path, line: Int, column: Int, expectedCompletions: List<String>) {
        val actualCompletionItems = service.completion(completionParams(script, line, column)).get().left
        assertEquals(
            expectedCompletions.sorted(),
            actualCompletionItems.map { "${it.label}, ${it.insertText}" }.sorted()
        )
    }

    private fun openFile(script: Path) {
        service.didOpen(DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = script.toUri().toString()
                text = script.readText()
            }
        })
    }

    private fun completionParams(script: Path, line: Int, column: Int): CompletionParams {
        val completionParams = CompletionParams().apply {
            textDocument = TextDocumentIdentifier(script.toUri().toString())
            position = Position(line, column)
        }
        return completionParams
    }

    @Suppress("LongMethod")
    private fun setupGradleBuild(dir: File): DeclarativeResourcesModel {
        val androidEcosystemPluginVersion = "0.1.40"
        settingsFile.writeText(
            """
            pluginManagement {
                repositories {
                    google() // Needed for the Android plugin, applied by the unified plugin
                    gradlePluginPortal()
                }
            }
            
            plugins {
                id("org.gradle.experimental.android-ecosystem").version("$androidEcosystemPluginVersion")
            }
            
            rootProject.name = "example-android-app"
            
            include("app")
            
            defaults {
                androidApplication {
                    jdkVersion = 17
                    compileSdk = 34
                    minSdk = 30
            
                    versionCode = 1
                    versionName = "0.1"
                    applicationId = "org.gradle.experimental.android.app"
            
                    testing {
                        dependencies {
                            implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                            runtimeOnly("org.junit.platform:junit-platform-launcher")
                        }
                    }
                }
            
                androidLibrary {
                    jdkVersion = 17
                    compileSdk = 34
                    minSdk = 30
            
                    testing {
                        dependencies {
                            implementation("org.junit.jupiter:junit-jupiter:5.10.2")
                            runtimeOnly("org.junit.platform:junit-platform-launcher")
                        }
                    }
                    
                    buildTypes {
                        release {
                            dependencies {
                                implementation("com.squareup.okhttp3:okhttp:4.2.2")
                            }
                
                            defaultProguardFiles = listOf(proguardFile("proguard-android-optimize.txt"))
                            proguardFiles = listOf(proguardFile("proguard-rules.pro"), proguardFile("some_other_file.txt"))
                
                            minify {
                                enabled = true
                            }
                        }
                    }
                }
            }
            """.trimIndent()
        )
        Path("$dir/gradle").createDirectories().resolve("gradle-daemon-jvm.properties").writeText(
            """
            toolchainVersion=17
            """.trimIndent()
        )
        Path("$dir/app/").createDirectories().resolve("build.gradle.dcl").writeText(
            """
            androidApplication {
                namespace = "org.example.app"
            }
            androidLibrary {
                secrets {         }
            }
            """.trimIndent()
        )
        Path("$dir/app/src/main/kotlin/org/example/app/").createDirectories().resolve("MainActivity.kt").writeText(
            """
            package org.example.app

            import org.apache.commons.text.WordUtils
            
            import android.widget.TextView
            import android.os.Bundle
            import android.app.Activity
            
            class MainActivity : Activity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                }
            }
            """.trimIndent()
        )

        return TapiConnectionHandler(dir).getDeclarativeResources()
    }

}
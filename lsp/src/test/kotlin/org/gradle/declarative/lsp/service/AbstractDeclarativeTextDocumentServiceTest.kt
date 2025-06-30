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

import io.mockk.mockk
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageClient
import org.gradle.declarative.lsp.DeclarativeFeatures
import org.gradle.declarative.lsp.DeclarativeModelStore
import org.gradle.declarative.lsp.DeclarativeTextDocumentService
import org.gradle.declarative.lsp.VersionedDocumentStore
import org.gradle.declarative.lsp.mutation.MutationRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals

abstract class AbstractDeclarativeTextDocumentServiceTest {

    @field:TempDir
    lateinit var buildFolder: File

    private lateinit var service: DeclarativeTextDocumentService

    protected lateinit var settingsFile: Path
    protected lateinit var buildFile: Path

    @BeforeEach
    fun setup() {
        settingsFile = Path("$buildFolder/settings.gradle.dcl")
        buildFile = Path("$buildFolder/app/build.gradle.dcl")

        val declarativeResources = setupGradleBuild(buildFolder)

        service = DeclarativeTextDocumentService()
        service.initialize(
            mockk<LanguageClient>(relaxed = true),
            VersionedDocumentStore(),
            MutationRegistry(emptyList()),
            DeclarativeFeatures(),
            declarativeResources
        )
    }

    protected abstract fun script(): Path

    protected fun assertCompletion(script: Path, line: Int, column: Int, expectedCompletions: String) {
        val actualCompletionItems = service.completion(completionParams(script, line, column)).get().left
        assertEquals(
            expectedCompletions,
            actualCompletionItems.map { "${it.label} --> ${it.insertText}" }.sorted().joinToString(separator = "\n")
        )
    }

    private fun completionParams(script: Path, line: Int, column: Int): CompletionParams {
        val completionParams = CompletionParams().apply {
            textDocument = TextDocumentIdentifier(script.toUri().toString())
            position = Position(line, column)
        }
        return completionParams
    }

    protected fun openFile(script: Path) {
        service.didOpen(DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = script.toUri().toString()
                text = script.readText()
            }
        })
    }

    @Suppress("LongMethod")
    private fun setupGradleBuild(dir: File): DeclarativeModelStore {
        val androidEcosystemPluginVersion = "0.1.42"
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
                    
                    secrets {         }
                    
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
                        debug {         }
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
            androidLibrary {
                namespace = "org.example.app"
                
                secrets {         }

                buildTypes {
                    release {         }
                }
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

        return DeclarativeModelStore(
            dir,
        ).apply {
            updateModel()
        }
    }

}
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

class DeclarativeTextDocumentServiceTest {

    @field:TempDir
    lateinit var buildFolder: File

    private lateinit var service: DeclarativeTextDocumentService

    private lateinit var settingsFile: Path

    @BeforeEach
    fun setup() {
        settingsFile = Path("$buildFolder/settings.gradle.dcl")

        val declarativeResources = setupGradleBuild(buildFolder)

        service = DeclarativeTextDocumentService()
        service.initialize(
            mockk<LanguageClient>(relaxed = true),
            VersionedDocumentStore(),
            MutationRegistry(declarativeResources, emptyList()),
            DeclarativeFeatures(),
            declarativeResources
        )
        service.didOpen(DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = settingsFile.toUri().toString()
                text = settingsFile.readText()
            }
        })
    }

    @Test
    fun `code completion`() {
        val completionParams = CompletionParams().apply {
            textDocument = TextDocumentIdentifier(settingsFile.toUri().toString())
            position = Position(32, 16)
        }

        assertEquals(
            listOf(
                "        compileOptions {",
                "            sourceCompatibility = VERSION_17",
                "            targetCompatibility = VERSION_17",
                "        }"
            ),
            settingsFile.readLines().slice(31..34)
        )

        assertEquals(
            listOf(
                "encoding = String",
                "isCoreLibraryDesugaringEnabled = Boolean",
                "sourceCompatibility = JavaVersion",
                "targetCompatibility = JavaVersion"
            ),
            service.completion(completionParams).get().left.map { it.label }
        )
    }


    @Suppress("LongMethod")
    private fun setupGradleBuild(dir: File): DeclarativeResourcesModel {
        Path("$dir/settings.gradle.dcl").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    maven {
                        url = uri("https://androidx.dev/studio/builds/12648882/artifacts/artifacts/repository")
                    }
                }
            }
            
            plugins {
                id("com.android.ecosystem").version("8.9.0-dev")
            }
            
            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                    maven {
                        url = uri("https://androidx.dev/studio/builds/12648882/artifacts/artifacts/repository")
                    }
                }
            }
            
            rootProject.name = "example-android-app"
            
            include("app")
            
            defaults {
                androidApp {
                    compileSdk = 34
                    compileOptions {
                        sourceCompatibility = VERSION_17
                        targetCompatibility = VERSION_17
                    }
                    defaultConfig {
                        minSdk = 30
                        versionCode = 1
                        versionName = "0.1"
                        applicationId = "org.gradle.experimental.android.app"
                    }
                    dependenciesDcl {
                        implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
                    }
                }
            
                androidLibrary {
                    compileSdk = 34
                    compileOptions {
                        sourceCompatibility = VERSION_17
                        targetCompatibility = VERSION_17
                    }
                    defaultConfig {
                        minSdk = 30
                    }
                    dependenciesDcl {
                        implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
                    }
                }
            }
            """.trimIndent()
        )
        Path("$dir/app/").createDirectories().resolve("build.gradle.dcl").writeText(
            """
            androidApp {
                namespace = "org.example.app"
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
                    setContentView(R.layout.activity_main)
            
                    val textView = findViewById(R.id.textView) as TextView
                    textView.text = "Hello, World!"
                }
            }
            """.trimIndent()
        )

        return TapiConnectionHandler(dir).getDeclarativeResources()
    }

}
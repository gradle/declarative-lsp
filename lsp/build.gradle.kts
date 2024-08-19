/*
 * Copyright 2024 the original author or authors.
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

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":model"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.lsp4j)
    implementation(libs.logback.classic)
    implementation(libs.gradle.tooling.api)
    implementation(libs.gradle.declarative.dsl.core)
    implementation(libs.gradle.declarative.dsl.evaluator)
    implementation(libs.gradle.declarative.dsl.tooling.models)

    testImplementation(libs.mockk)
}

detekt {
    // overwrite the config file's location
    config.convention(project.isolated.rootProject.projectDirectory.file("gradle/detekt.yml"))
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            java {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
            }
            useKotlinTest("2.0.0")
        }
    }
}